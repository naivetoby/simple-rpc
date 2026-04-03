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
import org.jspecify.annotations.NonNull;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.validation.annotation.Validated;
import vip.toby.rpc.annotation.RpcDTO;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;
import vip.toby.rpc.entity.RCode;
import vip.toby.rpc.entity.RpcStatus;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.properties.RpcProperties;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RpcServerHandler
 *
 * @author toby
 */
@Slf4j
public class RpcServerHandler implements ChannelAwareMessageListener, InitializingBean, DisposableBean {

    private final static Map<String, Method> METHOD_MAP = new ConcurrentHashMap<>();
    private final static Map<String, Class<?>> METHOD_PARAMETER_TYPE_MAP = new ConcurrentHashMap<>();
    private final static Map<String, Boolean> METHOD_ALLOW_DUPLICATE_MAP = new ConcurrentHashMap<>();
    private final static Map<String, String> METHOD_PARTITION_KEY_MAP = new ConcurrentHashMap<>();

    private final Object rpcServerBean;
    private final String rpcName;
    private final RpcType rpcType;
    private final Validator validator;
    private final RpcProperties rpcProperties;
    private final int xMessageTTL;
    private final int threadNum;
    private final int partitionNum;
    private final int queueCapacity;
    private final RpcServerHandlerInterceptor rpcServerHandlerInterceptor;
    private ExecutorService asyncExecutor;
    private List<ExecutorService> partitionExecutors = Collections.emptyList();

    RpcServerHandler(
            Object rpcServerBean,
            String rpcName,
            RpcType rpcType,
            Validator validator,
            RpcProperties rpcProperties,
            int xMessageTTL,
            int threadNum,
            int partitionNum,
            int queueCapacity,
            RpcServerHandlerInterceptor rpcServerHandlerInterceptor
    ) {
        this.rpcServerBean = rpcServerBean;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.validator = validator;
        this.rpcProperties = rpcProperties;
        this.xMessageTTL = xMessageTTL;
        this.threadNum = Math.max(threadNum, 1);
        this.partitionNum = partitionNum;
        this.queueCapacity = Math.max(queueCapacity, 1);
        this.rpcServerHandlerInterceptor = rpcServerHandlerInterceptor;
    }

    @Override
    public void afterPropertiesSet() {
        // 初始化所有接口
        final Class<?> rpcServerClass = AopProxyUtils.ultimateTargetClass(this.rpcServerBean);
        for (Method method : rpcServerClass.getMethods()) {
            final RpcServerMethod rpcServerMethod = AnnotationUtils.findAnnotation(method, RpcServerMethod.class);
            if (rpcServerMethod != null) {
                String methodName = rpcServerMethod.value();
                if (StringUtils.isBlank(methodName)) {
                    methodName = method.getName();
                }
                final String key = this.rpcName + "_" + methodName;
                if (METHOD_MAP.containsKey(key)) {
                    throw new RuntimeException("Class: " + rpcServerClass.getName() + ", Method: " + methodName + " 重复");
                }
                // 确保方法可访问
                ReflectionUtils.makeAccessible(method);
                final Class<?> parameterType = getParameterType(method, rpcServerClass);
                if (parameterType.getAnnotation(RpcDTO.class) != null) {
                    // FIXME 预热 FastJSON2 解析 和 Validator
                    validator.validate(JSON.to(parameterType, new JSONObject()), Default.class);
                } else {
                    if (parameterType != JSONObject.class) {
                        throw new RuntimeException("参数类型只能为 JSONObject 或者添加 @RpcDTO 注解, Class: " + rpcServerClass.getName() + ", Method: " + method.getName());
                    }
                }
                METHOD_MAP.put(key, method);
                METHOD_PARAMETER_TYPE_MAP.put(key, parameterType);
                METHOD_ALLOW_DUPLICATE_MAP.put(key, rpcServerMethod.allowDuplicate());
                METHOD_PARTITION_KEY_MAP.put(key, rpcServerMethod.partitionKey());
                log.debug("RpcServer: {}, Method: {} 已启动", this.rpcName, methodName);
            }
        }
        initExecutors();
        log.info("{} 已启动", this.rpcName);
    }

    @Override
    public void onMessage(@NonNull Message message, Channel channel) throws IOException {
        RpcStatus rpcStatus = RpcStatus.FAIL;
        MessageProperties messageProperties = null;
        JSONObject paramData = null;
        boolean acked = false;
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
                    log.error("Method Invoke Exception: Command 参数为空, RpcServer: {}, Received: {}", this.rpcName, paramData);
                    // 此错误一般出现在调试阶段，所以没有处理返回，只打印日志
                    return;
                }
                // 获取 data 数据
                final JSONObject data = paramData.getJSONObject("data");
                if (data == null) {
                    log.error("Method Invoke Exception: Data 参数错误, RpcServer: {}, Method: {}, Received: {}", this.rpcName, command, paramData);
                    // 此错误一般出现在调试阶段，所以没有处理返回，只打印日志
                    return;
                }
                // 异步执行任务
                if (this.rpcType == RpcType.ASYNC || this.rpcType == RpcType.DELAY) {
                    if (partitionEnabled()) {
                        dispatchAsync(command, data, messageProperties.getCorrelationId(), paramData);
                        channel.basicAck(messageProperties.getDeliveryTag(), false);
                        acked = true;
                        return;
                    }
                    final long start = System.currentTimeMillis();
                    executeMethod(command, data, messageProperties.getCorrelationId(), false);
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
                final Object serverResult = executeMethod(command, data, messageProperties.getCorrelationId(), true);
                if (serverResult != null) {
                    final long offset = System.currentTimeMillis() - start;
                    log(paramData, command, offset);
                    // 修改状态
                    rpcStatus = RpcStatus.OK;
                    resultJson.put("data", ((R) serverResult).toJSON());
                } else {
                    rpcStatus = RpcStatus.NOT_FOUND;
                }
            } catch (Exception e) {
                log.error("Method Invoke Exception! Received: {}", paramData);
                log.error(e.getMessage(), e);
            }
            // 异步或者延迟任务
            if (this.rpcType == RpcType.ASYNC || this.rpcType == RpcType.DELAY) {
                return;
            }
            // 状态设置
            resultJson.put("code", rpcStatus.getCode());
            // 构建配置
            final BasicProperties replyProps = new BasicProperties.Builder().correlationId(messageProperties.getCorrelationId())
                    .contentEncoding(StandardCharsets.UTF_8.name())
                    .contentType(messageProperties.getContentType())
                    .build();
            // 反馈消息
            channel.basicPublish(Objects.requireNonNull(messageProperties.getReplyToAddress())
                    .getExchangeName(), messageProperties.getReplyToAddress()
                    .getRoutingKey(), replyProps, JSONB.toBytes(resultJson));
        } catch (Exception e) {
            log.error("RpcServer: {} Exception! Received: {}", this.rpcName, paramData);
            log.error(e.getMessage(), e);
        } finally {
            // FIXME 同步和异步消息, 强制 Ack
            if (!acked && (this.rpcType == RpcType.SYNC || this.rpcType == RpcType.ASYNC)) {
                if (messageProperties != null) {
                    channel.basicAck(messageProperties.getDeliveryTag(), false);
                }
            }
        }
    }

    private Object executeMethod(String command, Object data, String correlationId, boolean isSync) {
        // 获取当前服务的反射方法调用
        final String key = this.rpcName + "_" + command;
        // 通过缓存来优化性能
        final Method method = METHOD_MAP.get(key);
        if (method == null) {
            log.error("Not Found! RpcServer: {}, Method: {}", this.rpcName, command);
            return null;
        }
        // 重复调用检测
        if (this.rpcServerHandlerInterceptor != null && this.rpcServerHandlerInterceptor.rpcDuplicateHandle(key, correlationId)) {
            log.warn("Call Duplicate! RpcServer: {}, Method: {}", this.rpcName, command);
            return isSync ? R.build(RCode.DUPLICATE) : null;
        }
        if (!METHOD_ALLOW_DUPLICATE_MAP.get(key) && this.rpcServerHandlerInterceptor != null && this.rpcServerHandlerInterceptor.duplicateHandle(key, data)) {
            log.warn("Call Duplicate! RpcServer: {}, Method: {}", this.rpcName, command);
            return isSync ? R.build(RCode.DUPLICATE) : null;
        }
        // JavaBean 参数
        final Class<?> parameterType = METHOD_PARAMETER_TYPE_MAP.get(key);
        if (parameterType != JSONObject.class) {
            data = JSON.to(parameterType, data);
            // 参数校验
            final Annotation[] annotations = method.getParameters()[0].getAnnotations();
            for (Annotation ann : annotations) {
                // 先尝试获取 @Validated 注解
                final Validated validatedAnn = AnnotationUtils.getAnnotation(ann, Validated.class);
                // 如果直接标注了 @Validated，那么直接开启校验
                // 如果没有，那么判断参数前是否有 Valid 开头的注解
                if (validatedAnn != null || ann.annotationType().getSimpleName().startsWith("Valid")) {
                    final Class<?>[] validationHints = validated(ann, validatedAnn);
                    // 执行校验
                    final Set<ConstraintViolation<Object>> constraintViolations = validator.validate(data, validationHints);
                    if (!constraintViolations.isEmpty()) {
                        // 校验不合格处理
                        final List<String> tipList = new ArrayList<>();
                        constraintViolations.forEach(cv -> tipList.add(cv.getMessage()));
                        final String details = StringUtils.join(tipList, ", ");
                        log.error("Param Invalid! Detail: {}, RpcServer: {}, Method: {}", details, this.rpcName, command);
                        return isSync ? R.failMessage(details) : null;
                    }
                    break;
                }
            }
        }
        // 使用 ReflectionUtils 调用方法
        return ReflectionUtils.invokeMethod(method, this.rpcServerBean, data);
    }

    private Class<?>[] validated(Annotation ann, Validated validatedAnn) {
        Object hints = (validatedAnn != null ? validatedAnn.value() : AnnotationUtils.getValue(ann));
        if (hints == null) {
            hints = Default.class;
        }
        return (hints instanceof Class<?>[] ? (Class<?>[]) hints : new Class<?>[]{(Class<?>) hints});
    }

    private static Class<?> getParameterType(Method method, Class<?> rpcServerClass) {
        if (method.getReturnType() != R.class) {
            throw new RuntimeException("返回类型只能为 ServerResult, Class: " + rpcServerClass.getName() + ", Method: " + method.getName());
        }
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length != 1) {
            throw new RuntimeException("只能包含唯一参数, Class: " + rpcServerClass.getName() + ", Method: " + method.getName());
        }
        return parameterTypes[0];
    }

    private void log(JSONObject paramData, String command, double offset) {
        if (this.xMessageTTL > 0 && offset > Math.floor(this.rpcProperties.getServerSlowCallTimePercent() * this.xMessageTTL)) {
            log.info("Call Slowing! Duration: {}ms, RpcServer: {}, Method: {}, Received: {}", offset, this.rpcName, command, paramData);
        } else {
            log.info("Duration: {}ms, RpcServer: {}, Method: {}, Received: {}", offset, this.rpcName, command, paramData);
        }
    }

    @Override
    public void destroy() {
        if (this.asyncExecutor != null) {
            this.asyncExecutor.shutdown();
        }
        for (ExecutorService partitionExecutor : this.partitionExecutors) {
            partitionExecutor.shutdown();
        }
    }

    private void initExecutors() {
        if (!partitionEnabled()) {
            return;
        }
        boolean hasPartitionMethod = false;
        for (Map.Entry<String, String> entry : METHOD_PARTITION_KEY_MAP.entrySet()) {
            if (!entry.getKey().startsWith(this.rpcName + "_")) {
                continue;
            }
            if (StringUtils.isNotBlank(entry.getValue())) {
                hasPartitionMethod = true;
                break;
            }
        }
        if (!hasPartitionMethod) {
            return;
        }
        this.asyncExecutor = new ThreadPoolExecutor(
                this.threadNum,
                this.threadNum,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(this.queueCapacity),
                namedThreadFactory(this.rpcName + "-async"),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        final List<ExecutorService> executors = new ArrayList<>(this.partitionNum);
        for (int i = 0; i < this.partitionNum; i++) {
            executors.add(new ThreadPoolExecutor(
                    1,
                    1,
                    0L,
                    TimeUnit.MILLISECONDS,
                    new ArrayBlockingQueue<>(this.queueCapacity),
                    namedThreadFactory(this.rpcName + "-partition-" + i),
                    new ThreadPoolExecutor.CallerRunsPolicy()
            ));
        }
        this.partitionExecutors = executors;
    }

    private boolean partitionEnabled() {
        return (this.rpcType == RpcType.ASYNC || this.rpcType == RpcType.DELAY) && this.partitionNum > 1;
    }

    private void dispatchAsync(String command, JSONObject data, String correlationId, JSONObject paramData) {
        final ExecutorService executor = selectExecutor(command, data);
        final Runnable task = () -> {
            final long start = System.currentTimeMillis();
            try {
                executeMethod(command, data, correlationId, false);
                final double offset = System.currentTimeMillis() - start;
                log(paramData, command, offset);
            } catch (Exception e) {
                log.error("Method Invoke Exception! Received: {}", paramData);
                log.error(e.getMessage(), e);
            }
        };
        try {
            executor.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    private ExecutorService selectExecutor(String command, JSONObject data) {
        final String key = this.rpcName + "_" + command;
        final String partitionKey = METHOD_PARTITION_KEY_MAP.get(key);
        if (StringUtils.isBlank(partitionKey)) {
            if (this.asyncExecutor == null) {
                throw new RuntimeException("未初始化异步线程池, RpcServer: " + this.rpcName + ", Method: " + command);
            }
            return this.asyncExecutor;
        }
        if (!data.containsKey(partitionKey)) {
            throw new RuntimeException("未找到分区字段, PartitionKey: " + partitionKey + ", RpcServer: " + this.rpcName + ", Method: " + command);
        }
        final Object partitionValue = data.get(partitionKey);
        if (partitionValue == null) {
            throw new RuntimeException("分区字段不能为空, PartitionKey: " + partitionKey + ", RpcServer: " + this.rpcName + ", Method: " + command);
        }
        if (this.partitionExecutors.isEmpty()) {
            throw new RuntimeException("未开启分区配置, RpcServer: " + this.rpcName + ", Method: " + command);
        }
        final int index = Math.floorMod(partitionValue.toString().hashCode(), this.partitionExecutors.size());
        return this.partitionExecutors.get(index);
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        final AtomicInteger index = new AtomicInteger(1);
        return runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setName(prefix + "-" + index.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        };
    }

}
