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
import vip.toby.rpc.util.RpcUtil;

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
    private final int partitionNum;

    RpcClientProxy(
            Class<T> rpcClientInterface,
            String rpcName,
            RpcType rpcType,
            RabbitTemplate sender,
            RpcProperties rpcProperties,
            int replyTimeout,
            int partitionNum
    ) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.sender = sender;
        this.rpcProperties = rpcProperties;
        this.replyTimeout = replyTimeout;
        this.partitionNum = partitionNum;
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
            throw new RuntimeException("ASYNC RpcClient 返回类型只能为 void, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        if (this.rpcType == RpcType.DELAY && method.getGenericReturnType() != Void.TYPE) {
            throw new RuntimeException("DELAY RpcClient 返回类型只能为 void, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        if (this.rpcType == RpcType.SYNC && method.getGenericReturnType() != RpcResult.class) {
            throw new RuntimeException("SYNC RpcClient 返回类型只能为 RpcResult, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        String methodName = rpcClientMethod.value();
        if (StringUtils.isBlank(methodName)) {
            methodName = method.getName();
        }
        // 组装 data
        JSONObject data = new JSONObject();
        final Parameter[] parameters = method.getParameters();
        if (this.rpcType == RpcType.DELAY) {
            if (parameters.length != 1) {
                throw new RuntimeException("DELAY RpcClient 只能包含唯一参数, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
            }
            final Type type = parameters[0].getParameterizedType();
            if (!(type instanceof Class<?> clazz)) {
                throw new RuntimeException("DELAY RpcClient 参数必须继承 RpcDelayDTO, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
            }
            if (!(RpcDelayDTO.class.isAssignableFrom(clazz))) {
                throw new RuntimeException("DELAY RpcClient 参数必须继承 RpcDelayDTO, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
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
        final String partitionKey = rpcClientMethod.partitionKey();
        final Object partitionValue = getPartitionValue(method, partitionKey, data);
        final String routingKey = RpcUtil.getRoutingKey(this.rpcName, this.partitionNum, partitionValue);
        // MessageProperties
        final MessageProperties messageProperties = new MessageProperties();
        messageProperties.setContentType(MessageProperties.CONTENT_TYPE_BYTES);
        messageProperties.setCorrelationId(UUID.randomUUID().toString());
        if (this.rpcType == RpcType.DELAY) {
            messageProperties.setDelayLong(data.getLongValue("delay", 0));
        }
        // Message
        final Message message = new Message(JSONB.toBytes(paramData), messageProperties);
        // CorrelationData
        final CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
        try {
            if (this.rpcType == RpcType.ASYNC) {
                this.sender.send("simple.rpc.async", routingKey, message, correlationData);
                log.debug("RpcClient: {}, Method: {}, Param: {}", this.rpcName, methodName, paramData);
                return null;
            }
            if (this.rpcType == RpcType.DELAY) {
                this.sender.send("simple.rpc.delay", routingKey, message, correlationData);
                log.debug("RpcClient: {}, Method: {}, Param: {}", this.rpcName, methodName, paramData);
                return null;
            }
            // 发起请求并返回结果
            final long start = System.currentTimeMillis();
            final Message resultObj = this.sender.sendAndReceive("simple.rpc.sync", routingKey, message, correlationData);
            if (resultObj == null) {
                // 无返回任何结果，说明服务器负载过高，没有及时处理请求，导致超时
                log.error("Unavailable! Duration: {}ms, RpcClient: {}, Method: {}, Param: {}", System.currentTimeMillis() - start, this.rpcName, methodName, paramData);
                return RpcResult.build(RpcStatus.UNAVAILABLE);
            }
            // 获取调用结果的状态
            final JSONObject resultJson = JSONB.parseObject(resultObj.getBody());
            final int code = resultJson.getIntValue("code");
            final Object resultData = resultJson.get("data");
            final RpcStatus rpcStatus = RpcStatus.of(code);
            if (rpcStatus != RpcStatus.OK || resultData == null) {
                log.error("{}! Duration: {}ms, RpcClient: {}, Method: {}, Param: {}", rpcStatus.getMessage(), System.currentTimeMillis() - start, this.rpcName, methodName, paramData);
                return RpcResult.build(rpcStatus);
            }
            // 获取操作层的状态
            final RpcResult rpcResult = RpcResult.build(RpcStatus.OK).result(toR(resultData));
            final long offset = System.currentTimeMillis() - start;
            if (offset > Math.floor(this.rpcProperties.getClientSlowCallTimePercent() * this.replyTimeout)) {
                log.warn("Call Slowing! Duration: {}ms, RpcClient: {}, Method: {}, Param: {}, RpcResult: {}", offset, this.rpcName, methodName, paramData, rpcResult);
            } else {
                log.debug("Duration: {}ms, RpcClient: {}, Method: {}, Param: {}, RpcResult: {}", offset, this.rpcName, methodName, paramData, rpcResult);
            }
            return rpcResult;
        } catch (Exception e) {
            this.sender.destroy();
            log.error("RpcClient: {} Exception! Method: {}, Param: {}", this.rpcName, methodName, paramData);
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private R toR(Object resultData) {
        final JSONObject result = JSONObject.parseObject(resultData.toString());
        if (result != null) {
            final int codeValue = result.getIntValue("code", RCode.FAIL.getCode());
            String message = result.getString("msg");
            if (StringUtils.isBlank(message)) {
                message = RCode.FAIL.getMessage();
            }
            final ICode code = ICode.build(codeValue, message);
            return R.build(code).result(result.get("data")).detail(result.get("det"));
        }
        return R.fail();
    }

    @Override
    public String toString() {
        return "RpcClient-" + this.rpcName;
    }

    private Object getPartitionValue(Method method, String partitionKey, JSONObject data) {
        if (StringUtils.isBlank(partitionKey)) {
            return null;
        }
        if (this.partitionNum <= 1) {
            throw new RuntimeException("未开启分区配置, RpcClient: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        if (!data.containsKey(partitionKey)) {
            throw new RuntimeException("未找到分区字段, PartitionKey: " + partitionKey + ", RpcClient: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        final Object partitionValue = data.get(partitionKey);
        if (partitionValue == null) {
            throw new RuntimeException("分区字段不能为空, PartitionKey: " + partitionKey + ", RpcClient: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
        }
        return partitionValue;
    }

}
