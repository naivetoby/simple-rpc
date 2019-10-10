package vip.toby.rpc.server;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import vip.toby.rpc.annotation.RPCServer;
import vip.toby.rpc.entity.RPCServerType;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

@ConditionalOnProperty(prefix = "simple-rpc", name = "mode", havingValue = "server")
@Component
public class RPCServerPostProcessor implements BeanPostProcessor {

    @Autowired
    @Lazy
    private ConnectionFactory connectionFactory;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> rpcClass = bean.getClass();
        if (rpcClass.getAnnotations() != null && rpcClass.getAnnotations().length > 0) {
            for (Annotation annotation : rpcClass.getAnnotations()) {
                if (annotation instanceof RPCServer) {
                    rpcServerHandler(bean, (RPCServer) annotation);
                }
            }
        }
        return bean;
    }

    // 启动服务
    private void rpcServerHandler(Object bean, RPCServer rpcServer) {
        String queueName = rpcServer.name();
        for (RPCServerType rpcServerType : rpcServer.type()) {
            switch (rpcServerType) {
                case SYNC:
                    Map<String, Object> params = new HashMap<>();
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    Queue syncQueue = new Queue(queueName, false, false, false, params);
                    BindingBuilder.bind(syncQueue).to(new DirectExchange("simple.rpc", true, false)).with(syncQueue.getName());
                    messageListenerContainer(connectionFactory, new RPCServerHandler(bean, queueName, RPCServerType.SYNC.getName()), syncQueue, rpcServer.threadNum());
                    break;
                case ASYNC:
                    Queue asyncQueue = new Queue(queueName.concat(".async"), true, false, false);
                    BindingBuilder.bind(asyncQueue).to(new DirectExchange("simple.rpc", true, false)).with(asyncQueue.getName());
                    messageListenerContainer(connectionFactory, new RPCServerHandler(bean, queueName, RPCServerType.ASYNC.getName()), asyncQueue, rpcServer.threadNum());
                    break;
                default:
                    break;
            }
        }
    }

    private void messageListenerContainer(ConnectionFactory connectionFactory, RPCServerHandler handler, Queue queue, int threadNum) {
        SimpleMessageListenerContainer container = new SimpleMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.setQueueNames(queue.getName());
        container.setMessageListener(handler);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setConcurrentConsumers(threadNum);
    }
}
