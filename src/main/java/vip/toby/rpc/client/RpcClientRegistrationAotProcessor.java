package vip.toby.rpc.client;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.properties.RpcProperties;

import java.lang.reflect.Method;

public class RpcClientRegistrationAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        if (registeredBean.getBeanClass() != RpcClientProxyFactory.class) {
            return null;
        }
        final Object type = registeredBean.getMergedBeanDefinition().getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
        if (!(type instanceof Class<?> clientInterface)) {
            return null;
        }
        return (context, code) -> registerHints(context.getRuntimeHints(), clientInterface);
    }

    private void registerHints(RuntimeHints hints, Class<?> clientInterface) {
        hints.proxies().registerJdkProxy(clientInterface);
        final BindingReflectionHintsRegistrar binding = new BindingReflectionHintsRegistrar();
        for (Method method : clientInterface.getMethods()) {
            if (method.isAnnotationPresent(RpcClientMethod.class)) {
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
                binding.registerReflectionHints(hints.reflection(), method.getGenericParameterTypes());
            }
        }
        // 客户端在运行时通过 BeanDefinition 创建发送器和默认配置
        for (Class<?> type : new Class<?>[]{RabbitTemplate.class, RpcQuietRabbitTemplate.class, RpcProperties.class}) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
