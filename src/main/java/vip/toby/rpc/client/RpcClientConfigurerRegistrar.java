package vip.toby.rpc.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;

import javax.annotation.Nonnull;

/**
 * RpcClientConfigurerRegistrar
 *
 * @author toby
 */
@Slf4j
public class RpcClientConfigurerRegistrar implements ImportBeanDefinitionRegistrar {

    @Override
    public void registerBeanDefinitions(
            @Nonnull AnnotationMetadata importingClassMetadata,
            @Nonnull BeanDefinitionRegistry registry
    ) {
        final BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(RpcClientConfigurationSupport.class);
        registry.registerBeanDefinition(RpcClientConfigurationSupport.class.getName(), builder.getBeanDefinition());
    }

}
