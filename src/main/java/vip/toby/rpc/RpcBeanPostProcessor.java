package vip.toby.rpc;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.client.RpcClientProxyFactory;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.server.RpcServerHandler;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RpcBeanPostProcessor
 *
 * @author toby
 */
@Component
public class RpcBeanPostProcessor implements BeanPostProcessor {

    @Autowired
    @Lazy
    private ConnectionFactory connectionFactory;
    @Autowired
    @Lazy
    private DirectExchange syncDirectExchange;
    @Autowired
    @Lazy
    private DirectExchange syncReplyDirectExchange;
    @Autowired
    @Lazy
    private DirectExchange asyncDirectExchange;
    @Autowired
    private ConfigurableApplicationContext applicationContext;

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
                    rpcServerHandler(bean, (RpcServer) annotation);
                }
                if (annotation instanceof RpcClient) {
                    rpcClientSender(bean, (RpcClient) annotation);
                }
            }
        }
        return bean;
    }

    /**
     * 服务端
     */
    private void rpcServerHandler(Object rpcServerObject, RpcServer rpcServer) {
        String rpcName = rpcServer.name();
        for (RpcType rpcType : rpcServer.type()) {
            switch (rpcType) {
                case SYNC:
                    Map<String, Object> params = new HashMap<>(1);
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    Queue syncQueue = queue(rpcName, rpcType, false, params);
                    binding(rpcName, rpcType, syncQueue);
                    RpcServerHandler syncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerObject);
                    messageListenerContainer(rpcName, rpcType, syncQueue, syncServerHandler, rpcServer.threadNum());
                    break;
                case ASYNC:
                    Queue asyncQueue = queue(rpcName, rpcType, true, null);
                    binding(rpcName, rpcType, asyncQueue);
                    RpcServerHandler asyncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerObject);
                    messageListenerContainer(rpcName, rpcType, asyncQueue, asyncServerHandler, rpcServer.threadNum());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 客户端
     */
    private void rpcClientSender(Object rpcClientObject, RpcClient rpcClient) {
        String rpcName = rpcClient.name();
        RpcType rpcType = rpcClient.type();
        switch (rpcType) {
            case SYNC:
                RabbitTemplate syncSender = syncSender(rpcName, replyQueue(rpcName, UUID.randomUUID().toString()), rpcClient.replyTimeout(), rpcClient.maxAttempts());
                replyMessageListenerContainer(rpcName, syncSender);
                registerBean(rpcClientObject.getClass().getName(), RpcClientProxyFactory.class, rpcClientObject, rpcType, syncSender);
                break;
            case ASYNC:
                RabbitTemplate asyncSender = asyncSender(rpcName);
                registerBean(rpcClientObject.getClass().getName(), RpcClientProxyFactory.class, rpcClientObject, rpcType, asyncSender);
                break;
            default:
                break;
        }

    }

    /**
     * 实例化 Queue
     */
    private Queue queue(String rpcName, RpcType rpcType, boolean durable, Map<String, Object> params) {
        return registerBean(rpcType.getValue() + "_" + rpcName + "_Queue", Queue.class, rpcType == RpcType.ASYNC ? (rpcName + ".async") : rpcName, durable, false, rpcType == RpcType.SYNC, params);
    }

    /**
     * 实例化 Binding
     */
    private Binding binding(String rpcName, RpcType rpcType, Queue queue) {
        return registerBean(rpcType.getValue() + "_" + rpcName + "_Binding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, (rpcType == RpcType.SYNC ? syncDirectExchange : asyncDirectExchange).getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 RpcServerHandler
     */
    private RpcServerHandler rpcServerHandler(String rpcName, RpcType rpcType, Object rpcServerObject) {
        return registerBean(rpcType.getValue() + "_" + rpcName + "_RpcServerHandler", RpcServerHandler.class, rpcServerObject, rpcName, rpcType);
    }

    /**
     * 实例化 SimpleMessageListenerContainer
     */
    private SimpleMessageListenerContainer messageListenerContainer(String rpcName, RpcType rpcType, Queue queue, RpcServerHandler rpcServerHandler, int threadNum) {
        SimpleMessageListenerContainer messageListenerContainer = registerBean(rpcType.getValue() + "_" + rpcName + "_MessageListenerContainer", SimpleMessageListenerContainer.class, connectionFactory);
        messageListenerContainer.setQueueNames(queue.getName());
        messageListenerContainer.setMessageListener(rpcServerHandler);
        messageListenerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        messageListenerContainer.setConcurrentConsumers(threadNum);
        return messageListenerContainer;
    }

    /**
     * 实例化 replyQueue
     */
    private Queue replyQueue(String rpcName, String rabbitClientId) {
        return registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyQueue", Queue.class, rpcName + ".reply." + rabbitClientId);
    }

    /**
     * 实例化 ReplyBinding
     */
    private Binding replyBinding(String rpcName, Queue queue) {
        return registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyBinding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, syncReplyDirectExchange.getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 ReplyMessageListenerContainer
     */
    private SimpleMessageListenerContainer replyMessageListenerContainer(String rpcName, RabbitTemplate syncSender) {
        SimpleMessageListenerContainer replyMessageListenerContainer = registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyMessageListenerContainer", SimpleMessageListenerContainer.class, connectionFactory);
        replyMessageListenerContainer.setQueueNames(rpcName);
        replyMessageListenerContainer.setMessageListener(syncSender);
        return replyMessageListenerContainer;
    }

    /**
     * 实例化 AsyncSender
     */
    private RabbitTemplate asyncSender(String rpcName) {
        RabbitTemplate asyncSender = registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class);
        asyncSender.setConnectionFactory(connectionFactory);
        asyncSender.setDefaultReceiveQueue(rpcName + ".async");
        asyncSender.setRoutingKey(rpcName + ".async");
        return asyncSender;
    }

    /**
     * 实例化 SyncSender
     */
    private RabbitTemplate syncSender(String rpcName, Queue replyQueue, int replyTimeout, int maxAttempts) {
        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
        simpleRetryPolicy.setMaxAttempts(maxAttempts);
        RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(simpleRetryPolicy);
        RabbitTemplate syncSender = registerBean(RpcType.ASYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class);
        syncSender.setConnectionFactory(connectionFactory);
        syncSender.setDefaultReceiveQueue(rpcName);
        syncSender.setRoutingKey(rpcName);
        syncSender.setReplyAddress(replyQueue.getName());
        syncSender.setReplyTimeout(replyTimeout);
        syncSender.setRetryTemplate(retryTemplate);
        return syncSender;
    }

    /**
     * 实例化
     */
    private <T> T registerBean(String name, Class<T> clazz, Object... args) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (args.length > 0) {
            for (Object arg : args) {
                beanDefinitionBuilder.addConstructorArgValue(arg);
            }
        }
        BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        BeanDefinitionRegistry beanFactory = (BeanDefinitionRegistry) applicationContext.getBeanFactory();
        if (beanFactory.isBeanNameInUse(name)) {
            throw new RuntimeException("Bean: " + name + " 实例化时发生重复");
        }
        beanFactory.registerBeanDefinition(name, beanDefinition);
        return applicationContext.getBean(name, clazz);
    }

}
