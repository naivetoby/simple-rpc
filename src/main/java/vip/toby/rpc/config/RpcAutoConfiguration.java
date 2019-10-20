package vip.toby.rpc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.StringUtils;
import vip.toby.rpc.client.RpcClientProxyFactory;
import vip.toby.rpc.client.RpcClientScannerConfigurer;
import vip.toby.rpc.server.RpcServerPostProcessor;

import java.util.List;

/**
 * RabbitAutoConfiguration
 *
 * @author toby
 */
@Configuration
public class RpcAutoConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcAutoConfiguration.class);


    public static class AutoConfiguredRpcClientScannerRegistrar implements BeanFactoryAware, ImportBeanDefinitionRegistrar {

        private BeanFactory beanFactory;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {

            if (!AutoConfigurationPackages.has(this.beanFactory)) {
                LOGGER.debug("Could not determine auto-configuration package, automatic rpc-client scanning disabled.");
                return;
            }

            LOGGER.debug("Searching for rpc-client annotated with @RpcClient");

            List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
            if (LOGGER.isDebugEnabled()) {
                packages.forEach(pkg -> LOGGER.debug("Using auto-configuration base package '{}'", pkg));
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

    @Configuration
    @Import(AutoConfiguredRpcClientScannerRegistrar.class)
    @ConditionalOnMissingBean({RpcClientProxyFactory.class, RpcClientScannerConfigurer.class})
    public static class RpcClientScannerRegistrarNotFoundConfiguration implements InitializingBean {

        @Override
        public void afterPropertiesSet() {
            LOGGER.debug("Not found configuration for registering rpcClient bean using @RpcClientScan, RpcClientProxyFactory and RpcClientScannerConfigurer.");
        }

    }

    @Configuration
    @Import(RpcServerPostProcessor.class)
    @ConditionalOnMissingBean({RpcServerPostProcessor.class})
    public static class RpcServerScannerRegistrarNotFoundConfiguration {

    }

}
