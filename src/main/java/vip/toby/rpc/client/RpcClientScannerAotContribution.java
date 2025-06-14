package vip.toby.rpc.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RegisteredBean;

import javax.annotation.Nonnull;

/**
 * RPC客户端扫描器AOT贡献
 * 在AOT编译时扫描@RpcClient接口并预注册
 */
@Slf4j
public class RpcClientScannerAotContribution implements BeanRegistrationAotContribution {

    private final RegisteredBean registeredBean;

    public RpcClientScannerAotContribution(RegisteredBean registeredBean) {
        this.registeredBean = registeredBean;
    }

    @Override
    public void applyTo(
            @Nonnull GenerationContext generationContext,
            @Nonnull BeanRegistrationCode beanRegistrationCode
    ) {
        log.debug("AOT: Starting @RpcClient interface scanning...");

        try {
            // 🎯 简化方案：直接从BeanFactory获取已注册的Bean定义
            if (registeredBean.getBeanFactory() instanceof BeanDefinitionRegistry registry) {

                String[] beanNames = registry.getBeanDefinitionNames();
                int processedCount = 0;

                for (String beanName : beanNames) {
                    try {
                        BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
                        String className = beanDefinition.getBeanClassName();

                        // 检查是否是RpcClientProxyFactory（说明是@RpcClient接口）
                        if (RpcClientProxyFactory.class.getName().equals(className)) {

                            // 获取构造参数中的接口类名
                            ConstructorArgumentValues constructorArgs = beanDefinition.getConstructorArgumentValues();
                            ConstructorArgumentValues.ValueHolder valueHolder = constructorArgs.getGenericArgumentValue(String.class);

                            if (valueHolder != null) {
                                Object value = valueHolder.getValue(); // 🔧 修复：获取实际值

                                if (value instanceof String interfaceName) { // 🔧 修复：确保是字符串
                                    log.debug("AOT: Found @RpcClient interface from bean definition: {}", interfaceName);


                                    try {
                                        Class<?> interfaceClass = Class.forName(interfaceName);

                                        // 注册JDK代理
                                        generationContext.getRuntimeHints().proxies().registerJdkProxy(interfaceClass);

                                        // 注册反射提示
                                        generationContext.getRuntimeHints()
                                                .reflection()
                                                .registerType(interfaceClass, MemberCategory.INVOKE_DECLARED_METHODS);

                                        log.debug("AOT: Registered hints for @RpcClient interface: {}", interfaceName);
                                        processedCount++;

                                    } catch (ClassNotFoundException e) {
                                        log.warn("AOT: Could not load @RpcClient interface: {}", interfaceName);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.trace("AOT: Skipping bean {}: {}", beanName, e.getMessage());
                    }
                }

                log.info("AOT: Completed @RpcClient interface scanning. Processed {} interfaces.", processedCount);
            }

        } catch (Exception e) {
            log.error("AOT: Failed to scan @RpcClient interfaces", e);
        }
    }
}
