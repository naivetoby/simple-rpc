package vip.toby.rpc.server;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.aot.hint.BindingReflectionHintsRegistrar;
import org.springframework.aot.hint.ExecutableMode;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationAotProcessor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.annotation.AnnotationUtils;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.properties.RpcProperties;

import java.lang.reflect.Method;

public class RpcServerRegistrationAotProcessor implements BeanRegistrationAotProcessor {

    @Override
    public BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean) {
        final Class<?> serverClass = registeredBean.getBeanClass();
        if (!serverClass.isAnnotationPresent(RpcServer.class)) {
            return null;
        }
        return (context, code) -> registerHints(context.getRuntimeHints(), serverClass);
    }

    private void registerHints(RuntimeHints hints, Class<?> serverClass) {
        final BindingReflectionHintsRegistrar binding = new BindingReflectionHintsRegistrar();
        for (Method method : serverClass.getMethods()) {
            if (AnnotationUtils.findAnnotation(method, RpcServerMethod.class) != null) {
                hints.reflection().registerMethod(method, ExecutableMode.INVOKE);
                binding.registerReflectionHints(hints.reflection(), method.getGenericParameterTypes());
            }
        }
        // 这些基础设施 Bean 由 RpcServerPostProcessor 根据业务注解在运行时创建
        for (Class<?> type : new Class<?>[]{Queue.class, Binding.class, DirectExchange.class, CustomExchange.class,
                SimpleMessageListenerContainer.class, RpcServerHandler.class, RpcProperties.class}) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
        }
    }
}
