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
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.entity.ServerStatus;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RpcServerHandler
 *
 * @author toby
 */
public class RpcServerHandler implements ChannelAwareMessageListener, InitializingBean {

    private final static Logger LOGGER = LoggerFactory.getLogger(RpcServerHandler.class);

    private final static Map<String, FastMethod> FAST_METHOD_MAP = new ConcurrentHashMap<>();

    @Value("${spring.rabbitmq.slow-call-time:1000}")
    private int slowCallTime;

    private final Object rpcServerBean;
    private final String rpcName;
    private final RpcType rpcType;

    RpcServerHandler(Object rpcServerBean, String rpcName, RpcType rpcType) {
        this.rpcServerBean = rpcServerBean;
        this.rpcName = rpcName;
        this.rpcType = rpcType;
    }

    @Override
    public void afterPropertiesSet() {
        // 初始化所有接口
        Class<?> rpcServerClass = this.rpcServerBean.getClass();
        for (Method targetMethod : rpcServerClass.getMethods()) {
            if (targetMethod != null && targetMethod.isAnnotationPresent(RpcServerMethod.class)) {
                String methodName = targetMethod.getAnnotation(RpcServerMethod.class).name();
                if (StringUtils.isBlank(methodName)) {
                    methodName = targetMethod.getName();
                }
                String key = this.rpcType.getValue() + "_" + this.rpcName + "_" + methodName;
                if (FAST_METHOD_MAP.containsKey(key)) {
                    throw new RuntimeException("Method: " + methodName + " 重复");
                }
                FastMethod fastMethod = FastClass.create(rpcServerClass).getMethod(targetMethod.getName(), new Class[]{JSONObject.class});
                if (fastMethod != null) {
                    FAST_METHOD_MAP.put(key, fastMethod);
                    LOGGER.debug("接口注册成功, " + this.rpcType.getName() + " RpcServer: " + this.rpcName + ", Method: " + methodName);
                }
            }
        }
        LOGGER.info(this.rpcType.getName() + " RpcServer: " + this.rpcName + " 已启动");
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
            LOGGER.info(this.rpcType.getName() + " RpcServer: " + this.rpcName + " 接收到消息: " + messageStr);
            // 构建返回JSON值
            JSONObject resultJson = new JSONObject();
            try {
                // 组装参数json
                JSONObject paramData = JSON.parseObject(messageStr);
                // 异步执行任务
                if (RpcType.ASYNC == this.rpcType) {
                    long start = System.currentTimeMillis();
                    asyncExecute(paramData);
                    double offset = System.currentTimeMillis() - start;
                    LOGGER.info("耗时: " + offset + "ms, paramData: " + paramData);
                    if (offset > this.slowCallTime) {
                        LOGGER.warn(this.rpcType.getName() + " RpcServer: " + this.rpcName + " 调用时间过长, 共耗时: " + offset + "ms, paramData: " + paramData);
                    }
                    return;
                }
                // 同步执行任务并返回结果
                long start = System.currentTimeMillis();
                JSONObject data = syncExecute(paramData);
                if (data != null) {
                    double offset = System.currentTimeMillis() - start;
                    LOGGER.info("耗时: " + offset + "ms, paramData: " + paramData);
                    if (offset > this.slowCallTime) {
                        LOGGER.warn(this.rpcType.getName() + " RpcServer: " + this.rpcName + " 调用时间过长, 共耗时: " + offset + "ms, paramData: " + paramData);
                    }
                    // 修改状态
                    serverStatus = ServerStatus.SUCCESS;
                    resultJson.put("data", data);
                } else {
                    serverStatus = ServerStatus.NOT_EXIST;
                }
            } catch (InvocationTargetException e) {
                // 获取目标异常
                Throwable t = e.getTargetException();
                LOGGER.error("Method Invoke Target Exception! Message: " + messageStr);
                LOGGER.error(t.getMessage(), t);
            } catch (Exception e) {
                LOGGER.error("Method Invoke Exception! Message: " + messageStr);
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
            LOGGER.error(this.rpcType.getName() + " RpcServer: " + this.rpcName + " Exception! Message: " + messageStr);
            LOGGER.error(e.getMessage(), e);
        } finally {
            // 确认处理任务
            if (messageProperties != null) {
                channel.basicAck(messageProperties.getDeliveryTag(), false);
            }
        }
    }

    /**
     * 同步调用
     */
    private void asyncExecute(JSONObject paramData) throws InvocationTargetException {
        // 获得当前command
        String command = paramData.getString("command");
        if (StringUtils.isBlank(command)) {
            throw new RuntimeException("Command 参数为空");
        }
        // 获取当前服务的反射方法调用
        String key = this.rpcType.getValue() + "_" + this.rpcName + "_" + command;
        // 通过缓存来优化性能
        FastMethod fastMethod = FAST_METHOD_MAP.get(key);
        if (fastMethod == null) {
            LOGGER.error("接口不存在, " + this.rpcType.getName() + " RpcServer: " + this.rpcName + ", Method: " + command);
            return;
        }
        // 获取data数据
        JSONObject data = paramData.getJSONObject("data");
        if (data == null) {
            throw new RuntimeException("Data 参数错误");
        }
        // 通过发射来调用方法
        fastMethod.invoke(this.rpcServerBean, new Object[]{data});
    }

    /**
     * 异步调用
     */
    private JSONObject syncExecute(JSONObject paramData) throws InvocationTargetException {
        // 获得当前command
        String command = paramData.getString("command");
        if (StringUtils.isBlank(command)) {
            throw new RuntimeException("Command 参数为空");
        }
        // 获取当前服务的反射方法调用
        String key = this.rpcType.getValue() + "_" + this.rpcName + "_" + command;
        // 通过缓存来优化性能
        FastMethod fastMethod = FAST_METHOD_MAP.get(key);
        if (fastMethod == null) {
            LOGGER.error("接口不存在, " + this.rpcType.getName() + " RpcServer: " + this.rpcName + ", Method: " + command);
            return null;
        }
        // 获取data数据
        JSONObject param = paramData.getJSONObject("data");
        if (param == null) {
            throw new RuntimeException("Data 参数错误");
        }
        // 通过反射来调用方法
        return (JSONObject) fastMethod.invoke(this.rpcServerBean, new Object[]{param});
    }

}
