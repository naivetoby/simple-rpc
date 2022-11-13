package vip.toby.rpc.server;

import lombok.RequiredArgsConstructor;
import org.hibernate.validator.HibernateValidator;
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
import org.springframework.stereotype.Component;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.properties.RpcProperties;

import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
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
@RequiredArgsConstructor
public class RpcServerPostProcessor implements BeanPostProcessor {

    private final ConfigurableApplicationContext applicationContext;

    private final ConnectionFactory connectionFactory;

    @Autowired(required = false)
    private RpcServerBaseHandlerInterceptor rpcServerBaseHandlerInterceptor;

    private DirectExchange syncDirectExchange;
    private DirectExchange asyncDirectExchange;
    private Validator validator;
    private RpcProperties rpcProperties;

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> rpcServerClass = bean.getClass();
        if (rpcServerClass.getAnnotations().length > 0) {
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
        String rpcName = rpcServer.value();
        for (RpcType rpcType : rpcServer.type()) {
            switch (rpcType) {
                case SYNC -> {
                    Map<String, Object> params = new HashMap<>(1);
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    Queue syncQueue = queue(rpcName, rpcType, params);
                    binding(rpcName, rpcType, syncQueue);
                    RpcServerHandler syncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean, getValidator(), getRpcProperties(), rpcServerBaseHandlerInterceptor);
                    messageListenerContainer(rpcName, rpcType, syncQueue, syncServerHandler, rpcServer.threadNum());
                }
                case ASYNC -> {
                    Queue asyncQueue = queue(rpcName, rpcType, null);
                    binding(rpcName, rpcType, asyncQueue);
                    RpcServerHandler asyncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean, getValidator(), getRpcProperties(), rpcServerBaseHandlerInterceptor);
                    messageListenerContainer(rpcName, rpcType, asyncQueue, asyncServerHandler, rpcServer.threadNum());
                }
                default -> {
                }
            }
        }
    }

    /**
     * 实例化 Queue
     */
    private Queue queue(String rpcName, RpcType rpcType, Map<String, Object> params) {
        return registerBean(this.applicationContext, rpcType.getName() + "-Queue-" + rpcName, Queue.class, rpcType == RpcType.ASYNC ? (rpcName + ".async") : rpcName, rpcType == RpcType.ASYNC, false, false, params);
    }

    /**
     * 实例化 Binding
     */
    private void binding(String rpcName, RpcType rpcType, Queue queue) {
        registerBean(this.applicationContext, rpcType.getName() + "-Binding-" + rpcName, Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getDirectExchange(rpcType).getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 RpcServerHandler
     */
    private RpcServerHandler rpcServerHandler(
            String rpcName,
            RpcType rpcType,
            Object rpcServerBean,
            Validator validator,
            RpcProperties rpcProperties,
            RpcServerHandlerInterceptor rpcServerHandlerInterceptor
    ) {
        return registerBean(this.applicationContext, rpcType.getName() + "-RpcServerHandler-" + rpcName, RpcServerHandler.class, rpcServerBean, rpcName, rpcType, validator, rpcProperties, rpcServerHandlerInterceptor);
    }

    /**
     * 实例化 SimpleMessageListenerContainer
     */
    private void messageListenerContainer(
            String rpcName,
            RpcType rpcType,
            Queue queue,
            RpcServerHandler rpcServerHandler,
            int threadNum
    ) {
        SimpleMessageListenerContainer messageListenerContainer = registerBean(this.applicationContext, rpcType.getName() + "-MessageListenerContainer-" + rpcName, SimpleMessageListenerContainer.class, this.connectionFactory);
        messageListenerContainer.setQueueNames(queue.getName());
        messageListenerContainer.setMessageListener(rpcServerHandler);
        messageListenerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        messageListenerContainer.setConcurrentConsumers(threadNum);
    }

    /**
     * 实例化 Validator
     */
    private Validator getValidator() {
        if (this.validator == null) {
            ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class).configure().failFast(getRpcProperties().getValidatorFailFast().equals("true")).buildValidatorFactory();
            this.validator = validatorFactory.getValidator();
        }
        return this.validator;
    }

    /**
     * 实例化 RpcProperties
     */
    private RpcProperties getRpcProperties() {
        if (this.rpcProperties == null) {
            if (this.applicationContext.containsBean("rpcProperties")) {
                this.rpcProperties = this.applicationContext.getBean("rpcProperties", RpcProperties.class);
            } else {
                this.rpcProperties = registerBean(this.applicationContext, "rpcProperties", RpcProperties.class);
            }
        }
        return this.rpcProperties;
    }

    /**
     * 实例化 DirectExchange
     */
    private DirectExchange getDirectExchange(RpcType rpcType) {
        if (rpcType == RpcType.SYNC) {
            if (this.syncDirectExchange == null) {
                if (this.applicationContext.containsBean("syncDirectExchange")) {
                    this.syncDirectExchange = this.applicationContext.getBean("syncDirectExchange", DirectExchange.class);
                } else {
                    this.syncDirectExchange = registerBean(this.applicationContext, "syncDirectExchange", DirectExchange.class, "simple.rpc.sync", true, false);
                }
            }
            return this.syncDirectExchange;
        }
        if (this.asyncDirectExchange == null) {
            if (this.applicationContext.containsBean("asyncDirectExchange")) {
                this.asyncDirectExchange = this.applicationContext.getBean("asyncDirectExchange", DirectExchange.class);
            } else {
                this.asyncDirectExchange = registerBean(this.applicationContext, "asyncDirectExchange", DirectExchange.class, "simple.rpc.async", true, false);
            }
        }
        return this.asyncDirectExchange;
    }

    /**
     * 对象实例化并注册到Spring上下文
     */
    private <T> T registerBean(
            ConfigurableApplicationContext applicationContext,
            String name,
            Class<T> clazz,
            Object... args
    ) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (args.length > 0) {
            for (Object arg : args) {
                beanDefinitionBuilder.addConstructorArgValue(arg);
            }
        }
        BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        BeanDefinitionRegistry beanFactory = (BeanDefinitionRegistry) (applicationContext).getBeanFactory();
        if (beanFactory.isBeanNameInUse(name)) {
            throw new RuntimeException("BeanName: " + name + " 重复");
        }
        beanFactory.registerBeanDefinition(name, beanDefinition);
        return applicationContext.getBean(name, clazz);
    }

}
