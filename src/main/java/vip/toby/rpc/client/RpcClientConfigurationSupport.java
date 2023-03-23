package vip.toby.rpc.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import vip.toby.rpc.annotation.RpcClient;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class RpcClientConfigurationSupport implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private RpcClientConfigurer rpcClientConfigurer;
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        if (this.getRpcClientConfigurer() == null) {
            log.debug("No config implements RpcClientConfigurer. Please check your configuration.");
            return;
        }
        log.debug("Searching for rpc-client annotated with @RpcClient by RpcClientConfigurer.");
        // 添加注册
        final RpcClientRegistry rpcClientRegistry = new RpcClientRegistry();
        this.rpcClientConfigurer.addRpcClientRegistry(rpcClientRegistry);
        final List<Class<?>> rpcClientRegistrations = rpcClientRegistry.getRegistrations()
                .stream()
                .filter(rpcClientRegistration -> Arrays.stream(rpcClientRegistration.getAnnotations())
                        .anyMatch(annotation -> annotation instanceof RpcClient))
                .toList();
        if (rpcClientRegistrations.isEmpty()) {
            log.debug("No @RpcClient was found by RpcClientConfigurer. Please check your configuration.");
            return;
        }
        for (Class<?> clazz : rpcClientRegistrations) {
            processBeanDefinitions(clazz);
        }
    }

    private void processBeanDefinitions(Class<?> clazz) {
        final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        final GenericBeanDefinition rpcClientBeanDefinition = (GenericBeanDefinition) beanDefinitionBuilder.getRawBeanDefinition();
        final String beanClassName = rpcClientBeanDefinition.getBeanClassName();
        if (beanClassName != null) {
            final BeanDefinitionRegistry beanDefinitionRegistry = (BeanDefinitionRegistry) this.applicationContext;
            // FIXME 有可能已经注册成功, 如配置的 RPC-Client 已经在本项目中
            if (!beanDefinitionRegistry.containsBeanDefinition(beanClassName)) {
                // 获取真实接口 Class, 并作为构造方法的参数
                rpcClientBeanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
                // 修改类为 RpcClientProxyFactory
                rpcClientBeanDefinition.setBeanClass(RpcClientProxyFactory.class);
                // 采用按照类型注入的方式
                rpcClientBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
                beanDefinitionRegistry.registerBeanDefinition(beanClassName, rpcClientBeanDefinition);
                log.debug("@RpcClient was found at {}", beanClassName);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    /**
     * 实例化 RpcClientConfigurer
     */
    private RpcClientConfigurer getRpcClientConfigurer() {
        if (this.rpcClientConfigurer == null) {
            try {
                // FIXME 未实现 RpcClientConfigurer 接口
                this.rpcClientConfigurer = this.applicationContext.getBean(RpcClientConfigurer.class);
            } catch (BeansException ignored) {
            }
        }
        return this.rpcClientConfigurer;
    }

}
