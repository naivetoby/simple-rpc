package vip.toby.rpc.client;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import vip.toby.rpc.annotation.RpcClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.util.List;

public class RpcClientConfigurationSupport implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    @Nullable
    private RpcClientConfigurer rpcClientConfigurer;
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(@Nonnull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(@Nonnull BeanDefinitionRegistry registry) throws BeansException {
        if (this.rpcClientConfigurer != null) {
            // 添加注册
            final RpcClientRegistry rpcClientRegistry = new RpcClientRegistry();
            this.rpcClientConfigurer.addRpcClientRegistry(rpcClientRegistry);
            final List<Class<?>> rpcClientRegistrations = rpcClientRegistry.getRegistrations();
            for (Class<?> clazz : rpcClientRegistrations) {
                for (Annotation annotation : clazz.getAnnotations()) {
                    if (annotation instanceof RpcClient) {
                        processBeanDefinitions(clazz);
                        break;
                    }
                }
            }
        }
    }

    private void processBeanDefinitions(Class<?> clazz) {
        final BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(clazz);
        final GenericBeanDefinition rpcClientBeanDefinition = (GenericBeanDefinition) beanDefinitionBuilder.getRawBeanDefinition();
        final String beanClassName = rpcClientBeanDefinition.getBeanClassName();
        if (beanClassName != null) {
            if (!applicationContext.containsBeanDefinition(beanClassName)) {
                // 获取真实接口 Class, 并作为构造方法的参数
                rpcClientBeanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
                // 修改类为 RpcClientProxyFactory
                rpcClientBeanDefinition.setBeanClass(RpcClientProxyFactory.class);
                // 采用按照类型注入的方式
                rpcClientBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
            }
        }
    }

    @Override
    public void postProcessBeanFactory(@Nonnull ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

}
