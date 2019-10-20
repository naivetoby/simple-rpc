package vip.toby.rpc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.entity.RpcType;

import java.util.*;

/**
 * ClassPathRpcServerScanner
 *
 * @author toby
 */
public class ClassPathRpcServerScanner extends ClassPathBeanDefinitionScanner implements EnvironmentAware, BeanFactoryAware, BeanClassLoaderAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClassPathRpcServerScanner.class);

    private BeanFactory beanFactory;
    private ClassLoader classLoader;

    private DirectExchange syncDirectExchange;
    private DirectExchange asyncDirectExchange;
    private ConnectionFactory connectionFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void setBeanClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    ClassPathRpcServerScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
    }

    void registerFilters() {
        addIncludeFilter(new AnnotationTypeFilter(RpcServer.class));
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return beanDefinition.getMetadata().isConcrete() && beanDefinition.getMetadata().isIndependent();
    }

    @Override
    public Set<BeanDefinitionHolder> doScan(String... basePackages) {
        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
        if (beanDefinitions.isEmpty()) {
            LOGGER.warn("No RpcServer was found in '" + Arrays.toString(basePackages) + "' package. Please check your configuration.");
        } else {
            processBeanDefinitions(beanDefinitions);
        }
        return beanDefinitions;
    }

    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
        GenericBeanDefinition rpcServerBeanDefinition;
        for (BeanDefinitionHolder holder : beanDefinitions) {
            rpcServerBeanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            try {
                Class<?> rpcServerClass = rpcServerBeanDefinition.resolveBeanClass(this.classLoader);
                if (rpcServerClass != null) {
                    Object rpcServerBean = registerBean(rpcServerBeanDefinition.getBeanClassName(), rpcServerClass);
                    RpcServer rpcServer = rpcServerClass.getAnnotation(RpcServer.class);
                    rpcServerStart(rpcServerBean, rpcServer);
                }
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 启动服务监听
     */
    private void rpcServerStart(Object rpcServerBean, RpcServer rpcServer) {
        String rpcName = rpcServer.name();
        for (RpcType rpcType : rpcServer.type()) {
            switch (rpcType) {
                case SYNC:
                    Map<String, Object> params = new HashMap<>(1);
                    params.put("x-message-ttl", rpcServer.xMessageTTL());
                    Queue syncQueue = queue(rpcName, rpcType, false, params);
                    binding(rpcName, rpcType, syncQueue);
                    RpcServerHandler syncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean);
                    messageListenerContainer(rpcName, rpcType, syncQueue, syncServerHandler, rpcServer.threadNum());
                    break;
                case ASYNC:
                    Queue asyncQueue = queue(rpcName, rpcType, true, null);
                    binding(rpcName, rpcType, asyncQueue);
                    RpcServerHandler asyncServerHandler = rpcServerHandler(rpcName, rpcType, rpcServerBean);
                    messageListenerContainer(rpcName, rpcType, asyncQueue, asyncServerHandler, rpcServer.threadNum());
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * 实例化 Queue
     */
    private Queue queue(String rpcName, RpcType rpcType, boolean durable, Map<String, Object> params) {
        return registerBean(rpcType.getValue() + "_" + rpcName + "_Queue", Queue.class, rpcType == RpcType.ASYNC ? (rpcName + ".async") : rpcName, durable, false, false, params);
    }

    /**
     * 实例化 Binding
     */
    private Binding binding(String rpcName, RpcType rpcType, Queue queue) {
        return registerBean(rpcType.getValue() + "_" + rpcName + "_Binding", Binding.class, queue.getName(), Binding.DestinationType.QUEUE, getDirectExchange(rpcType).getName(), queue.getName(), Collections.<String, Object>emptyMap());
    }

    /**
     * 实例化 RpcServerHandler
     */
    private RpcServerHandler rpcServerHandler(String rpcName, RpcType rpcType, Object rpcServerBean) {
        return registerBean(rpcType.getValue() + "_" + rpcName + "_RpcServerHandler", RpcServerHandler.class, rpcServerBean, rpcName, rpcType);
    }

    /**
     * 实例化 SimpleMessageListenerContainer
     */
    private SimpleMessageListenerContainer messageListenerContainer(String rpcName, RpcType rpcType, Queue queue, RpcServerHandler rpcServerHandler, int threadNum) {
        SimpleMessageListenerContainer messageListenerContainer = registerBean(rpcType.getValue() + "_" + rpcName + "_MessageListenerContainer", SimpleMessageListenerContainer.class, getConnectionFactory());
        messageListenerContainer.setQueueNames(queue.getName());
        messageListenerContainer.setMessageListener(rpcServerHandler);
        messageListenerContainer.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        messageListenerContainer.setConcurrentConsumers(threadNum);
        return messageListenerContainer;
    }

    private DirectExchange getDirectExchange(RpcType rpcType) {
        if (rpcType == RpcType.SYNC) {
            if (this.syncDirectExchange == null) {
                this.syncDirectExchange = registerBean("syncDirectExchange", DirectExchange.class, "simple.rpc.sync", true, false);
            }
            return this.syncDirectExchange;
        }
        if (this.asyncDirectExchange == null) {
            this.asyncDirectExchange = registerBean("asyncDirectExchange", DirectExchange.class, "simple.rpc.async", true, false);
        }
        return this.asyncDirectExchange;
    }

    private ConnectionFactory getConnectionFactory() {
        if (this.connectionFactory == null) {
            this.connectionFactory = this.beanFactory.getBean(ConnectionFactory.class);
        }
        return this.connectionFactory;
    }

    /**
     * 对象实例化并注册到Spring上下文
     */
    private <T> T registerBean(String name, Class<T> clazz, Object... args) {
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        if (args != null && args.length > 0) {
            for (Object arg : args) {
                beanDefinitionBuilder.addConstructorArgValue(arg);
            }
        }
        BeanDefinition beanDefinition = beanDefinitionBuilder.getRawBeanDefinition();
        BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) this.beanFactory;
        if (beanDefinitionRegistry.isBeanNameInUse(name)) {
            throw new RuntimeException("Bean: " + name + " 实例化时发生重复");
        }
        beanDefinitionRegistry.registerBeanDefinition(name, beanDefinition);
        return this.beanFactory.getBean(name, clazz);
    }

}