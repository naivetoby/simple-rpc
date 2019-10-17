package vip.toby.rpc.server;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.util.RpcUtil;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * RpcServerPostProcessor
 *
 * @author toby
 */
public class RpcServerPostProcessor implements BeanPostProcessor {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    private final ConnectionFactory connectionFactory;
    private final DirectExchange syncDirectExchange;
    private final DirectExchange asyncDirectExchange;

    public RpcServerPostProcessor(ConnectionFactory connectionFactory, DirectExchange syncDirectExchange, DirectExchange asyncDirectExchange) {
        this.connectionFactory = connectionFactory;
        this.syncDirectExchange = syncDirectExchange;
        this.asyncDirectExchange = asyncDirectExchange;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> rpcServerClass = bean.getClass();
        if (rpcServerClass.getAnnotations() != null && rpcServerClass.getAnnotations().length > 0) {
            for (Annotation annotation : rpcServerClass.getAnnotations()) {
                if (annotation instanceof RpcServer) {
                    rpcServerStart(bean, (RpcServer) annotation);
                }
            }
        }
        return bean;
    }

    /**
     * 启动服务监听
     */
    private void rpcServerStart(Object rpcServerBean, RpcServer rpcServer) {
        String rpcName = rpcServer.name();
        for (RpcType rpcType : rpcServer.type()) {
            switch (rpcType) {
                case SYNC:
                    Map<String, Object> params = new HashMap<>(1);
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    Queue syncQueue = queue(rpcName, rpcType, false, params);
                    binding(rpcName, rpcType, syncQueue);
                    RpcServerHandler syncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean);
                    messageListenerContainer(rpcName, rpcType, syncQueue, syncServerHandler, rpcServer.threadNum());
                    break;
                case ASYNC:
                    Queue asyncQueue = queue(rpcName, rpcType, true, null);
                    binding(rpcName, rpcType, asyncQueue);
                    RpcServerHandler asyncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean);
                    messageListenerContainer(rpcName, rpcType, asyncQueue, asyncServerHandler, rpcServer.threadNum());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 实例化 Queue
     */
    private Queue queue(String rpcName, RpcType rpcType, boolean durable, Map<String, Object> params) {
        return RpcUtil.registerBean(applicationContext, rpcType.getValue() + "_" + rpcName + "_Queue", Queue.class, rpcType == RpcType.ASYNC ? (rpcName + ".async") : rpcName, durable, false, rpcType == RpcType.SYNC, params);
    }

    /**
     * 实例化 Binding
     */
    private Binding binding(String rpcName, RpcType rpcType, Queue queue) {
        return RpcUtil.registerBean(applicationContext, rpcType.getValue() + "_" + rpcName + "_Binding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, (rpcType == RpcType.SYNC ? syncDirectExchange : asyncDirectExchange).getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 RpcServerHandler
     */
    private RpcServerHandler rpcServerHandler(String rpcName, RpcType rpcType, Object rpcServerBean) {
        return RpcUtil.registerBean(applicationContext, rpcType.getValue() + "_" + rpcName + "_RpcServerHandler", RpcServerHandler.class, rpcServerBean, rpcName, rpcType);
    }

    /**
     * 实例化 SimpleMessageListenerContainer
     */
    private SimpleMessageListenerContainer messageListenerContainer(String rpcName, RpcType rpcType, Queue queue, RpcServerHandler rpcServerHandler, int threadNum) {
        SimpleMessageListenerContainer messageListenerContainer = RpcUtil.registerBean(applicationContext, rpcType.getValue() + "_" + rpcName + "_MessageListenerContainer", SimpleMessageListenerContainer.class, connectionFactory);
        messageListenerContainer.setQueueNames(queue.getName());
        messageListenerContainer.setMessageListener(rpcServerHandler);
        messageListenerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        messageListenerContainer.setConcurrentConsumers(threadNum);
        return messageListenerContainer;
    }

}
