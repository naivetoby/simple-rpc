package vip.toby.rpc.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import vip.toby.rpc.annotation.Param;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.*;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

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
    private final SimpleMessageListenerContainer messageListenerContainer;

    RpcClientProxy(Class<T> rpcClientInterface, String rpcName, RpcType rpcType, RabbitTemplate sender, SimpleMessageListenerContainer messageListenerContainer) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.sender = sender;
        this.messageListenerContainer = messageListenerContainer;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 未初始化完成
        if (this.rpcType == RpcType.SYNC && !this.messageListenerContainer.isRunning()) {
            return new RpcResult(ServerStatus.FAILURE);
        }
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
            // 需要加上-parameters编译参数, 否则参数名不对
            String paramName = parameters[i].getName();
            Parameter parameter = parameters[i];
            Param param = parameter.getAnnotation(Param.class);
            if (param != null && StringUtils.isNotBlank(param.value())) {
                paramName = param.value();
            } else {
                LOGGER.warn(this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + ", 未加@Param或@Param的值为空");
            }
            data.put(paramName, args[i]);
        }
        // 调用参数
        JSONObject paramData = new JSONObject();
        paramData.put("command", methodName);
        paramData.put("data", data);
        String paramDataJsonString = paramData.toJSONString();
        try {
            if (this.rpcType == RpcType.ASYNC) {
                sender.convertAndSend(paramDataJsonString);
                LOGGER.debug(this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + " Call Success, Param: " + paramDataJsonString);
                return null;
            }
            // 发起请求并返回结果
            long start = System.currentTimeMillis();
            Object resultObj = sender.convertSendAndReceive(paramDataJsonString);
            if (resultObj == null) {
                // 无返回任何结果，说明服务器负载过高，没有及时处理请求，导致超时
                LOGGER.error("Duration: " + (System.currentTimeMillis() - start) + "ms, " + this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + " Service Unavailable, Param: " + paramDataJsonString);
                return new RpcResult(ServerStatus.UNAVAILABLE);
            }
            // 获取调用结果的状态
            JSONObject resultJson = JSONObject.parseObject(resultObj.toString());
            int status = resultJson.getIntValue("status");
            Object resultData = resultJson.get("data");
            ServerStatus serverStatus = ServerStatus.getServerStatus(status);
            if (serverStatus != ServerStatus.SUCCESS || resultData == null) {
                LOGGER.error("Duration: " + (System.currentTimeMillis() - start) + "ms, " + this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + " " + serverStatus.getMessage() + ", Param: " + paramDataJsonString);
                return new RpcResult(ServerStatus.getServerStatus(status));
            }
            // 获取操作层的状态
            JSONObject serverResultJson = JSON.parseObject(resultData.toString());
            RpcResult rpcResult = new RpcResult(ServerResult.build(OperateStatus.getOperateStatus(serverResultJson.getIntValue("status"))).message(serverResultJson.getString("message")).result(serverResultJson.get("result")).errorCode(serverResultJson.getIntValue("errorCode")));
            LOGGER.debug("Duration: " + (System.currentTimeMillis() - start) + "ms, " + this.rpcType.getName() + "-RpcClient-" + this.rpcName + ", Method: " + methodName + " Call Success, Param: " + paramDataJsonString + ", RpcResult: " + rpcResult.toString());
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

}
