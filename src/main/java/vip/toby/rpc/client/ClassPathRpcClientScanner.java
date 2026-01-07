package vip.toby.rpc.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultBeanNameGenerator;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import vip.toby.rpc.annotation.RpcClient;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Set;

/**
 * ClassPathRpcClientScanner
 *
 * @author toby
 */
@Slf4j
public class ClassPathRpcClientScanner extends ClassPathBeanDefinitionScanner {

    ClassPathRpcClientScanner(BeanDefinitionRegistry registry) {
        super(registry, false);
        setBeanNameGenerator(new DefaultBeanNameGenerator());
    }

    void registerFilters() {
        addIncludeFilter(new AnnotationTypeFilter(RpcClient.class));
    }

    @Override
    protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
        return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
    }

    @Override
    protected boolean checkCandidate(@Nonnull String beanName, @Nonnull BeanDefinition beanDefinition) {
        if (getRegistry().containsBeanDefinition(beanName)) {
            final BeanDefinition existingDef = getRegistry().getBeanDefinition(beanName);
            // 如果现有的 Bean 已经是 RpcClientProxyFactory 类型，说明是重复扫描，直接跳过
            if (RpcClientProxyFactory.class.getName().equals(existingDef.getBeanClassName())) {
                return false;
            }
            // 如果是普通的 @Component 扫描产生的定义，或者是其他冲突，则移除它，让位给 RPC 代理
            log.debug("Existing bean definition '{}' found. Replacing with RPC proxy.", beanName);
            getRegistry().removeBeanDefinition(beanName);
        }
        return true;
    }

    @Override
    @Nonnull
    public Set<BeanDefinitionHolder> doScan(@Nonnull String... basePackages) {
        final Set<BeanDefinitionHolder> beanDefinitions = super.doScan(basePackages);
        if (beanDefinitions.isEmpty()) {
            log.debug("No @RpcClient was found in '{}' package. Please check your configuration.", Arrays.toString(basePackages));
        } else {
            processBeanDefinitions(beanDefinitions);
        }
        return beanDefinitions;
    }

    private void processBeanDefinitions(Set<BeanDefinitionHolder> beanDefinitions) {
        GenericBeanDefinition rpcClientBeanDefinition;
        for (BeanDefinitionHolder holder : beanDefinitions) {
            rpcClientBeanDefinition = (GenericBeanDefinition) holder.getBeanDefinition();
            final String beanClassName = rpcClientBeanDefinition.getBeanClassName();
            if (beanClassName != null) {
                // 获取真实接口 class，并作为构造方法的参数
                rpcClientBeanDefinition.getConstructorArgumentValues().addGenericArgumentValue(beanClassName);
                // 修改类为 RpcClientProxyFactory
                rpcClientBeanDefinition.setBeanClass(RpcClientProxyFactory.class);
                // 采用按照类型注入的方式
                rpcClientBeanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
                log.debug("@RpcClient was found at {}", beanClassName);
            }
        }
    }

}
