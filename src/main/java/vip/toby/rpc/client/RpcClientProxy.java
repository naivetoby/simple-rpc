package vip.toby.rpc.client;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
public class RpcClientProxy implements InvocationHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(RpcClientProxy.class);

    private final Class<?> rpcClientInterface;
    private final String rpcName;
    private final RpcType rpcType;
    private final RabbitTemplate sender;

    RpcClientProxy(Class<?> rpcClientInterface, String rpcName, RpcType rpcType, RabbitTemplate sender) {
        this.rpcClientInterface = rpcClientInterface;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
        this.sender = sender;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 获取方法注解
        RpcClientMethod rpcClientMethod = method.getAnnotation(RpcClientMethod.class);
        if (rpcClientMethod == null) {
            return method.invoke(this, args);
        }
        if (method.getGenericReturnType() != RpcResult.class) {
            throw new RuntimeException("返回类型只能为 RpcResult, Class: " + this.rpcClientInterface.getName() + ", Method: " + method.getName());
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
                LOGGER.warn("注解@Param为空或未加注解@Param的参数名, Param: " + paramName + ", Class:" + this.rpcClientInterface.getName() + ", Method: " + method.getName());
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
                return null;
            }
            // 发起请求并返回结果
            Object resultObj = sender.convertSendAndReceive(paramDataJsonString);
            if (resultObj == null) {
                // 无返回任何结果，说明服务器负载过高，没有及时处理请求，导致超时
                LOGGER.error("Simple-Rpc Call Timeout, ParamData: " + paramDataJsonString);
                return new RpcResult(ServerStatus.UNAVAILABLE);
            }
            // 获取调用结果的状态
            JSONObject resultJson = JSONObject.parseObject(resultObj.toString());
            int status = resultJson.getIntValue("status");
            Object resultData = resultJson.get("data");
            ServerStatus serverStatus = ServerStatus.getServerStatus(status);
            if (serverStatus != ServerStatus.SUCCESS || resultData == null) {
                LOGGER.error("Simple-Rpc Call Error, Cause: " + serverStatus.getMessage() + ", ParamData: " + paramDataJsonString);
                return new RpcResult(ServerStatus.getServerStatus(status));
            }
            LOGGER.debug("Simple-Rpc Call Success, Result: " + JSON.toJSONString(resultData));
            // 获取操作层的状态
            JSONObject serverResultJson = JSON.parseObject(resultData.toString());
            return new RpcResult(new ServerResult(OperateStatus.getOperateStatus(serverResultJson.getIntValue("status")), serverResultJson.getString("message"), (JSON) serverResultJson.get("result"), serverResultJson.getIntValue("errorCode")));
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
        return new RpcResult(ServerStatus.FAILURE);
    }

    @Override
    public String toString() {
        return this.rpcName + "_Client";
    }

}
