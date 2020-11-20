package vip.toby.rpc.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.parser.deserializer.JavaBeanDeserializer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.*;
import vip.toby.rpc.properties.RpcProperties;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;

/**
 * RpcClientProxy
 *
 * @author toby
 */
public class RpcClientProxy<T> implements InvocationHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(RpcClientProxy.class);

    private final Class<T> rpcClientInterface;
    private final String rpcName;
    private final RpcType rpcType;
    private final RabbitTemplate sender;
    private final RpcProperties rpcProperties;

    RpcClientProxy(Class<T> rpcClientInterface, String rpcName, RpcType rpcType, RabbitTemplate sender, RpcProperties rpcProperties) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.sender = sender;
        this.rpcProperties = rpcProperties;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        // 获取方法注解
        RpcClientMethod rpcClientMethod = method.getAnnotation(RpcClientMethod.class);
        if (rpcClientMethod == null) {
            try {
                if (Object.class.equals(method.getDeclaringClass())) {
                    return method.invoke(this, args);
                }
                throw new RuntimeException("未加@RpcClientMethod, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
        if (this.rpcType == RpcType.ASYNC && method.getGenericReturnType() != Void.TYPE) {
            throw new RuntimeException("ASYNC-RpcClient 返回类型只能为 void, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
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
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            if (isJavaBean(parameter.getType())) {
                data = (JSONObject) JSON.toJSON(args[i]);
                break;
            } else if (parameter.getType() == JSONObject.class) {
                data.putAll((JSONObject) args[i]);
            } else {
                // Spring-Boot框架默认已加上-parameters编译参数
                data.put(parameters[i].getName(), args[i]);
            }
        }
        // 调用参数
        JSONObject paramData = new JSONObject();
        paramData.put("command", methodName);
        paramData.put("data", data);
        String paramDataJsonString = paramData.toJSONString();
        try {
            if (this.rpcType == RpcType.ASYNC) {
                this.sender.convertAndSend(paramDataJsonString);
                LOGGER.debug(this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + ", Param: " + paramDataJsonString);
                return null;
            }
            // 发起请求并返回结果
            long start = System.currentTimeMillis();
            Object resultObj = this.sender.convertSendAndReceive(paramDataJsonString);
            if (resultObj == null) {
                // 无返回任何结果，说明服务器负载过高，没有及时处理请求，导致超时
                LOGGER.error("Service Unavailable! Duration: " + (System.currentTimeMillis() - start) + "ms, " + this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + ", Param: " + paramDataJsonString);
                return new RpcResult(ServerStatus.UNAVAILABLE);
            }
            // 获取调用结果的状态
            JSONObject resultJson = JSONObject.parseObject(resultObj.toString());
            int status = resultJson.getIntValue("status");
            Object resultData = resultJson.get("data");
            ServerStatus serverStatus = ServerStatus.getServerStatus(status);
            if (serverStatus != ServerStatus.SUCCESS || resultData == null) {
                LOGGER.error(serverStatus.getMessage() + "! Duration: " + (System.currentTimeMillis() - start) + "ms, " + this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + ", Param: " + paramDataJsonString);
                return new RpcResult(ServerStatus.getServerStatus(status));
            }
            // 获取操作层的状态
            JSONObject serverResultJson = JSON.parseObject(resultData.toString());
            RpcResult rpcResult = new RpcResult(ServerResult.build(OperateStatus.getOperateStatus(serverResultJson.getIntValue("status"))).message(serverResultJson.getString("message")).result(serverResultJson.get("result")).errorCode(serverResultJson.getIntValue("errorCode")));
            long offset = System.currentTimeMillis() - start;
            if (offset > this.rpcProperties.getClientSlowCallTime()) {
                LOGGER.warn("Call Slowing! Duration: " + offset + "ms, " + this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + ", Param: " + paramDataJsonString + ", RpcResult: " + rpcResult.toString());
            } else {
                LOGGER.debug("Duration: " + offset + "ms, " + this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + ", Param: " + paramDataJsonString + ", RpcResult: " + rpcResult.toString());
            }
            return rpcResult;
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return this.rpcType.getName() + "-RpcClient-" + this.rpcName;
    }

    private static boolean isJavaBean(Type type) {
        if (null == type) {
            throw new NullPointerException();
        }
        // 根据 getDeserializer 返回值类型判断是否为 java bean 类型
        return ParserConfig.global.getDeserializer(type) instanceof JavaBeanDeserializer;
    }

}
