package vip.toby.rpc.scan;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.client.RpcClientProxyFactory;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.server.RpcServerHandler;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * RpcScanDefinitionRegistrar
 *
 * @author toby
 */
public class RpcScanDefinitionRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware, BeanFactoryAware, BeanClassLoaderAware {

    private Environment environment;
    private BeanFactory beanFactory;
    private ClassLoader classLoader;
    private DirectExchange syncDirectExchange;
    private DirectExchange asyncDirectExchange;
    private DirectExchange syncReplyDirectExchange;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Map<String, Object> annotationAttributes = metadata.getAnnotationAttributes(EnableSimpleRpc.class.getCanonicalName());
        if (annotationAttributes != null) {
            String[] basePackages = (String[]) annotationAttributes.get("clientPath");
            if (basePackages.length == 0) {
                basePackages = new String[]{((StandardAnnotationMetadata) metadata).getIntrospectedClass().getPackage().getName()};
            }
            ClassPathScanningCandidateComponentProvider provider = new ClassPathScanningCandidateComponentProvider(false, this.environment) {
                @Override
                protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                    AnnotationMetadata metadata = beanDefinition.getMetadata();
                    return metadata.isIndependent() && metadata.isInterface();
                }
            };
            provider.addIncludeFilter(new AnnotationTypeFilter(RpcServer.class));
            provider.addIncludeFilter(new AnnotationTypeFilter(RpcClient.class));
            for (String basePackage : basePackages) {
                for (BeanDefinition beanDefinition : provider.findCandidateComponents(basePackage)) {
                    RpcServer rpcServer = beanDefinition.getClass().getAnnotation(RpcServer.class);
                    if (rpcServer != null) {
                        String rpcName = rpcServer.name();
                        for (RpcType rpcType : rpcServer.type()) {
                            switch (rpcType) {
                                case SYNC:
                                    Map<String, Object> params = new HashMap<>(1);
                                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                                    Queue syncQueue = queue(rpcName, rpcType, false, params);
                                    binding(rpcName, rpcType, syncQueue);
                                    RpcServerHandler syncServerHandler = rpcServerHandler(rpcName, rpcType, beanDefinition);
                                    messageListenerContainer(rpcName, rpcType, syncQueue, syncServerHandler, rpcServer.threadNum());
                                    break;
                                case ASYNC:
                                    Queue asyncQueue = queue(rpcName, rpcType, true, null);
                                    binding(rpcName, rpcType, asyncQueue);
                                    RpcServerHandler asyncServerHandler = rpcServerHandler(rpcName, rpcType, beanDefinition);
                                    messageListenerContainer(rpcName, rpcType, asyncQueue, asyncServerHandler, rpcServer.threadNum());
                                    break;
                                default:
                                    break;
                            }
                        }
                    } else {
                        GenericBeanDefinition rpcClientProxy = new GenericBeanDefinition(beanDefinition);
                        try {
                            Class<?> rpcClientInterface = rpcClientProxy.resolveBeanClass(this.classLoader);
                            RpcClient rpcClient = rpcClientProxy.getBeanClass().getAnnotation(RpcClient.class);
                            String rpcName = rpcClient.name();
                            RpcType rpcType = rpcClient.type();
                            // 获取真实接口class，并作为构造方法的参数
                            rpcClientProxy.getConstructorArgumentValues().addGenericArgumentValue(rpcClientInterface);
                            // 修改类为 RpcClientProxyFactory
                            rpcClientProxy.setBeanClass(RpcClientProxyFactory.class);
                            // 注入值
                            rpcClientProxy.getPropertyValues().add("rpcClientInterface", rpcClientInterface);
                            rpcClientProxy.getPropertyValues().add("rpcName", rpcName);
                            rpcClientProxy.getPropertyValues().add("rpcType", rpcType);
                            rpcClientProxy.getPropertyValues().add("sender", rpcClientSender(rpcClient));
                            // 采用按照类型注入的方式
                            rpcClientProxy.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
                            // 注入到spring
                            registry.registerBeanDefinition(beanDefinition.getBeanClassName(), rpcClientProxy);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    /**
     * 实例化 Queue
     */
    private Queue queue(String rpcName, RpcType rpcType, boolean durable, Map<String, Object> params) {
        return registerBean(this.beanFactory, rpcType.getValue() + "_" + rpcName + "_Queue", Queue.class, rpcType == RpcType.ASYNC ? (rpcName + ".async") : rpcName, durable, false, false, params);
    }

    /**
     * 实例化 Binding
     */
    private void binding(String rpcName, RpcType rpcType, Queue queue) {
        registerBean(this.beanFactory, rpcType.getValue() + "_" + rpcName + "_Binding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getDirectExchange(rpcType).getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 RpcServerHandler
     */
    private RpcServerHandler rpcServerHandler(String rpcName, RpcType rpcType, Object rpcServerBean) {
        return registerBean(this.beanFactory, rpcType.getValue() + "_" + rpcName + "_RpcServerHandler", RpcServerHandler.class, rpcServerBean, rpcName, rpcType);
    }

    /**
     * 实例化 SimpleMessageListenerContainer
     */
    private void messageListenerContainer(String rpcName, RpcType rpcType, Queue queue, RpcServerHandler rpcServerHandler, int threadNum) {
        SimpleMessageListenerContainer messageListenerContainer = registerBean(this.beanFactory, rpcType.getValue() + "_" + rpcName + "_MessageListenerContainer", SimpleMessageListenerContainer.class, null);
        messageListenerContainer.setQueueNames(queue.getName());
        messageListenerContainer.setMessageListener(rpcServerHandler);
        messageListenerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        messageListenerContainer.setConcurrentConsumers(threadNum);
    }

    /**
     * 实例化 DirectExchange
     */
    private DirectExchange getDirectExchange(RpcType rpcType) {
        if (rpcType == RpcType.SYNC) {
            if (this.syncDirectExchange == null) {
                this.syncDirectExchange = registerBean(this.beanFactory, "syncDirectExchange", DirectExchange.class, "simple.rpc.sync", true, false);
            }
            return this.syncDirectExchange;
        }
        if (this.asyncDirectExchange == null) {
            this.asyncDirectExchange = registerBean(this.beanFactory, "asyncDirectExchange", DirectExchange.class, "simple.rpc.async", true, false);
        }
        return this.asyncDirectExchange;
    }

    /**
     * 客户端
     */
    private RabbitTemplate rpcClientSender(RpcClient rpcClient) {
        String rpcName = rpcClient.name();
        if (rpcClient.type() == RpcType.SYNC) {
            Queue replyQueue = replyQueue(rpcName, UUID.randomUUID().toString());
            replyBinding(rpcName, replyQueue);
            RabbitTemplate syncSender = syncSender(rpcName, replyQueue, rpcClient.replyTimeout(), rpcClient.maxAttempts());
            replyMessageListenerContainer(rpcName, syncSender);
            return syncSender;
        }
        return asyncSender(rpcName);
    }

    /**
     * 实例化 replyQueue
     */
    private Queue replyQueue(String rpcName, String rabbitClientId) {
        return registerBean(this.beanFactory, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyQueue", Queue.class, rpcName + ".reply." + rabbitClientId, true, false, false);
    }

    /**
     * 实例化 ReplyBinding
     */
    private void replyBinding(String rpcName, Queue queue) {
        registerBean(this.beanFactory, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyBinding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getSyncReplyDirectExchange().getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 ReplyMessageListenerContainer
     */
    private void replyMessageListenerContainer(String rpcName, RabbitTemplate syncSender) {
        SimpleMessageListenerContainer replyMessageListenerContainer = registerBean(this.beanFactory, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyMessageListenerContainer", SimpleMessageListenerContainer.class, null);
        replyMessageListenerContainer.setQueueNames(rpcName);
        replyMessageListenerContainer.setMessageListener(syncSender);
    }

    /**
     * 实例化 AsyncSender
     */
    private RabbitTemplate asyncSender(String rpcName) {
        RabbitTemplate asyncSender = registerBean(this.beanFactory, RpcType.ASYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class, null);
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
        RabbitTemplate syncSender = registerBean(this.beanFactory, RpcType.SYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class, null);
        syncSender.setDefaultReceiveQueue(rpcName);
        syncSender.setRoutingKey(rpcName);
        syncSender.setReplyAddress(replyQueue.getName());
        syncSender.setReplyTimeout(replyTimeout);
        syncSender.setRetryTemplate(retryTemplate);
        return syncSender;
    }

    /**
     * 实例化 SyncReplyDirectExchange
     */
    private DirectExchange getSyncReplyDirectExchange() {
        if (this.syncReplyDirectExchange == null) {
            this.syncReplyDirectExchange = registerBean(this.beanFactory, "syncReplyDirectExchange", DirectExchange.class, "simple.rpc.sync.reply", true, false);
        }
        return this.syncReplyDirectExchange;
    }

    /**
     * 对象实例化并注册到Spring上下文
     */
    private <T> T registerBean(BeanFactory beanFactory, String name, Class<T> clazz, Object... args) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (args.length > 0) {
            for (Object arg : args) {
                beanDefinitionBuilder.addConstructorArgValue(arg);
            }
        }
        BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) beanFactory;
        if (beanDefinitionRegistry.isBeanNameInUse(name)) {
            throw new RuntimeException("Bean: " + name + " 实例化时发生重复");
        }
        beanDefinitionRegistry.registerBeanDefinition(name, beanDefinition);
        return beanFactory.getBean(name, clazz);
    }
}
