package vip.toby.rpc.client;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.FactoryBean;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.entity.RpcType;

import java.lang.reflect.Proxy;

/**
 * RpcClientProxyFactory
 *
 * @author toby
 */
public class RpcClientProxyFactory implements FactoryBean {

    private Class<?> rpcClientInterface;
    private RabbitTemplate sender;

    private String rpcName;
    private RpcType rpcType;
    private DirectExchange syncReplyDirectExchange;

    public RpcClientProxyFactory(Class<?> rpcClientInterface) {
        this.rpcClientInterface = rpcClientInterface;
        RpcClient rpcClient = this.rpcClientInterface.getAnnotation(RpcClient.class);
        this.rpcName = rpcClient.name();
        this.rpcType = rpcClient.type();
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        if (this.sender == null) {

//            if (this.rpcType == RpcType.SYNC) {
//                Queue replyQueue = replyQueue(this.rpcName, UUID.randomUUID().toString());
//                replyBinding(this.rpcName, replyQueue);
//                RabbitTemplate syncSender = syncSender(this.rpcName, replyQueue, this.replyTimeout, this.maxAttempts, connectionFactory);
//                replyMessageListenerContainer(this.rpcName, syncSender, connectionFactory);
//                this.sender = syncSender;
//            }
//            this.sender = asyncSender(this.rpcName, connectionFactory);
        }
    }

    public void setRpcClientInterface(Class<?> rpcClientInterface) {
        this.rpcClientInterface = rpcClientInterface;
    }

    @Override
    public Object getObject() throws Exception {
        return Proxy.newProxyInstance(this.rpcClientInterface.getClassLoader(), new Class[]{this.rpcClientInterface}, new RpcClientProxy(this.rpcClientInterface, this.rpcName, this.rpcType, this.sender));
    }

    @Override
    public Class<?> getObjectType() {
        return this.rpcClientInterface;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

//    /**
//     * 实例化 replyQueue
//     */
//    private Queue replyQueue(String rpcName, String rabbitClientId) {
//        return registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyQueue", Queue.class, rpcName + ".reply." + rabbitClientId, true, false, false);
//    }
//
//    /**
//     * 实例化 ReplyBinding
//     */
//    private void replyBinding(String rpcName, Queue queue) {
//        registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyBinding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getSyncReplyDirectExchange().getName(), queue.getName(), Collections.<String, Object>emptyMap());
//    }
//
//    /**
//     * 实例化 ReplyMessageListenerContainer
//     */
//    private void replyMessageListenerContainer(String rpcName, RabbitTemplate syncSender, ConnectionFactory connectionFactory) {
//        SimpleMessageListenerContainer replyMessageListenerContainer = registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyMessageListenerContainer", SimpleMessageListenerContainer.class, connectionFactory);
//        replyMessageListenerContainer.setQueueNames(rpcName);
//        replyMessageListenerContainer.setMessageListener(syncSender);
//    }
//
//    /**
//     * 实例化 AsyncSender
//     */
//    private RabbitTemplate asyncSender(String rpcName, ConnectionFactory connectionFactory) {
//        RabbitTemplate asyncSender = registerBean(RpcType.ASYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class, connectionFactory);
//        asyncSender.setDefaultReceiveQueue(rpcName + ".async");
//        asyncSender.setRoutingKey(rpcName + ".async");
//        return asyncSender;
//    }
//
//    /**
//     * 实例化 SyncSender
//     */
//    private RabbitTemplate syncSender(String rpcName, Queue replyQueue, int replyTimeout, int maxAttempts, ConnectionFactory connectionFactory) {
//        SimpleRetryPolicy simpleRetryPolicy = new SimpleRetryPolicy();
//        simpleRetryPolicy.setMaxAttempts(maxAttempts);
//        RetryTemplate retryTemplate = new RetryTemplate();
//        retryTemplate.setRetryPolicy(simpleRetryPolicy);
//        RabbitTemplate syncSender = registerBean(RpcType.SYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class, connectionFactory);
//        syncSender.setDefaultReceiveQueue(rpcName);
//        syncSender.setRoutingKey(rpcName);
//        syncSender.setReplyAddress(replyQueue.getName());
//        syncSender.setReplyTimeout(replyTimeout);
//        syncSender.setRetryTemplate(retryTemplate);
//        return syncSender;
//    }
//
//    /**
//     * 实例化 SyncReplyDirectExchange
//     */
//    private DirectExchange getSyncReplyDirectExchange() {
//        if (this.syncReplyDirectExchange == null) {
//            this.syncReplyDirectExchange = registerBean("syncReplyDirectExchange", DirectExchange.class, "simple.rpc.sync.reply", true, false);
//        }
//        return this.syncReplyDirectExchange;
//    }
//
//    /**
//     * 对象实例化并注册到Spring上下文
//     */
//    private <T> T registerBean(String name, Class<T> clazz, Object... args) {
//        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
//        if (args != null && args.length > 0) {
//            for (Object arg : args) {
//                beanDefinitionBuilder.addConstructorArgValue(arg);
//            }
//        }
//        BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
//        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) this.beanFactory;
//        if (beanDefinitionRegistry.isBeanNameInUse(name)) {
//            throw new RuntimeException("Bean: " + name + " 实例化时发生重复");
//        }
//        beanDefinitionRegistry.registerBeanDefinition(name, beanDefinition);
//        return this.beanFactory.getBean(name, clazz);
//    }

}
