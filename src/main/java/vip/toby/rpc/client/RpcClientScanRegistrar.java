package vip.toby.rpc.client;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.StandardAnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.util.RpcUtil;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * RpcClientScanRegistrar
 *
 * @author toby
 */
@Component
public class RpcClientScanRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    @Autowired
    private ConfigurableApplicationContext applicationContext;
    @Autowired
    private ConnectionFactory connectionFactory;

    private Environment environment;
    private DirectExchange syncReplyDirectExchange;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
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
            provider.addIncludeFilter(new AnnotationTypeFilter(RpcClient.class));
            for (String basePackage : basePackages) {
                for (BeanDefinition beanDefinition : provider.findCandidateComponents(basePackage)) {
                    GenericBeanDefinition rpcClientProxy = new GenericBeanDefinition(beanDefinition);
                    Class<?> rpcClientInterface = null;
                    try {
                        rpcClientInterface = rpcClientProxy.resolveBeanClass(rpcClientProxy.getClass().getClassLoader());
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
        return RpcUtil.registerBean(this.applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyQueue", Queue.class, rpcName + ".reply." + rabbitClientId, true, false, false);
    }

    /**
     * 实例化 ReplyBinding
     */
    private Binding replyBinding(String rpcName, Queue queue) {
        return RpcUtil.registerBean(this.applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyBinding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getSyncReplyDirectExchange().getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 ReplyMessageListenerContainer
     */
    private SimpleMessageListenerContainer replyMessageListenerContainer(String rpcName, RabbitTemplate syncSender) {
        SimpleMessageListenerContainer replyMessageListenerContainer = RpcUtil.registerBean(this.applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_ReplyMessageListenerContainer", SimpleMessageListenerContainer.class, this.connectionFactory);
        replyMessageListenerContainer.setQueueNames(rpcName);
        replyMessageListenerContainer.setMessageListener(syncSender);
        return replyMessageListenerContainer;
    }

    /**
     * 实例化 AsyncSender
     */
    private RabbitTemplate asyncSender(String rpcName) {
        RabbitTemplate asyncSender = RpcUtil.registerBean(this.applicationContext, RpcType.SYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class);
        asyncSender.setConnectionFactory(this.connectionFactory);
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
        RabbitTemplate syncSender = RpcUtil.registerBean(this.applicationContext, RpcType.ASYNC.getValue() + "_" + rpcName + "_Sender", RabbitTemplate.class);
        syncSender.setConnectionFactory(this.connectionFactory);
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
            this.syncReplyDirectExchange = RpcUtil.registerBean(this.applicationContext, "syncReplyDirectExchange", DirectExchange.class, "simple.rpc.sync.reply", true, false);
        }
        return this.syncReplyDirectExchange;
    }

}
