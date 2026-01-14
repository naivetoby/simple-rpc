package vip.toby.rpc.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.properties.RpcProperties;

import javax.annotation.Nonnull;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.UUID;

/**
 * RpcClientProxyFactory
 *
 * @author toby
 */
@Slf4j
public class RpcClientProxyFactory<T> implements FactoryBean<T>, BeanFactoryAware {

    private BeanFactory beanFactory;
    private final Class<T> rpcClientInterface;
    private ConnectionFactory connectionFactory;
    private DirectExchange syncReplyDirectExchange;
    private RpcProperties rpcProperties;

    public RpcClientProxyFactory(Class<T> rpcClientInterface) {
        this.rpcClientInterface = rpcClientInterface;
    }

    @Override
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @SuppressWarnings("unchecked")
    @Override
    public T getObject() {
        RabbitTemplate sender;
        final RpcClient rpcClient = this.rpcClientInterface.getAnnotation(RpcClient.class);
        assert rpcClient != null;
        final String rpcName = rpcClient.value();
        final RpcType rpcType = rpcClient.type();
        final int replyTimeout = rpcClient.replyTimeout();
        if (rpcType == RpcType.SYNC) {
            final Queue replyQueue = replyQueue(rpcName, UUID.randomUUID().toString());
            replyBinding(rpcName, replyQueue);
            final RabbitTemplate syncSender = syncSender(rpcName, replyQueue, replyTimeout, getConnectionFactory());
            replyMessageListenerContainer(rpcName, replyQueue, syncSender, getConnectionFactory());
            sender = syncSender;
        } else if (rpcType == RpcType.ASYNC) {
            sender = asyncSender(rpcName, getConnectionFactory());
        } else {
            sender = delaySender(rpcName, getConnectionFactory());
        }
        return (T) Proxy.newProxyInstance(this.rpcClientInterface.getClassLoader(), new Class[]{this.rpcClientInterface}, new RpcClientProxy<>(this.rpcClientInterface, rpcName, rpcType, sender, getRpcProperties(), replyTimeout));
    }

    @Override
    public Class<T> getObjectType() {
        return this.rpcClientInterface;
    }

    /**
     * 实例化 replyQueue
     */
    private Queue replyQueue(String rpcName, String rabbitClientId) {
        return registerBean(RpcType.SYNC.getName() + "-ReplyQueue-" + rpcName, Queue.class, rpcName + ".reply." + rabbitClientId, false, false, true);
    }

    /**
     * 实例化 ReplyBinding
     */
    private void replyBinding(String rpcName, Queue queue) {
        registerBean(RpcType.SYNC.getName() + "-ReplyBinding-" + rpcName, Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getSyncReplyDirectExchange().getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 ReplyMessageListenerContainer
     */
    private void replyMessageListenerContainer(
            String rpcName,
            Queue queue,
            RabbitTemplate syncSender,
            ConnectionFactory connectionFactory
    ) {
        final SimpleMessageListenerContainer replyMessageListenerContainer = registerBean(RpcType.SYNC.getName() + "-ReplyMessageListenerContainer-" + rpcName, SimpleMessageListenerContainer.class, connectionFactory);
        replyMessageListenerContainer.setQueueNames(queue.getName());
        replyMessageListenerContainer.setMessageListener(syncSender);
        replyMessageListenerContainer.setErrorHandler(t -> {
            final Throwable cause = t.getCause();
            if (t instanceof org.springframework.amqp.rabbit.support.ListenerExecutionFailedException && cause instanceof org.springframework.amqp.AmqpRejectAndDontRequeueException) {
                if (cause.getMessage() != null && cause.getMessage().contains("Reply received after timeout")) {
                    // log.debug("RPC Reply Listener timeout for rpc: {}", rpcName);
                    return;
                }
            }
            log.error("RPC Reply Listener failed for rpc: {}", rpcName, t);
        });
    }

    /**
     * 实例化 AsyncSender
     */
    private RabbitTemplate asyncSender(String rpcName, ConnectionFactory connectionFactory) {
        final RabbitTemplate asyncSender = registerBean(RpcType.ASYNC.getName() + "-Sender-" + rpcName, RabbitTemplate.class, connectionFactory);
        asyncSender.setRoutingKey(rpcName + ".async");
        asyncSender.setUserCorrelationId(true);
        return asyncSender;
    }

    /**
     * 实例化 DelaySender
     */
    private RabbitTemplate delaySender(String rpcName, ConnectionFactory connectionFactory) {
        final RabbitTemplate delaySender = registerBean(RpcType.DELAY.getName() + "-Sender-" + rpcName, RabbitTemplate.class, connectionFactory);
        delaySender.setRoutingKey(rpcName + ".delay");
        delaySender.setUserCorrelationId(true);
        return delaySender;
    }

    /**
     * 实例化 SyncSender
     */
    private RabbitTemplate syncSender(
            String rpcName,
            Queue replyQueue,
            int replyTimeout,
            ConnectionFactory connectionFactory
    ) {
        final RetryPolicy defaultRetryPolicy = RetryPolicy.withDefaults();
        final RetryTemplate retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(defaultRetryPolicy);
        final RabbitTemplate syncSender = registerBean(RpcType.SYNC.getName() + "-Sender-" + rpcName, RabbitTemplate.class, connectionFactory);
        syncSender.setDefaultReceiveQueue(rpcName);
        syncSender.setRoutingKey(rpcName);
        syncSender.setReplyAddress(replyQueue.getName());
        syncSender.setReplyTimeout(replyTimeout);
        syncSender.setRetryTemplate(retryTemplate);
        syncSender.setUserCorrelationId(true);
        return syncSender;
    }

    /**
     * 实例化 ConnectionFactory
     */
    private ConnectionFactory getConnectionFactory() {
        if (this.connectionFactory == null) {
            this.connectionFactory = this.beanFactory.getBean(ConnectionFactory.class);
        }
        return this.connectionFactory;
    }

    /**
     * 实例化 RpcProperties
     */
    private RpcProperties getRpcProperties() {
        if (this.rpcProperties == null) {
            if (this.beanFactory.containsBean("rpcProperties")) {
                this.rpcProperties = this.beanFactory.getBean("rpcProperties", RpcProperties.class);
            } else {
                this.rpcProperties = registerBean("rpcProperties", RpcProperties.class);
            }
        }
        return this.rpcProperties;
    }

    /**
     * 实例化 SyncReplyDirectExchange
     */
    private DirectExchange getSyncReplyDirectExchange() {
        if (this.syncReplyDirectExchange == null) {
            if (this.beanFactory.containsBean("syncReplyDirectExchange")) {
                this.syncReplyDirectExchange = this.beanFactory.getBean("syncReplyDirectExchange", DirectExchange.class);
            } else {
                this.syncReplyDirectExchange = registerBean("syncReplyDirectExchange", DirectExchange.class, "simple.rpc.sync.reply", true, false);
            }
        }
        return this.syncReplyDirectExchange;
    }

    /**
     * 对象实例化并注册到 Spring 上下文
     */
    private <L> L registerBean(String name, Class<L> clazz, Object... args) {
        final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (args != null) {
            for (Object arg : args) {
                beanDefinitionBuilder.addConstructorArgValue(arg);
            }
        }
        final BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        final BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) this.beanFactory;
        if (beanDefinitionRegistry.isBeanNameInUse(name)) {
            throw new RuntimeException("BeanName: " + name + " 重复");
        }
        beanDefinitionRegistry.registerBeanDefinition(name, beanDefinition);
        return this.beanFactory.getBean(name, clazz);
    }

}
