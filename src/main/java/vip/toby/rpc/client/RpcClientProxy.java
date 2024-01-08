package vip.toby.rpc.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.annotation.RpcDTO;
import vip.toby.rpc.entity.*;
import vip.toby.rpc.properties.RpcProperties;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.UUID;

/**
 * RpcClientProxy
 *
 * @author toby
 */
@Slf4j
public class RpcClientProxy<T> implements InvocationHandler {

    private final Class<T> rpcClientInterface;
    private final String rpcName;
    private final RpcType rpcType;
    private final RabbitTemplate sender;
    private final RpcProperties rpcProperties;
    private final int replyTimeout;

    RpcClientProxy(
            Class<T> rpcClientInterface,
            String rpcName,
            RpcType rpcType,
            RabbitTemplate sender,
            RpcProperties rpcProperties,
            int replyTimeout
    ) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.sender = sender;
        this.rpcProperties = rpcProperties;
        this.replyTimeout = replyTimeout;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        // 获取方法注解
        final RpcClientMethod rpcClientMethod = method.getAnnotation(RpcClientMethod.class);
        if (rpcClientMethod == null) {
            try {
                if (Object.class.equals(method.getDeclaringClass())) {
                    return method.invoke(this, args);
                }
                throw new RuntimeException("未加 @RpcClientMethod, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        if (this.rpcType == RpcType.ASYNC && method.getGenericReturnType() != Void.TYPE) {
            throw new RuntimeException("ASYNC-RpcClient 返回类型只能为 void, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        if (this.rpcType == RpcType.DELAY && method.getGenericReturnType() != Void.TYPE) {
            throw new RuntimeException("DELAY-RpcClient 返回类型只能为 void, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        if (this.rpcType == RpcType.SYNC && method.getGenericReturnType() != RpcResult.class) {
            throw new RuntimeException("SYNC-RpcClient 返回类型只能为 RpcResult, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        String methodName = rpcClientMethod.value();
        if (StringUtils.isBlank(methodName)) {
            methodName = method.getName();
        }
        // 组装data
        JSONObject data = new JSONObject();
        final Parameter[] parameters = method.getParameters();
        if (this.rpcType == RpcType.DELAY) {
            if (parameters.length != 1) {
                throw new RuntimeException("DELAY-RpcClient 只能包含唯一参数, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
            }
            final Type type = parameters[0].getParameterizedType();
            if (!(type instanceof Class<?> clazz)) {
                throw new RuntimeException("DELAY-RpcClient 参数必须继承 RpcDelayDTO, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
            }
            if (!(RpcDelayDTO.class.isAssignableFrom(clazz))) {
                throw new RuntimeException("DELAY-RpcClient 参数必须继承 RpcDelayDTO, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
            }
        }
        for (int i = 0; i < parameters.length; i++) {
            final Parameter parameter = parameters[i];
            if (parameter.getType() == JSONObject.class) {
                // JSONObject
                data.putAll((JSONObject) args[i]);
            } else if (parameter.getType().getAnnotation(RpcDTO.class) != null) {
                // 添加 @RpcDTO 的 JavaBean
                data = (JSONObject) JSON.toJSON(args[i]);
            } else {
                // Spring-Boot 框架默认已加上 -parameters 编译参数
                data.put(parameter.getName(), args[i]);
            }
        }
        // 调用参数
        final JSONObject paramData = new JSONObject();
        paramData.put("command", methodName);
        paramData.put("data", data);
        // MessageProperties
        final MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
        messageProperties.setCorrelationId(UUID.randomUUID().toString());
        if (this.rpcType == RpcType.DELAY) {
            messageProperties.setDelay(data.getIntValue("delay", 0));
        }
        // Message
        final Message message = new Message(JSONB.toBytes(paramData), messageProperties);
        // CorrelationData
        final CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        try {
            if (this.rpcType == RpcType.ASYNC) {
                this.sender.send("simple.rpc.async", this.sender.getRoutingKey(), message, correlationData);
                log.debug("{}-RpcClient-{}, Method: {}, Param: {}", this.rpcType.getName(), this.rpcName, methodName, paramData);
                return null;
            }
            if (this.rpcType == RpcType.DELAY) {
                this.sender.send("simple.rpc.delay", this.sender.getRoutingKey(), message, correlationData);
                log.debug("{}-RpcClient-{}, Method: {}, Param: {}", this.rpcType.getName(), this.rpcName, methodName, paramData);
                return null;
            }
            // 发起请求并返回结果
            final long start = System.currentTimeMillis();
            final Message resultObj = this.sender.sendAndReceive("simple.rpc.sync", this.sender.getRoutingKey(), message, correlationData);
            if (resultObj == null) {
                // 无返回任何结果，说明服务器负载过高，没有及时处理请求，导致超时
                log.error("Service Unavailable! Duration: {}ms, {}-RpcClient-{}, Method: {}, Param: {}", System.currentTimeMillis() - start, this.rpcType.getName(), this.rpcName, methodName, paramData);
                return RpcResult.build(RpcStatus.UNAVAILABLE);
            }
            // 获取调用结果的状态
            final JSONObject resultJson = JSONB.parseObject(resultObj.getBody());
            final int status = resultJson.getIntValue("status");
            final Object resultData = resultJson.get("data");
            final RpcStatus rpcStatus = RpcStatus.of(status);
            if (rpcStatus != RpcStatus.OK || resultData == null) {
                log.error("{}! Duration: {}ms, {}-RpcClient-{}, Method: {}, Param: {}", rpcStatus.getMessage(), System.currentTimeMillis() - start, this.rpcType.getName(), this.rpcName, methodName, paramData);
                return RpcResult.build(rpcStatus);
            }
            // 获取操作层的状态
            final JSONObject serverResultJson = JSON.parseObject(resultData.toString());
            final RpcResult rpcResult = RpcResult.okResult(R.build(RStatus.of(serverResultJson.getIntValue("status")))
                    .message(serverResultJson.getString("message"))
                    .result(serverResultJson.get("result"))
                    .errorCode(serverResultJson.getIntValue("errorCode")));
            final long offset = System.currentTimeMillis() - start;
            if (offset > Math.floor(this.rpcProperties.getClientSlowCallTimePercent() * this.replyTimeout)) {
                log.warn("Call Slowing! Duration: {}ms, {}-RpcClient-{}, Method: {}, Param: {}, RpcResult: {}", offset, this.rpcType.getName(), this.rpcName, methodName, paramData, rpcResult);
            } else {
                log.debug("Duration: {}ms, {}-RpcClient-{}, Method: {}, Param: {}, RpcResult: {}", offset, this.rpcType.getName(), this.rpcName, methodName, paramData, rpcResult);
            }
            return rpcResult;
        } catch (Exception e) {
            this.sender.destroy();
            log.error("{}-RpcServer-{} Exception! Method: {}, Param: {}", this.rpcType.getName(), this.rpcName, methodName, paramData);
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return this.rpcType.getName() + "-RpcClient-" + this.rpcName;
    }

}
