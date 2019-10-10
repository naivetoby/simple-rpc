package vip.toby.rpc.server;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import vip.toby.rpc.annotation.RPCServer;
import vip.toby.rpc.entity.RPCServerType;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ConditionalOnProperty(prefix = "simple-rpc", name = "mode", havingValue = "server")
@Component
public class RPCServerPostProcessor implements BeanPostProcessor {

    @Autowired
    @Lazy
    private ConnectionFactory connectionFactory;
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
                if (annotation instanceof RPCServer) {
                    rpcServerHandler(bean, (RPCServer) annotation);
                }
            }
        }
        return bean;
    }

    // 启动服务
    private void rpcServerHandler(Object rpcServerObject, RPCServer rpcServer) {
        String queueName = rpcServer.name();
        for (RPCServerType rpcServerType : rpcServer.type()) {
            switch (rpcServerType) {
                case SYNC:
                    Map<String, Object> params = new HashMap<>();
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    Queue syncQueue = queue(queueName, rpcServerType, false, params);
                    DirectExchange syncDirectExchange = directExchange(queueName, rpcServerType);
                    binding(queueName, rpcServerType, syncQueue, syncDirectExchange);
                    RPCServerHandler syncRPCServerHandler = rpcServerHandler(queueName, rpcServerType, rpcServerObject);
                    simpleMessageListenerContainer(queueName, rpcServerType, syncRPCServerHandler, rpcServer.threadNum());
                    break;
                case ASYNC:
                    Queue asyncQueue = queue(queueName, rpcServerType, true, null);
                    DirectExchange asyncDirectExchange = directExchange(queueName, rpcServerType);
                    binding(queueName, rpcServerType, asyncQueue, asyncDirectExchange);
                    RPCServerHandler asyncRPCServerHandler = rpcServerHandler(queueName, rpcServerType, rpcServerObject);
                    simpleMessageListenerContainer(queueName, rpcServerType, asyncRPCServerHandler, rpcServer.threadNum());
                    break;
                default:
                    break;
            }
        }
    }

    // 实例化 Queue
    private Queue queue(String queueName, RPCServerType rpcServerType, boolean durable, Map<String, Object> params) {
        return registerBean(rpcServerType.getName() + queueName + "Queue", Queue.class, rpcServerType == RPCServerType.ASYNC ? (queueName + ".async") : queueName, durable, false, false, params);
    }

    // 实例化 DirectExchange
    private DirectExchange directExchange(String queueName, RPCServerType rpcServerType) {
        return registerBean(rpcServerType.getName() + queueName + "DirectExchange", DirectExchange.class, "simple.rpc", true, false);
    }

    // 实例化 Binding
    private Binding binding(String queueName, RPCServerType rpcServerType, Queue queue, DirectExchange directExchange) {
        return registerBean(rpcServerType.getName() + queueName + "Binding", Binding.class, queueName, Binding.DestinationType.QUEUE, directExchange.getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    // 实例化 RPCServerHandler
    private RPCServerHandler rpcServerHandler(String queueName, RPCServerType rpcServerType, Object rpcServerObject) {
        return registerBean(rpcServerType.getName() + queueName + "RPCServerHandler", RPCServerHandler.class, rpcServerObject, queueName, rpcServerType);
    }

    // 实例化 SimpleMessageListenerContainer
    private SimpleMessageListenerContainer simpleMessageListenerContainer(String queueName, RPCServerType rpcServerType, RPCServerHandler handler, int threadNum) {
        SimpleMessageListenerContainer container = registerBean(rpcServerType.getName() + queueName + "MessageListenerContainer", SimpleMessageListenerContainer.class, connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(handler);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setConcurrentConsumers(threadNum);
        return container;
    }

    private <T> T registerBean(String name, Class<T> clazz, Object... args) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (args.length > 0) {
            for (Object arg : args) {
                beanDefinitionBuilder.addConstructorArgValue(arg);
            }
        }
        BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        BeanDefinitionRegistry beanFactory = (BeanDefinitionRegistry) applicationContext.getBeanFactory();
        beanFactory.registerBeanDefinition(name, beanDefinition);
        return applicationContext.getBean(name, clazz);
    }
}
