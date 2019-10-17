//package vip.toby.rpc.client;
//
//import org.springframework.amqp.rabbit.connection.ConnectionFactory;
//import org.springframework.beans.BeansException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
//import org.springframework.beans.factory.support.BeanDefinitionRegistry;
//import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
//import org.springframework.context.ConfigurableApplicationContext;
//import org.springframework.context.annotation.Lazy;
//import org.springframework.stereotype.Component;
//import org.springframework.util.StringUtils;
//
///**
// * RpcClientScannerConfigurer
// *
// * @author toby
// */
//@Component
//public class RpcClientScannerConfigurer implements BeanDefinitionRegistryPostProcessor {
//
//    @Autowired
//    private ConfigurableApplicationContext applicationContext;
//    @Autowired
//    @Lazy
//    private ConnectionFactory connectionFactory;
//
//    @Override
//    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
//
//    }
//
//    @Override
//    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
//        // 创建扫描器
//        ClassPathRpcClientScanner scanner = new ClassPathRpcClientScanner(registry);
//        scanner.setConnectionFactory(this.connectionFactory);
//        scanner.setResourceLoader(this.applicationContext);
//        scanner.setApplicationContext(this.applicationContext);
//        // 注册Filter
//        scanner.registerFilters();
//        // 扫描
//        scanner.scan(StringUtils.tokenizeToStringArray(null, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
//    }
//
//}
