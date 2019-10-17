//package vip.toby.rpc.client;
//
//import org.springframework.amqp.core.Binding;
//import org.springframework.amqp.core.DirectExchange;
//import org.springframework.amqp.core.Queue;
//import org.springframework.amqp.rabbit.connection.ConnectionFactory;
//import org.springframework.amqp.rabbit.core.RabbitTemplate;
//import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
//import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
//import org.springframework.beans.factory.config.BeanDefinitionHolder;
//import org.springframework.beans.factory.support.AbstractBeanDefinition;
//import org.springframework.beans.factory.support.BeanDefinitionRegistry;
//import org.springframework.beans.factory.support.GenericBeanDefinition;
//import org.springframework.context.ConfigurableApplicationContext;
//import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
//import org.springframework.core.type.filter.AnnotationTypeFilter;
//import org.springframework.retry.policy.SimpleRetryPolicy;
//import org.springframework.retry.support.RetryTemplate;
//import vip.toby.rpc.annotation.RpcClient;
//import vip.toby.rpc.entity.RpcType;
//import vip.toby.rpc.util.RpcUtil;
//
//import java.lang.annotation.Annotation;
//import java.util.Collections;
//import java.util.Set;
//import java.util.UUID;
//
///**
// * ClassPathRpcClientScanner
// *
// * @author toby
// */
//public class ClassPathRpcClientScanner extends ClassPathBeanDefinitionScanner {
//
//
//    private ConfigurableApplicationContext applicationContext;
//    private ConnectionFactory connectionFactory;
//    private DirectExchange syncReplyDirectExchange;
//
//    public void setApplicationContext(ConfigurableApplicationContext applicationContext) {
//        this.applicationContext = applicationContext;
//    }
//
//    public void setConnectionFactory(ConnectionFactory connectionFactory) {
//        this.connectionFactory = connectionFactory;
//    }
//
//    public ClassPathRpcClientScanner(BeanDefinitionRegistry registry) {
//        super(registry);
//    }
//
//    public void registerFilters() {
//        // 扫描使用 RpcClient 注解的接口
//        addIncludeFilter(new AnnotationTypeFilter(RpcClient.class));
//    }
//
//    @Override
//    protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
//        // 调用父类的doScan方法
//        Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
//        if (!beanDefinitions.isEmpty()) {
//            // 处理 beanDefinitions
//            processBeanDefinitions(beanDefinitions);
//        }
//        return beanDefinitions;
//    }
//
//    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
//        GenericBeanDefinition definition;
//        // 遍历 beanDefinitions
//        for (BeanDefinitionHolder holder : beanDefinitions) {
//            definition = (GenericBeanDefinition) holder.getBeanDefinition();
//            Class<?> rpcClientInterface = definition.getBeanClass();
//            String rpcName = definition.getBeanClassName();
//            // 获取注解
//            if (rpcClientInterface.getAnnotations() != null && rpcClientInterface.getAnnotations().length > 0) {
//                for (Annotation annotation : rpcClientInterface.getAnnotations()) {
//                    if (annotation instanceof RpcClient) {
//                        RpcClient rpcClient = (RpcClient) annotation;
//                        RpcType rpcType = rpcClient.type();
//                        // 获取真实接口class，并作为构造方法的参数
//                        definition.getConstructorArgumentValues().addGenericArgumentValue(rpcClientInterface);
//                        // 修改类为 RpcClientProxyFactory
//                        definition.setBeanClass(RpcClientProxyFactory.class);
//                        // 注入值
//                        definition.getPropertyValues().add("rpcClientInterface", rpcClientInterface);
//                        definition.getPropertyValues().add("rpcName", rpcName);
//                        definition.getPropertyValues().add("rpcType", rpcType);
//                        definition.getPropertyValues().add("sender", rpcClientSender(rpcClient));
//                        // 采用按照类型注入的方式
//                        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
//                    }
//                }
//            }
//        }
//    }
//
//    @Override
//    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
//        return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
//    }
//
//
//}
