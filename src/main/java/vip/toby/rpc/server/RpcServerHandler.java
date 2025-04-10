package vip.toby.rpc.server;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.JSONObject;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.groups.Default;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.cglib.reflect.FastClass;
import org.springframework.cglib.reflect.FastMethod;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.validation.annotation.Validated;
import vip.toby.rpc.annotation.RpcDTO;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;
import vip.toby.rpc.entity.RpcStatus;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.properties.RpcProperties;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
@Slf4j
public class RpcServerHandler implements ChannelAwareMessageListener, InitializingBean {

    private final static Map<String, FastMethod> FAST_METHOD_MAP = new ConcurrentHashMap<>();
    private final static Map<String, Class<?>> FAST_METHOD_PARAMETER_TYPE_MAP = new ConcurrentHashMap<>();
    private final static Map<String, Boolean> METHOD_ALLOW_DUPLICATE_MAP = new ConcurrentHashMap<>();

    private final Object rpcServerBean;
    private final String rpcName;
    private final RpcType rpcType;
    private final Validator validator;
    private final RpcProperties rpcProperties;
    private final int xMessageTTL;
    private final RpcServerHandlerInterceptor rpcServerHandlerInterceptor;

    RpcServerHandler(
            Object rpcServerBean,
            String rpcName,
            RpcType rpcType,
            Validator validator,
            RpcProperties rpcProperties,
            int xMessageTTL,
            RpcServerHandlerInterceptor rpcServerHandlerInterceptor
    ) {
        this.rpcServerBean = rpcServerBean;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.validator = validator;
        this.rpcProperties = rpcProperties;
        this.xMessageTTL = xMessageTTL;
        this.rpcServerHandlerInterceptor = rpcServerHandlerInterceptor;
    }

    @Override
    public void afterPropertiesSet() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        // 初始化所有接口
        final Class<?> rpcServerClass = this.rpcServerBean.getClass();
        final FastClass fastClass = FastClass.create(rpcServerClass);
        for (Method targetMethod : rpcServerClass.getMethods()) {
            final RpcServerMethod rpcServerMethod = AnnotationUtils.findAnnotation(targetMethod, RpcServerMethod.class);
            if (rpcServerMethod != null) {
                String methodName = rpcServerMethod.value();
                if (StringUtils.isBlank(methodName)) {
                    methodName = targetMethod.getName();
                }
                final String key = this.rpcType.getName() + "_" + this.rpcName + "_" + methodName;
                if (FAST_METHOD_MAP.containsKey(key)) {
                    throw new RuntimeException("Class: " + rpcServerClass.getName() + ", Method: " + methodName + " 重复");
                }
                final FastMethod fastMethod = fastClass.getMethod(targetMethod);
                final Class<?> parameterType = getParameterType(targetMethod, fastMethod, rpcServerClass);
                if (parameterType.getAnnotation(RpcDTO.class) != null) {
                    // FIXME 预热 FastJSON2 解析 和 Validator
                    validator.validate(JSON.to(parameterType, parameterType.getDeclaredConstructor()
                            .newInstance()), Default.class);
                } else {
                    if (parameterType != JSONObject.class) {
                        throw new RuntimeException("参数类型只能为 JSONObject 或者添加 @RpcDTO 注解, Class: " + rpcServerClass.getName() + ", Method: " + fastMethod.getName());
                    }
                }
                FAST_METHOD_MAP.put(key, fastMethod);
                FAST_METHOD_PARAMETER_TYPE_MAP.put(key, parameterType);
                METHOD_ALLOW_DUPLICATE_MAP.put(key, rpcServerMethod.allowDuplicate());
                log.debug("{}-RpcServer-{}, Method: {} 已启动", this.rpcType.getName(), this.rpcName, methodName);
            }
        }
        log.info("{}-RpcServerHandler-{} 已启动", this.rpcType.getName(), this.rpcName);
    }

    private static Class<?> getParameterType(Method targetMethod, FastMethod fastMethod, Class<?> rpcServerClass) {
        if (fastMethod == null) {
            throw new RuntimeException("Class: " + rpcServerClass.getName() + ", Method: " + targetMethod.getName() + " Invoke Exception");
        }
        if (fastMethod.getReturnType() != R.class) {
            throw new RuntimeException("返回类型只能为 ServerResult, Class: " + rpcServerClass.getName() + ", Method: " + fastMethod.getName());
        }
        final Class<?>[] parameterTypes = fastMethod.getParameterTypes();
        if (parameterTypes == null || parameterTypes.length != 1) {
            throw new RuntimeException("只能包含唯一参数, Class: " + rpcServerClass.getName() + ", Method: " + fastMethod.getName());
        }
        return parameterTypes[0];
    }

    @Override
    public void onMessage(Message message, Channel channel) throws IOException {
        RpcStatus rpcStatus = RpcStatus.FAIL;
        MessageProperties messageProperties = null;
        JSONObject paramData = null;
        try {
            messageProperties = message.getMessageProperties();
            // 组装参数 JSON
            paramData = JSONB.parseObject(message.getBody());
            // 构建返回 JSON 值
            final JSONObject resultJson = new JSONObject();
            try {
                // 获得当前 command
                final String command = paramData.getString("command");
                if (StringUtils.isBlank(command)) {
                    log.error("Method Invoke Exception: Command 参数为空, {}-RpcServer-{}, Received: {}", this.rpcType.getName(), this.rpcName, paramData);
                    // 此错误一般出现在调试阶段，所以没有处理返回，只打印日志
                    return;
                }
                // 获取 data 数据
                final JSONObject data = paramData.getJSONObject("data");
                if (data == null) {
                    log.error("Method Invoke Exception: Data 参数错误, {}-RpcServer-{}, Method: {}, Received: {}", this.rpcType.getName(), this.rpcName, command, paramData);
                    // 此错误一般出现在调试阶段，所以没有处理返回，只打印日志
                    return;
                }
                // 异步执行任务
                if (this.rpcType == RpcType.ASYNC || this.rpcType == RpcType.DELAY) {
                    final long start = System.currentTimeMillis();
                    asyncExecute(command, data, messageProperties.getCorrelationId());
                    final double offset = System.currentTimeMillis() - start;
                    log(paramData, command, offset);
                    // FIXME 延迟消息, 处理成功才 Ack
                    if (this.rpcType == RpcType.DELAY) {
                        channel.basicAck(messageProperties.getDeliveryTag(), false);
                    }
                    return;
                }
                // 同步执行任务并返回结果
                final long start = System.currentTimeMillis();
                final Object serverResult = syncExecute(command, data, messageProperties.getCorrelationId());
                if (serverResult != null) {
                    final long offset = System.currentTimeMillis() - start;
                    log(paramData, command, offset);
                    // 修改状态
                    rpcStatus = RpcStatus.OK;
                    resultJson.put("data", ((R) serverResult).toJSON());
                } else {
                    rpcStatus = RpcStatus.NOT_FOUND;
                }
            } catch (InvocationTargetException e) {
                // 获取目标异常
                final Throwable t = e.getTargetException();
                log.error("Method Invoke Target Exception! Received: {}", paramData);
                log.error(t.getMessage(), t);
            } catch (Exception e) {
                log.error("Method Invoke Exception! Received: {}", paramData);
                log.error(e.getMessage(), e);
            }
            // 异步执行任务
            if (this.rpcType == RpcType.ASYNC || this.rpcType == RpcType.DELAY) {
                return;
            }
            // 状态设置
            resultJson.put("status", rpcStatus.getStatus());
            // 构建配置
            final BasicProperties replyProps = new BasicProperties.Builder().correlationId(messageProperties.getCorrelationId())
                    .contentEncoding(StandardCharsets.UTF_8.name())
                    .contentType(messageProperties.getContentType())
                    .build();
            // 反馈消息
            channel.basicPublish(messageProperties.getReplyToAddress()
                    .getExchangeName(), messageProperties.getReplyToAddress()
                    .getRoutingKey(), replyProps, JSONB.toBytes(resultJson));
        } catch (Exception e) {
            log.error("{}-RpcServer-{} Exception! Received: {}", this.rpcType.getName(), this.rpcName, paramData);
            log.error(e.getMessage(), e);
        } finally {
            // FIXME 同步和异步消息, 强制 Ack
            if (this.rpcType == RpcType.SYNC || this.rpcType == RpcType.ASYNC) {
                if (messageProperties != null) {
                    channel.basicAck(messageProperties.getDeliveryTag(), false);
                }
            }
        }
    }

    private void log(JSONObject paramData, String command, double offset) {
        if (this.xMessageTTL > 0 && offset > Math.floor(this.rpcProperties.getServerSlowCallTimePercent() * this.xMessageTTL)) {
            log.info("Call Slowing! Duration: {}ms, {}-RpcServer-{}, Method: {}, Received: {}", offset, this.rpcType.getName(), this.rpcName, command, paramData);
        } else {
            log.info("Duration: {}ms, {}-RpcServer-{}, Method: {}, Received: {}", offset, this.rpcType.getName(), this.rpcName, command, paramData);
        }
    }

    /**
     * 异步调用
     */
    private void asyncExecute(String command, Object data, String correlationId) throws InvocationTargetException {
        // 获取当前服务的反射方法调用
        final String key = this.rpcType.getName() + "_" + this.rpcName + "_" + command;
        // 通过缓存来优化性能
        final FastMethod fastMethod = FAST_METHOD_MAP.get(key);
        if (fastMethod == null) {
            log.error("Not Found! {}-RpcServer-{}, Method: {}", this.rpcType.getName(), this.rpcName, command);
            return;
        }
        // 重复调用检测
        if (this.rpcServerHandlerInterceptor != null && this.rpcServerHandlerInterceptor.rpcDuplicateHandle(key, correlationId)) {
            log.warn("Call Duplicate! {}-RpcServer-{}, Method: {}", this.rpcType.getName(), this.rpcName, command);
            return;
        }
        if (!METHOD_ALLOW_DUPLICATE_MAP.get(key) && this.rpcServerHandlerInterceptor != null && this.rpcServerHandlerInterceptor.duplicateHandle(key, data)) {
            log.warn("Call Duplicate! {}-RpcServer-{}, Method: {}", this.rpcType.getName(), this.rpcName, command);
            return;
        }
        final Class<?> parameterType = FAST_METHOD_PARAMETER_TYPE_MAP.get(key);
        // JavaBean 参数
        if (parameterType != JSONObject.class) {
            data = JSON.to(parameterType, data);
            // JavaBean 参数是否需要校验
            final Annotation[] annotations = fastMethod.getJavaMethod().getParameters()[0].getAnnotations();
            for (Annotation ann : annotations) {
                // 先尝试获取 @Validated 注解
                final Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
                // 如果直接标注了 @Validated，那么直接开启校验
                // 如果没有，那么判断参数前是否有 Valid 开头的注解
                if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
                    final Class<?>[] validationHints = validated(ann, validatedAnn);
                    // 执行校验
                    final Set<ConstraintViolation<Object>> constraintViolations = validator.validate(validationHints);
                    if (!constraintViolations.isEmpty()) {
                        // 校验不合格处理
                        final List<String> tipList = new ArrayList<>();
                        constraintViolations.forEach(constraintViolationImpl -> tipList.add(constraintViolationImpl.getMessage()));
                        log.error("Param Invalid! Detail: {}, {}-RpcServer-{}, Method: {}", StringUtils.join(tipList, ", "), this.rpcType.getName(), this.rpcName, command);
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
    private Object syncExecute(String command, Object data, String correlationId) throws InvocationTargetException {
        // 获取当前服务的反射方法调用
        final String key = this.rpcType.getName() + "_" + this.rpcName + "_" + command;
        // 通过缓存来优化性能
        final FastMethod fastMethod = FAST_METHOD_MAP.get(key);
        if (fastMethod == null) {
            log.error("Not Found! {}-RpcServer-{}, Method: {}", this.rpcType.getName(), this.rpcName, command);
            return null;
        }
        // 重复调用检测
        if (this.rpcServerHandlerInterceptor != null && this.rpcServerHandlerInterceptor.rpcDuplicateHandle(key, correlationId)) {
            log.warn("Call Duplicate! {}-RpcServer-{}, Method: {}", this.rpcType.getName(), this.rpcName, command);
            return R.failMessage("Call Duplicate").errorCode(-1);
        }
        if (!METHOD_ALLOW_DUPLICATE_MAP.get(key) && this.rpcServerHandlerInterceptor != null && this.rpcServerHandlerInterceptor.duplicateHandle(key, data)) {
            log.warn("Call Duplicate! {}-RpcServer-{}, Method: {}", this.rpcType.getName(), this.rpcName, command);
            return R.failMessage("Call Duplicate").errorCode(-1);
        }
        final Class<?> parameterType = FAST_METHOD_PARAMETER_TYPE_MAP.get(key);
        // JavaBean 参数
        if (parameterType != JSONObject.class) {
            data = JSON.to(parameterType, data);
            // JavaBean 参数是否需要校验
            final Annotation[] annotations = fastMethod.getJavaMethod().getParameters()[0].getAnnotations();
            for (Annotation ann : annotations) {
                // 先尝试获取@Validated注解
                final Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
                // 如果直接标注了@Validated，那么直接开启校验
                // 如果没有，那么判断参数前是否有Valid起头的注解
                if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
                    final Class<?>[] validationHints = validated(ann, validatedAnn);
                    //执行校验
                    final Set<ConstraintViolation<Object>> constraintViolations = validator.validate(data, validationHints);
                    if (!constraintViolations.isEmpty()) {
                        // 校验不合格处理
                        final List<String> tipList = new ArrayList<>();
                        constraintViolations.forEach(constraintViolationImpl -> tipList.add(constraintViolationImpl.getMessage()));
                        log.error("Param Invalid! Detail: {}, {}-RpcServer-{}, Method: {}", StringUtils.join(tipList, ", "), this.rpcType.getName(), this.rpcName, command);
                        return R.failMessage(StringUtils.join(tipList, ", "));
                    }
                    break;
                }
            }
        }
        // 通过发射来调用方法
        return fastMethod.invoke(this.rpcServerBean, new Object[]{data});
    }

    private Class<?>[] validated(Annotation ann, Validated validatedAnn) {
        Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
        if (hints == null) {
            hints = Default.class;
        }
        return (hints instanceof Class<?>[] ? (Class<?>[]) hints : new Class<?>[]{(Class<?>) hints});
    }

}
