package vip.toby.rpc.server;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.HibernateValidator;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.properties.RpcProperties;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
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

    private final ConfigurableApplicationContext applicationContext;
    private final ConnectionFactory connectionFactory;
    private final RpcServerHandlerInterceptor rpcServerHandlerInterceptor;

    @Autowired
    public RpcServerPostProcessor(
            ConfigurableApplicationContext applicationContext,
            @Lazy ConnectionFactory connectionFactory,
            @Lazy RpcServerHandlerInterceptor rpcServerHandlerInterceptor
    ) {
        this.applicationContext = applicationContext;
        this.connectionFactory = connectionFactory;
        this.rpcServerHandlerInterceptor = rpcServerHandlerInterceptor;
    }

    private AbstractExchange syncDirectExchange;
    private AbstractExchange asyncDirectExchange;
    private AbstractExchange delayDirectExchange;
    private Validator validator;
    private RpcProperties rpcProperties;

    @Override
    public Object postProcessBeforeInitialization(
            @Nonnull Object bean,
            @Nonnull String beanName
    ) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(@Nonnull Object bean, @Nonnull String beanName) throws BeansException {
        final Class<?> rpcServerClass = AopProxyUtils.ultimateTargetClass(bean);
        for (Annotation annotation : rpcServerClass.getAnnotations()) {
            if (annotation instanceof RpcServer) {
                rpcServerStart(bean, (RpcServer) annotation);
            }
        }
        return bean;
    }

    /**
     * 启动服务监听
     */
    private void rpcServerStart(Object rpcServerBean, RpcServer rpcServer) {
        final String rpcValue = rpcServer.name();
        final int partitionNum = rpcServer.partitionNum();
        final boolean partitionMode = partitionNum > 1 && hasPartitionMethod(rpcServerBean);
        for (RpcType rpcType : rpcServer.type()) {
            final String rpcName = vip.toby.rpc.util.RpcUtil.getRpcName(rpcType, rpcValue);
            switch (rpcType) {
                case SYNC -> {
                    final Map<String, Object> params = new HashMap<>(1);
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    final RpcServerHandler syncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean, getValidator(), getRpcProperties(), rpcServer.xMessageTTL(), rpcServer.threadNum(), 0, rpcServer.queueCapacity(), rpcServerHandlerInterceptor);
                    final org.springframework.amqp.core.Queue syncQueue = queue(rpcName, rpcType, true, params);
                    binding(rpcName, rpcType, syncQueue);
                    messageListenerContainer(rpcName, syncQueue, syncServerHandler, rpcServer.threadNum());
                }
                case ASYNC, DELAY -> {
                    final RpcServerHandler asyncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean, getValidator(), getRpcProperties(), 0, rpcServer.threadNum(), partitionMode ? partitionNum : 0, rpcServer.queueCapacity(), rpcServerHandlerInterceptor);
                    final org.springframework.amqp.core.Queue asyncQueue = queue(rpcName, rpcType, false, null);
                    binding(rpcName, rpcType, asyncQueue);
                    messageListenerContainer(rpcName, asyncQueue, asyncServerHandler, partitionMode ? 1 : rpcServer.threadNum());
                }
                default -> {
                }
            }
        }
    }

    /**
     * 实例化 Queue
     */
    private org.springframework.amqp.core.Queue queue(String rpcName, RpcType rpcType, boolean autoDelete, Map<String, Object> params) {
        return registerBean(this.applicationContext, "Queue-" + rpcName, org.springframework.amqp.core.Queue.class, rpcName, rpcType == RpcType.ASYNC || rpcType == RpcType.DELAY, false, autoDelete, params);
    }

    /**
     * 实例化 Binding
     */
    private void binding(String rpcName, RpcType rpcType, org.springframework.amqp.core.Queue queue) {
        registerBean(this.applicationContext, "Binding-" + rpcName, Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getDirectExchange(rpcType).getName(), queue.getName(), Collections.<String, Object>emptyMap());
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
            int xMessageTTL,
            int threadNum,
            int partitionNum,
            int queueCapacity,
            RpcServerHandlerInterceptor rpcServerHandlerInterceptor
    ) {
        return registerBean(this.applicationContext, "RpcServerHandler-" + rpcName, RpcServerHandler.class, rpcServerBean, rpcName, rpcType, validator, rpcProperties, xMessageTTL, threadNum, partitionNum, queueCapacity, rpcServerHandlerInterceptor);
    }

    /**
     * 实例化 SimpleMessageListenerContainer
     */
    private void messageListenerContainer(
            String rpcName,
            org.springframework.amqp.core.Queue queue,
            RpcServerHandler rpcServerHandler,
            int threadNum
    ) {
        final SimpleMessageListenerContainer messageListenerContainer = registerBean(this.applicationContext, "MessageListenerContainer-" + rpcName, SimpleMessageListenerContainer.class, this.connectionFactory);
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
            final ValidatorFactory validatorFactory = Validation.byProvider(HibernateValidator.class)
                    .configure()
                    .failFast(getRpcProperties().getValidatorFailFast().equals("true"))
                    .buildValidatorFactory();
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
    private AbstractExchange getDirectExchange(RpcType rpcType) {
        // 同步
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
        // 异步
        if (rpcType == RpcType.ASYNC) {
            if (this.asyncDirectExchange == null) {
                if (this.applicationContext.containsBean("asyncDirectExchange")) {
                    this.asyncDirectExchange = this.applicationContext.getBean("asyncDirectExchange", DirectExchange.class);
                } else {
                    this.asyncDirectExchange = registerBean(this.applicationContext, "asyncDirectExchange", DirectExchange.class, "simple.rpc.async", true, false);
                }
            }
            return this.asyncDirectExchange;
        }
        // 延迟
        if (this.delayDirectExchange == null) {
            if (this.applicationContext.containsBean("delayDirectExchange")) {
                this.delayDirectExchange = this.applicationContext.getBean("delayDirectExchange", CustomExchange.class);
            } else {
                final Map<String, Object> args = new HashMap<>();
                args.put("x-delayed-type", "direct");
                this.delayDirectExchange = registerBean(this.applicationContext, "delayDirectExchange", CustomExchange.class, "simple.rpc.delay", "x-delayed-message", true, false, args);
            }
        }
        return this.delayDirectExchange;
    }

    private boolean hasPartitionMethod(Object rpcServerBean) {
        final Class<?> rpcServerClass = AopProxyUtils.ultimateTargetClass(rpcServerBean);
        for (Method method : rpcServerClass.getMethods()) {
            final RpcServerMethod rpcServerMethod = org.springframework.core.annotation.AnnotationUtils.findAnnotation(method, RpcServerMethod.class);
            if (rpcServerMethod != null && org.apache.commons.lang3.StringUtils.isNotBlank(rpcServerMethod.partitionKey())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 对象实例化并注册到 Spring 上下文
     */
    private <T> T registerBean(
            ConfigurableApplicationContext applicationContext,
            String name,
            Class<T> clazz,
            Object... args
    ) {
        final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        for (Object arg : args) {
            beanDefinitionBuilder.addConstructorArgValue(arg);
        }
        final BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        final BeanDefinitionRegistry beanFactory = (BeanDefinitionRegistry) (applicationContext).getBeanFactory();
        if (beanFactory.isBeanNameInUse(name)) {
            throw new RuntimeException("BeanName: " + name + " 重复");
        }
        beanFactory.registerBeanDefinition(name, beanDefinition);
        return applicationContext.getBean(name, clazz);
    }

}
