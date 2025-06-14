package vip.toby.rpc.server;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import vip.toby.rpc.annotation.RpcServer;
import vip.toby.rpc.annotation.RpcServerMethod;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * RPC服务端Bean注册AOT贡献
 * 为@RpcServer标注的类注册必要的反射提示
 *
 * @author toby
 */
@Slf4j
public class RpcServerBeanRegistrationContribution implements BeanRegistrationAotContribution {

    private final RegisteredBean registeredBean;

    public RpcServerBeanRegistrationContribution(RegisteredBean registeredBean) {
        this.registeredBean = registeredBean;
    }

    @Override
    public void applyTo(
            @Nonnull GenerationContext generationContext,
            @Nonnull BeanRegistrationCode beanRegistrationCode
    ) {
        Class<?> beanClass = registeredBean.getBeanClass();

        // 1. 注册 @RpcServer 类的完整反射访问
        generationContext.getRuntimeHints()
                .reflection()
                .registerType(beanClass, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS);

        // 2. 注册 @RpcServer 注解本身
        generationContext.getRuntimeHints()
                .reflection()
                .registerType(RpcServer.class, MemberCategory.INVOKE_DECLARED_METHODS);

        // 3. 注册实现的接口
        registerInterfaces(generationContext, beanClass);

        // 4. 注册 @RpcServerMethod 相关类型
        registerRpcServerMethodTypes(generationContext, beanClass);

        // 5. 注册父类（如果存在）
        registerSuperClass(generationContext, beanClass);

        log.debug("Registered AOT hints for @RpcServer class: {}", beanClass.getName());
    }

    /**
     * 注册实现的接口
     */
    private void registerInterfaces(GenerationContext generationContext, Class<?> beanClass) {
        Class<?>[] interfaces = beanClass.getInterfaces();
        for (Class<?> interfaceClass : interfaces) {
            generationContext.getRuntimeHints()
                    .reflection()
                    .registerType(interfaceClass, MemberCategory.INVOKE_DECLARED_METHODS);

            // 递归注册接口的父接口
            registerInterfaces(generationContext, interfaceClass);
        }
    }

    /**
     * 注册父类
     */
    private void registerSuperClass(GenerationContext generationContext, Class<?> beanClass) {
        Class<?> superClass = beanClass.getSuperclass();
        if (superClass != null && !superClass.equals(Object.class)) {
            if (shouldRegisterType(superClass)) {
                generationContext.getRuntimeHints()
                        .reflection()
                        .registerType(superClass, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS);

                // 递归注册父类的父类
                registerSuperClass(generationContext, superClass);
            }
        }
    }

    /**
     * 注册RPC服务端方法相关的类型反射提示
     */
    private void registerRpcServerMethodTypes(GenerationContext generationContext, Class<?> beanClass) {
        try {
            Method[] methods = beanClass.getDeclaredMethods();
            for (Method method : methods) {
                // 检查是否有 @RpcServerMethod 注解
                if (method.isAnnotationPresent(RpcServerMethod.class)) {
                    // 注册 @RpcServerMethod 注解
                    generationContext.getRuntimeHints()
                            .reflection()
                            .registerType(RpcServerMethod.class, MemberCategory.INVOKE_DECLARED_METHODS);

                    // 注册方法参数类型
                    registerMethodParameterTypes(generationContext, method);

                    // 注册方法返回类型
                    registerMethodReturnType(generationContext, method);

                    // 注册方法异常类型
                    registerMethodExceptionTypes(generationContext, method);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to register RpcServerMethod types for class: {}", beanClass.getName(), e);
        }
    }

    /**
     * 注册方法参数类型
     */
    private void registerMethodParameterTypes(GenerationContext generationContext, Method method) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Type[] genericParamTypes = method.getGenericParameterTypes();

        for (int i = 0; i < paramTypes.length; i++) {
            Class<?> paramType = paramTypes[i];
            Type genericType = genericParamTypes[i];

            // 注册参数类型
            if (shouldRegisterType(paramType)) {
                registerTypeWithHints(generationContext, paramType);
            }

            // 注册泛型参数类型
            registerGenericType(generationContext, genericType);
        }
    }

    /**
     * 注册方法返回类型
     */
    private void registerMethodReturnType(GenerationContext generationContext, Method method) {
        Class<?> returnType = method.getReturnType();
        Type genericReturnType = method.getGenericReturnType();

        if (shouldRegisterType(returnType) && !returnType.equals(Void.TYPE)) {
            registerTypeWithHints(generationContext, returnType);
        }

        // 注册泛型返回类型
        registerGenericType(generationContext, genericReturnType);
    }

    /**
     * 注册方法异常类型
     */
    private void registerMethodExceptionTypes(GenerationContext generationContext, Method method) {
        Class<?>[] exceptionTypes = method.getExceptionTypes();
        for (Class<?> exceptionType : exceptionTypes) {
            if (shouldRegisterType(exceptionType)) {
                registerTypeWithHints(generationContext, exceptionType);
            }
        }
    }

    /**
     * 注册泛型类型
     */
    private void registerGenericType(GenerationContext generationContext, Type type) {
        if (type instanceof ParameterizedType parameterizedType) {
            // 注册泛型的原始类型
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> rawClass && shouldRegisterType(rawClass)) {
                registerTypeWithHints(generationContext, rawClass);
            }

            // 注册泛型参数
            Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
            for (Type actualType : actualTypeArguments) {
                if (actualType instanceof Class<?> actualClass && shouldRegisterType(actualClass)) {
                    registerTypeWithHints(generationContext, actualClass);
                } else {
                    // 递归处理嵌套泛型
                    registerGenericType(generationContext, actualType);
                }
            }
        }
    }

    /**
     * 注册类型及其相关提示
     */
    private void registerTypeWithHints(GenerationContext generationContext, Class<?> type) {
        generationContext.getRuntimeHints()
                .reflection()
                .registerType(type, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS);

        // 如果是枚举类型，注册所有枚举值
        if (type.isEnum()) {
            try {
                Object[] enumConstants = type.getEnumConstants();
                for (Object enumConstant : enumConstants) {
                    // 枚举常量已通过registerType注册，这里可以添加额外的处理
                }
            } catch (Exception e) {
                log.debug("Failed to register enum constants for: {}", type.getName());
            }
        }

        // 如果是数组类型，注册组件类型
        if (type.isArray()) {
            Class<?> componentType = type.getComponentType();
            if (shouldRegisterType(componentType)) {
                registerTypeWithHints(generationContext, componentType);
            }
        }
    }

    /**
     * 判断是否应该注册该类型
     * 排除基本类型和Java核心类型
     */
    private boolean shouldRegisterType(Class<?> type) {
        if (type == null) {
            return false;
        }

        String typeName = type.getName();

        return !type.isPrimitive() && !typeName.startsWith("java.lang") && !typeName.startsWith("java.util") && !typeName.startsWith("java.time") && !typeName.startsWith("java.math") && !typeName.startsWith("java.io") && !typeName.startsWith("java.nio") && !typeName.startsWith("java.net") && !typeName.startsWith("java.security") && !typeName.startsWith("javax.") && !typeName.startsWith("org.springframework.") ||
                // 允许一些Spring相关的自定义类型
                typeName.startsWith("vip.toby.rpc");
    }
}
