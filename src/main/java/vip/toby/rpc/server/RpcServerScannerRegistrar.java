package vip.toby.rpc.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * RpcServerScannerRegistrar
 *
 * @author toby
 */
public class RpcServerScannerRegistrar implements BeanFactoryAware, ImportBeanDefinitionRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServerScannerRegistrar.class);

    private BeanFactory beanFactory;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

        if (!AutoConfigurationPackages.has(this.beanFactory)) {
            LOGGER.debug("Could not determine auto-configuration package, automatic rpc-server scanning disabled.");
            return;
        }

        LOGGER.debug("Searching for rpc-server annotated with @RpcServer");

        List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
        if (LOGGER.isDebugEnabled()) {
            packages.forEach(pkg -> LOGGER.debug("Using auto-configuration base package '{}'", pkg));
        }

        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RpcServerScannerConfigurer.class);
        builder.addPropertyValue("basePackage", StringUtils.collectionToCommaDelimitedString(packages));
        registry.registerBeanDefinition(RpcServerScannerConfigurer.class.getName(), builder.getBeanDefinition());
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

}