package vip.toby.rpc.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * RpcClientScannerRegistrar
 *
 * @author toby
 */
@Slf4j
public class RpcClientScannerRegistrar implements BeanFactoryAware, ImportBeanDefinitionRegistrar {

    private BeanFactory beanFactory;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        if (!AutoConfigurationPackages.has(this.beanFactory)) {
            log.debug("Could not determine auto-configuration package, automatic rpc-client scanning disabled.");
            return;
        }

        log.debug("Searching for rpc-client annotated with @RpcClient");

        List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
        if (log.isDebugEnabled()) {
            packages.forEach(pkg -> log.debug("Using auto-configuration base package '{}'", pkg));
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RpcClientScannerConfigurer.class);
        builder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(packages));
        registry.registerBeanDefinition(RpcClientScannerConfigurer.class.getName(), builder.getBeanDefinition());
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

}
