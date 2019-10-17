package vip.toby.rpc.client;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.util.RpcUtil;

import java.util.Collections;
import java.util.UUID;

/**
 * RpcClientScanner
 *
 * @author toby
 */
public class RpcClientScanner {

    @Autowired
    private ConfigurableApplicationContext applicationContext;

    private final ConnectionFactory connectionFactory;
    private final DirectExchange syncReplyDirectExchange;

    public RpcClientScanner(ConnectionFactory connectionFactory, DirectExchange syncReplyDirectExchange) {
        this.connectionFactory = connectionFactory;
        this.syncReplyDirectExchange = syncReplyDirectExchange;
    }

    /**
     * 客户端
     */
    private void rpcClientSender(Class<?> rpcClientInterface, RpcClient rpcClient) {
        String rpcName = rpcClient.name();
        RpcType rpcType = rpcClient.type();
        switch (rpcType) {
            case SYNC:
                Queue replyQueue = replyQueue(rpcName, UUID.randomUUID().toString());
                replyBinding(rpcName, replyQueue);
                RabbitTemplate syncSender = syncSender(rpcName, replyQueue, rpcClient.replyTimeout(), rpcClient.maxAttempts());
                replyMessageListenerContainer(rpcName, syncSender);
                RpcUtil.registerBean(applicationContext, rpcClientInterface.getName(), RpcClientProxyFactory.class, rpcClientInterface, rpcName, rpcType, syncSender);
                break;
            case ASYNC:
                RabbitTemplate asyncSender = asyncSender(rpcName);
                RpcUtil.registerBean(applicationContext, rpcClientInterface.getName(), RpcClientProxyFactory.class, rpcClientInterface, rpcName, rpcType, asyncSender);
                break;
            default:
                break;
        }

    }

    /**
     * 实例化 replyQueue
     */
    private Queue replyQueue(String rpcName, String rabbitClientId) {
        return RpcUtil.registerBean(applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyQueue", Queue.class, rpcName + ".reply." + rabbitClientId);
    }

    /**
     * 实例化 ReplyBinding
     */
    private Binding replyBinding(String rpcName, Queue queue) {
        return RpcUtil.registerBean(applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyBinding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, syncReplyDirectExchange.getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 ReplyMessageListenerContainer
     */
    private SimpleMessageListenerContainer replyMessageListenerContainer(String rpcName, RabbitTemplate syncSender) {
        SimpleMessageListenerContainer replyMessageListenerContainer = RpcUtil.registerBean(applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyMessageListenerContainer", SimpleMessageListenerContainer.class, connectionFactory);
        replyMessageListenerContainer.setQueueNames(rpcName);
        replyMessageListenerContainer.setMessageListener(syncSender);
        return replyMessageListenerContainer;
    }

    /**
     * 实例化 AsyncSender
     */
    private RabbitTemplate asyncSender(String rpcName) {
        RabbitTemplate asyncSender = RpcUtil.registerBean(applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class);
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
        RabbitTemplate syncSender = RpcUtil.registerBean(applicationContext, RpcType.ASYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class);
        syncSender.setConnectionFactory(connectionFactory);
        syncSender.setDefaultReceiveQueue(rpcName);
        syncSender.setRoutingKey(rpcName);
        syncSender.setReplyAddress(replyQueue.getName());
        syncSender.setReplyTimeout(replyTimeout);
        syncSender.setRetryTemplate(retryTemplate);
        return syncSender;
    }
}
