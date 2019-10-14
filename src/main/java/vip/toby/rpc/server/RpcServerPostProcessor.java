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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.entity.RpcServerType;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * RpcServerPostProcessor
 *
 * @author toby
 */
@Component
public class RpcServerPostProcessor implements BeanPostProcessor {

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
                if (annotation instanceof RpcServer) {
                    rpcServerHandler(bean, (RpcServer) annotation);
                }
            }
        }
        return bean;
    }

    /**
     * 启动服务
     */
    private void rpcServerHandler(Object rpcServerObject, RpcServer rpcServer) {
        String queueName = rpcServer.name();
        for (RpcServerType rpcServerType : rpcServer.type()) {
            switch (rpcServerType) {
                case SYNC:
                    Map<String, Object> params = new HashMap<>(1);
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    Queue syncQueue = queue(queueName, rpcServerType, false, params);
                    DirectExchange syncDirectExchange = directExchange(queueName, rpcServerType);
                    binding(queueName, rpcServerType, syncQueue, syncDirectExchange);
                    RpcServerHandler syncServerHandler = rpcServerHandler(queueName, rpcServerType, rpcServerObject);
                    simpleMessageListenerContainer(queueName, rpcServerType, syncServerHandler, rpcServer.threadNum());
                    break;
                case ASYNC:
                    Queue asyncQueue = queue(queueName, rpcServerType, true, null);
                    DirectExchange asyncDirectExchange = directExchange(queueName, rpcServerType);
                    binding(queueName, rpcServerType, asyncQueue, asyncDirectExchange);
                    RpcServerHandler asyncServerHandler = rpcServerHandler(queueName, rpcServerType, rpcServerObject);
                    simpleMessageListenerContainer(queueName, rpcServerType, asyncServerHandler, rpcServer.threadNum());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 实例化 Queue
     */
    private Queue queue(String queueName, RpcServerType rpcServerType, boolean durable, Map<String, Object> params) {
        return registerBean(rpcServerType.getName() + "_" + queueName + "_Queue", Queue.class, rpcServerType == RpcServerType.ASYNC ? (queueName + ".async") : queueName, durable, false, rpcServerType == RpcServerType.SYNC, params);
    }

    /**
     * 实例化 DirectExchange
     */
    private DirectExchange directExchange(String queueName, RpcServerType rpcServerType) {
        return registerBean(rpcServerType.getName() + "_" + queueName + "_DirectExchange", DirectExchange.class, "simple.rpc", true, false);
    }

    /**
     * 实例化 Binding
     */
    private Binding binding(String queueName, RpcServerType rpcServerType, Queue queue, DirectExchange directExchange) {
        return registerBean(rpcServerType.getName() + "_" + queueName + "_Binding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, directExchange.getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 RpcServerHandler
     */
    private RpcServerHandler rpcServerHandler(String queueName, RpcServerType rpcServerType, Object rpcServerObject) {
        return registerBean(rpcServerType.getName() + "_" + queueName + "_RpcServerHandler", RpcServerHandler.class, rpcServerObject, queueName, rpcServerType);
    }

    /**
     * 实例化 SimpleMessageListenerContainer
     */
    private SimpleMessageListenerContainer simpleMessageListenerContainer(String queueName, RpcServerType rpcServerType, RpcServerHandler handler, int threadNum) {
        SimpleMessageListenerContainer container = registerBean(rpcServerType.getName() + "_" + queueName + "_MessageListenerContainer", SimpleMessageListenerContainer.class, connectionFactory);
        container.setQueueNames(rpcServerType == RpcServerType.ASYNC ? (queueName + ".async") : queueName);
        container.setMessageListener(handler);
        container.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        container.setConcurrentConsumers(threadNum);
        return container;
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
