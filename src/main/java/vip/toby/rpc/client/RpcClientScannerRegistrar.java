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

import javax.annotation.Nonnull;
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
    public void setBeanFactory(@Nonnull BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void registerBeanDefinitions(
            @Nonnull AnnotationMetadata importingClassMetadata,
            @Nonnull BeanDefinitionRegistry registry
    ) {

        if (!AutoConfigurationPackages.has(this.beanFactory)) {
            log.debug("Could not determine auto-configuration package, automatic rpc-client scanning disabled.");
            return;
        }

        log.debug("Searching for rpc-client annotated with @RpcClient");

        final List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
        if (log.isDebugEnabled()) {
            packages.forEach(pkg -> log.debug("Using auto-configuration base package '{}'", pkg));
        }
        final BeanDefinitionBuilder scannerConfigurerBuilder = BeanDefinitionBuilder.genericBeanDefinition(RpcClientScannerConfigurer.class);
        scannerConfigurerBuilder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(packages));
        registry.registerBeanDefinition(RpcClientScannerConfigurer.class.getName(), scannerConfigurerBuilder.getBeanDefinition());
    }

}
