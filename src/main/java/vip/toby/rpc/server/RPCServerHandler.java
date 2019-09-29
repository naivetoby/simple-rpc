package vip.toby.rpc.server;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import net.sf.cglib.reflect.FastClass;
import net.sf.cglib.reflect.FastMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import vip.toby.rpc.annotation.RPCMethod;
import vip.toby.rpc.entity.ServerStatus;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RPCServerHandler implements ChannelAwareMessageListener, InitializingBean, DisposableBean {

    private final static Logger logger = LoggerFactory.getLogger(RPCServerHandler.class);

    private final static Map<String, FastMethod> fastMethodMap = new ConcurrentHashMap<>();

    private Object rpcServer;
    private String name;
    private String type;

    RPCServerHandler(Object rpcServer, String name, String type) {
        this.rpcServer = rpcServer;
        this.name = name;
        this.type = type;
    }

    @Override
    public void afterPropertiesSet() {
        logger.info(type + " RPCServer: " + name + " 已启动");
    }

    @Override
    public void destroy() {
        logger.info(type + " RPCServer: " + name + " 已停止");
    }

    @Override
    public void onMessage(Message message, Channel channel) throws IOException {
        ServerStatus serverStatus = ServerStatus.FAILURE;
        MessageProperties messageProperties = null;
        String messageStr = null;
        try {
            messageProperties = message.getMessageProperties();
            messageStr = new String(message.getBody(), StandardCharsets.UTF_8);
            // 打印
            logger.info(type + " RPCServer: " + name + " 接收到消息: " + messageStr);
            // 构建返回JSON值
            JSONObject resultJson = new JSONObject();
            try {
                // 组装参数json
                JSONObject paramData = JSON.parseObject(messageStr);
                // 异步执行任务
                if ("async".equalsIgnoreCase(type)) {
                    asyncExecute(paramData);
                    return;
                }
                // 同步执行任务并返回结果
                long start = System.currentTimeMillis();
                JSONObject data = syncExecute(paramData);
                if (data != null) {
                    double offset = System.currentTimeMillis() - start;
                    if (offset > 3000) {
                        logger.warn("同步调用时间过长, 共耗时: " + offset + "ms, paramData: " + paramData);
                    }
                    // 修改状态
                    serverStatus = ServerStatus.SUCCESS;
                    resultJson.put("data", data);
                }
            } catch (InvocationTargetException e) {
                // 获取目标异常
                Throwable t = e.getTargetException();
                logger.error("Method Invoke Target Exception! Message: " + messageStr);
                logger.error(t.getMessage(), t);
            } catch (Exception e) {
                logger.error("Method Invoke Exception! Message: " + messageStr);
                logger.error(e.getMessage(), e);
            }
            // 状态设置
            resultJson.put("status", serverStatus.getStatus());
            resultJson.put("message", serverStatus.getMessage());
            // 构建配置
            BasicProperties replyProps = new BasicProperties.Builder().correlationId(messageProperties.getCorrelationId()).contentEncoding(StandardCharsets.UTF_8.name()).contentType(messageProperties.getContentType()).build();
            // 反馈消息
            channel.basicPublish(messageProperties.getReplyToAddress().getExchangeName(), messageProperties.getReplyToAddress().getRoutingKey(), replyProps, resultJson.toJSONString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error(type + " RPCServer: " + name + " Exception! Message: " + messageStr);
            logger.error(e.getMessage(), e);
        } finally {
            // 确认处理任务
            if (messageProperties != null) {
                channel.basicAck(messageProperties.getDeliveryTag(), false);
            }
        }
    }

    private void asyncExecute(JSONObject paramData) throws InvocationTargetException {
        // 获得当前command
        String method = paramData.getString("method");
        // 获取当前服务的反射方法调用
        FastMethod fastMethod = getFastMethod(method);
        if (fastMethod == null) {
            return;
        }
        // 获取data数据
        JSONObject data = paramData.getJSONObject("data");
        if (data == null) {
            return;
        }
        // 通过发射来调用方法
        fastMethod.invoke(rpcServer, new Object[]{data});
    }

    private JSONObject syncExecute(JSONObject paramData) throws InvocationTargetException {
        // 获得当前command
        String method = paramData.getString("method");
        // 获取当前服务的反射方法调用
        FastMethod fastMethod = getFastMethod(method);
        if (fastMethod == null) {
            return null;
        }
        // 获取data数据
        JSONObject param = paramData.getJSONObject("data");
        if (param == null) {
            return null;
        }
        // 通过反射来调用方法
        return (JSONObject) fastMethod.invoke(rpcServer, new Object[]{param});
    }

    private FastMethod getFastMethod(String method) {
        // 校验参数
        if (StringUtils.isBlank(method)) {
            return null;
        }
        String key = type + "_" + name.concat("_").concat(method);
        // 通过缓存来优化性能
        FastMethod fastMethod = fastMethodMap.get(key);
        if (fastMethod != null) {
            return fastMethod;
        }
        try {
            Class<?> rpcServerClass = rpcServer.getClass();
            Method targetMethod = rpcServerClass.getMethod(method, JSONObject.class);
            if (targetMethod != null && targetMethod.isAnnotationPresent(RPCMethod.class)) {
                fastMethod = FastClass.create(rpcServerClass).getMethod(method, new Class[]{JSONObject.class});
                if (fastMethod != null) {
                    fastMethodMap.put(key, fastMethod);
                    return fastMethod;
                }
            }
        } catch (NoSuchMethodException e) {
            // logger.error(e.getMessage(), e);
        }
        logger.error("接口不存在, " + type + " RPCServer: " + name + ", Method: " + method);
        return null;
    }

}
