package vip.toby.rpc.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import net.sf.cglib.core.Constants;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.validation.annotation.Validated;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.entity.ServerResult;
import vip.toby.rpc.entity.ServerStatus;
import vip.toby.rpc.properties.RpcProperties;

import javax.validation.ConstraintViolation;
import javax.validation.Validator;
import javax.validation.groups.Default;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RpcServerHandler
 *
 * @author toby
 */
public class RpcServerHandler implements ChannelAwareMessageListener, InitializingBean {

    private final static Logger LOGGER = LoggerFactory.getLogger(RpcServerHandler.class);

    private final static Map<String, FastMethod> FAST_METHOD_MAP = new ConcurrentHashMap<>();
    private final static Map<String, Boolean> METHOD_ALLOW_DUPLICATE_MAP = new ConcurrentHashMap<>();

    private final Object rpcServerBean;
    private final String rpcName;
    private final RpcType rpcType;
    private final Validator validator;
    private final RpcProperties rpcProperties;
    private final RpcServerHandlerInterceptor rpcServerHandlerInterceptor;

    RpcServerHandler(Object rpcServerBean, String rpcName, RpcType rpcType, Validator validator, RpcProperties rpcProperties, RpcServerHandlerInterceptor rpcServerHandlerInterceptor) {
        this.rpcServerBean = rpcServerBean;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.validator = validator;
        this.rpcProperties = rpcProperties;
        this.rpcServerHandlerInterceptor = rpcServerHandlerInterceptor;
    }

    @Override
    public void afterPropertiesSet() {
        // 初始化所有接口
        Class<?> rpcServerClass = this.rpcServerBean.getClass();
        FastClass fastClass = FastClass.create(rpcServerClass);
        for (Method targetMethod : rpcServerClass.getMethods()) {
            if (targetMethod != null) {
                RpcServerMethod rpcServerMethod = AnnotationUtils.findAnnotation(targetMethod, RpcServerMethod.class);
                if (rpcServerMethod != null) {
                    String methodName = rpcServerMethod.value();
                    if (StringUtils.isBlank(methodName)) {
                        methodName = targetMethod.getName();
                    }
                    String key = this.rpcType.getName() + "_" + this.rpcName + "_" + methodName;
                    if (FAST_METHOD_MAP.containsKey(key)) {
                        throw new RuntimeException("Class: " + rpcServerClass.getName() + ", Method: " + methodName + " 重复");
                    }
                    FastMethod fastMethod = fastClass.getMethod(targetMethod);
                    if (fastMethod == null) {
                        throw new RuntimeException("Class: " + rpcServerClass.getName() + ", Method: " + targetMethod.getName() + " Invoke Exception");
                    }
                    if (fastMethod.getReturnType() != ServerResult.class) {
                        throw new RuntimeException("返回类型只能为 ServerResult, Class: " + rpcServerClass.getName() + ", Method: " + fastMethod.getName());
                    }
                    Class<?>[] parameterTypes = fastMethod.getParameterTypes();
                    if (parameterTypes == null || parameterTypes.length != 1 || (parameterTypes[0] != JSONObject.class && !isJavaBean(parameterTypes[0]))) {
                        throw new RuntimeException("只能包含唯一参数且参数类型只能为 JSONObject 或者 JavaBean, Class: " + rpcServerClass.getName() + ", Method: " + fastMethod.getName());
                    }
                    FAST_METHOD_MAP.put(key, fastMethod);
                    METHOD_ALLOW_DUPLICATE_MAP.put(key, rpcServerMethod.allowDuplicate());
                    LOGGER.debug(this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + methodName + " 已启动");
                }
            }
        }
        try {
            fastClass.invoke("toString", Constants.EMPTY_CLASS_ARRAY, this.rpcServerBean, new Object[]{});
        } catch (InvocationTargetException e) {
            // nothing to do
        }
        LOGGER.info(this.rpcType.getName() + "-RpcServerHandler-" + this.rpcName + " 已启动");
    }

    private static boolean isJavaBean(Type type) {
        if (null == type) {
            throw new NullPointerException();
        }
        // 根据 getDeserializer 返回值类型判断是否为 java bean 类型
        return ParserConfig.global.getDeserializer(type) instanceof JavaBeanDeserializer;
    }

    @Override
    public void onMessage(Message message, Channel channel) throws IOException {
        ServerStatus serverStatus = ServerStatus.FAILURE;
        MessageProperties messageProperties = null;
        String messageStr = null;
        try {
            messageProperties = message.getMessageProperties();
            messageStr = new String(message.getBody(), StandardCharsets.UTF_8);
            // 构建返回JSON值
            JSONObject resultJson = new JSONObject();
            try {
                // 组装参数json
                JSONObject paramData = JSON.parseObject(messageStr);
                // 获得当前command
                String command = paramData.getString("command");
                if (StringUtils.isBlank(command)) {
                    LOGGER.error("Method Invoke Exception: Command 参数为空, " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Received: " + messageStr);
                    // 此错误一般出现在调试阶段，所以没有处理返回，只打印日志
                    return;
                }
                // 获取data数据
                JSONObject data = paramData.getJSONObject("data");
                if (data == null) {
                    LOGGER.error("Method Invoke Exception: Data 参数错误, " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command + ", Received: " + messageStr);
                    // 此错误一般出现在调试阶段，所以没有处理返回，只打印日志
                    return;
                }
                // 异步执行任务
                if (RpcType.ASYNC == this.rpcType) {
                    long start = System.currentTimeMillis();
                    asyncExecute(command, data);
                    double offset = System.currentTimeMillis() - start;
                    if (offset > this.rpcProperties.getServerSlowCallTime()) {
                        LOGGER.warn("Call Slowing! Duration: " + offset + "ms, " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command + ", Received: " + messageStr);
                    } else {
                        LOGGER.info("Duration: " + offset + "ms, " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command + ", Received: " + messageStr);
                    }
                    return;
                }
                // 同步执行任务并返回结果
                long start = System.currentTimeMillis();
                JSONObject resultData = syncExecute(command, data, messageProperties.getCorrelationId());
                if (resultData != null) {
                    long offset = System.currentTimeMillis() - start;
                    if (offset > this.rpcProperties.getServerSlowCallTime()) {
                        LOGGER.warn("Call Slowing! Duration: " + offset + "ms, " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command + ", Received: " + messageStr);
                    } else {
                        LOGGER.info("Duration: " + offset + "ms, " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command + ", Received: " + messageStr);
                    }
                    // 修改状态
                    serverStatus = ServerStatus.SUCCESS;
                    resultJson.put("data", resultData);
                } else {
                    serverStatus = ServerStatus.NOT_EXIST;
                }
            } catch (InvocationTargetException e) {
                // 获取目标异常
                Throwable t = e.getTargetException();
                LOGGER.error("Method Invoke Target Exception! Received: " + messageStr);
                LOGGER.error(t.getMessage(), t);
            } catch (Exception e) {
                LOGGER.error("Method Invoke Exception! Received: " + messageStr);
                LOGGER.error(e.getMessage(), e);
            }
            // 状态设置
            resultJson.put("status", serverStatus.getStatus());
            resultJson.put("message", serverStatus.getMessage());
            // 构建配置
            BasicProperties replyProps = new BasicProperties.Builder().correlationId(messageProperties.getCorrelationId()).contentEncoding(StandardCharsets.UTF_8.name()).contentType(messageProperties.getContentType()).build();
            // 反馈消息
            channel.basicPublish(messageProperties.getReplyToAddress().getExchangeName(), messageProperties.getReplyToAddress().getRoutingKey(), replyProps, resultJson.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOGGER.error(this.rpcType.getName() + "-RpcServer-" + this.rpcName + " Exception! Received: " + messageStr);
            LOGGER.error(e.getMessage(), e);
        } finally {
            // 确认处理任务
            if (messageProperties != null) {
                channel.basicAck(messageProperties.getDeliveryTag(), false);
            }
        }
    }

    /**
     * 异步调用
     */
    private void asyncExecute(String command, Object data) throws InvocationTargetException {
        // 获取当前服务的反射方法调用
        String key = this.rpcType.getName() + "_" + this.rpcName + "_" + command;
        // 通过缓存来优化性能
        FastMethod fastMethod = FAST_METHOD_MAP.get(key);
        if (fastMethod == null) {
            LOGGER.error("Not Found! " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command);
            return;
        }
        // 重复调用检测
        if (!METHOD_ALLOW_DUPLICATE_MAP.get(key) && this.rpcServerHandlerInterceptor.duplicateHandle(key, data)) {
            LOGGER.warn("Call Duplicate! " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command);
            return;
        }
        Class<?> parameterType = fastMethod.getParameterTypes()[0];
        // JavaBean 参数
        if (parameterType != JSONObject.class) {
            data = JSON.toJavaObject((JSON) data, parameterType);
            // JavaBean 参数是否需要校验
            Annotation[] annotations = fastMethod.getJavaMethod().getParameters()[0].getAnnotations();
            for (Annotation ann : annotations) {
                // 先尝试获取@Validated注解
                Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
                // 如果直接标注了@Validated，那么直接开启校验
                // 如果没有，那么判断参数前是否有Valid起头的注解
                if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
                    Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
                    if (hints == null) {
                        hints = Default.class;
                    }
                    Class<?>[] validationHints = (hints instanceof Class<?>[] ? (Class<?>[]) hints : new Class<?>[]{(Class<?>) hints});
                    //执行校验
                    Set<ConstraintViolation<Object>> constraintViolations = validator.validate(validationHints);
                    if (!constraintViolations.isEmpty()) {
                        // 校验不合格处理
                        List<String> tipList = new ArrayList<>();
                        constraintViolations.forEach(constraintViolationImpl -> tipList.add(constraintViolationImpl.getMessage()));
                        LOGGER.error("Param Invalid! Detail: " + StringUtils.join(tipList, ", ") + ", " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command);
                        return;
                    }
                    break;
                }
            }
        }
        // 通过发射来调用方法
        fastMethod.invoke(this.rpcServerBean, new Object[]{data});
    }

    /**
     * 同步调用
     */
    private JSONObject syncExecute(String command, Object data, String correlationId) throws InvocationTargetException {
        // 获取当前服务的反射方法调用
        String key = this.rpcType.getName() + "_" + this.rpcName + "_" + command;
        // 通过缓存来优化性能
        FastMethod fastMethod = FAST_METHOD_MAP.get(key);
        if (fastMethod == null) {
            LOGGER.error("Not Found! " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command);
            return null;
        }
        // 重复调用检测
        if (this.rpcServerHandlerInterceptor.rpcDuplicateHandle(key, correlationId)) {
            LOGGER.warn("Call Duplicate! " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command);
            ServerResult resultData = ServerResult.buildFailureMessage("Call Duplicate").errorCode(-1);
            return JSONObject.parseObject(resultData.toString());
        }
        if (!METHOD_ALLOW_DUPLICATE_MAP.get(key) && this.rpcServerHandlerInterceptor.duplicateHandle(key, data)) {
            LOGGER.warn("Call Duplicate! " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command);
            ServerResult resultData = ServerResult.buildFailureMessage("Call Duplicate").errorCode(-1);
            return JSONObject.parseObject(resultData.toString());
        }
        Class<?> parameterType = fastMethod.getParameterTypes()[0];
        // JavaBean 参数
        if (parameterType != JSONObject.class) {
            data = JSON.toJavaObject((JSON) data, parameterType);
            // JavaBean 参数是否需要校验
            Annotation[] annotations = fastMethod.getJavaMethod().getParameters()[0].getAnnotations();
            for (Annotation ann : annotations) {
                // 先尝试获取@Validated注解
                Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
                // 如果直接标注了@Validated，那么直接开启校验
                // 如果没有，那么判断参数前是否有Valid起头的注解
                if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
                    Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
                    if (hints == null) {
                        hints = Default.class;
                    }
                    Class<?>[] validationHints = (hints instanceof Class<?>[] ? (Class<?>[]) hints : new Class<?>[]{(Class<?>) hints});
                    //执行校验
                    Set<ConstraintViolation<Object>> constraintViolations = validator.validate(data, validationHints);
                    if (!constraintViolations.isEmpty()) {
                        // 校验不合格处理
                        List<String> tipList = new ArrayList<>();
                        constraintViolations.forEach(constraintViolationImpl -> tipList.add(constraintViolationImpl.getMessage()));
                        LOGGER.error("Param Invalid! Detail: " + StringUtils.join(tipList, ", ") + ", " + this.rpcType.getName() + "-RpcServer-" + this.rpcName + ", Method: " + command);
                        ServerResult resultData = ServerResult.buildFailureMessage(StringUtils.join(tipList, ", "));
                        return JSONObject.parseObject(resultData.toString());
                    }
                    break;
                }
            }
        }
        // 通过发射来调用方法
        return JSONObject.parseObject(fastMethod.invoke(this.rpcServerBean, new Object[]{data}).toString());
    }

}
