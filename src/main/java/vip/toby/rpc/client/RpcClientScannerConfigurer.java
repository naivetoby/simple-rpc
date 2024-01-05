package vip.toby.rpc.client;

import lombok.Setter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;

/**
 * RpcClientScannerConfigurer
 *
 * @author toby
 */
public class RpcClientScannerConfigurer implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    @Setter
    private String basePackage;
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        final ClassPathRpcClientScanner scanner = new ClassPathRpcClientScanner(registry);
        scanner.setResourceLoader(this.applicationContext);
        scanner.registerFilters();
        scanner.scan(StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

}
