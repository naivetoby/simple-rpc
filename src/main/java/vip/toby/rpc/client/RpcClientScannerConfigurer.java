package vip.toby.rpc.client;

import lombok.Setter;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.StringUtils;

import javax.annotation.Nonnull;

/**
 * RpcClientScannerConfigurer
 *
 * @author toby
 */
@Setter
public class RpcClientScannerConfigurer implements BeanDefinitionRegistryPostProcessor {

    private String basePackage;

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        final ClassPathRpcClientScanner scanner = new ClassPathRpcClientScanner(registry);
        scanner.registerFilters();
        scanner.scan(StringUtils.tokenizeToStringArray(this.basePackage, ConfigurableApplicationContext.CONFIG_LOCATION_DELIMITERS));
    }

}
