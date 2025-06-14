package vip.toby.rpc.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.beans.factory.aot.BeanRegistrationAotContribution;
import org.springframework.beans.factory.aot.BeanRegistrationCode;
import org.springframework.beans.factory.support.RegisteredBean;
import vip.toby.rpc.annotation.RpcClient;
import vip.toby.rpc.annotation.RpcClientMethod;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * RPC客户端Bean注册AOT贡献
 * 为@RpcClient标注的接口注册JDK代理和反射提示
 *
 * @author toby
 */
@Slf4j
public class RpcClientBeanRegistrationContribution implements BeanRegistrationAotContribution {

    private final RegisteredBean registeredBean;

    public RpcClientBeanRegistrationContribution(RegisteredBean registeredBean) {
        this.registeredBean = registeredBean;
    }

    @Override
    public void applyTo(
            @Nonnull GenerationContext generationContext,
            @Nonnull BeanRegistrationCode beanRegistrationCode
    ) {
        Class<?> beanClass = registeredBean.getBeanClass();

        // 1. 为 @RpcClient 接口注册 JDK 代理
        if (beanClass.isInterface() && beanClass.isAnnotationPresent(RpcClient.class)) {
            generationContext.getRuntimeHints().proxies().registerJdkProxy(beanClass);
            log.debug("Registered JDK proxy for @RpcClient interface: {}", beanClass.getName());
        }

        // 2. 注册接口的反射访问
        generationContext.getRuntimeHints()
                .reflection()
                .registerType(beanClass, MemberCategory.INVOKE_DECLARED_METHODS);

        // 3. 注册 @RpcClient 注解本身的反射访问
        generationContext.getRuntimeHints()
                .reflection()
                .registerType(RpcClient.class, MemberCategory.INVOKE_DECLARED_METHODS);

        // 4. 注册接口的父接口
        registerParentInterfaces(generationContext, beanClass);

        // 5. 注册接口方法的参数和返回值类型
        registerRpcClientMethodTypes(generationContext, beanClass);

        // 6. 注册RPC客户端相关的核心类
        registerRpcClientCoreClasses(generationContext);

        log.debug("Registered AOT hints for @RpcClient interface: {}", beanClass.getName());
    }

    /**
     * 注册父接口
     */
    private void registerParentInterfaces(GenerationContext generationContext, Class<?> interfaceClass) {
        Class<?>[] superInterfaces = interfaceClass.getInterfaces();
        for (Class<?> superInterface : superInterfaces) {
            generationContext.getRuntimeHints()
                    .reflection()
                    .registerType(superInterface, MemberCategory.INVOKE_DECLARED_METHODS);

            // 递归注册父接口的父接口
            registerParentInterfaces(generationContext, superInterface);
        }
    }

    /**
     * 注册RPC客户端接口方法相关的类型反射提示
     */
    private void registerRpcClientMethodTypes(GenerationContext generationContext, Class<?> interfaceClass) {
        try {
            Method[] methods = interfaceClass.getDeclaredMethods();
            for (Method method : methods) {
                // 注册方法参数类型
                registerMethodParameterTypes(generationContext, method);

                // 注册方法返回类型
                registerMethodReturnType(generationContext, method);

                // 注册方法异常类型
                registerMethodExceptionTypes(generationContext, method);

                // 如果方法有@RpcClientMethod注解，也注册该注解
                if (method.isAnnotationPresent(RpcClientMethod.class)) {
                    generationContext.getRuntimeHints()
                            .reflection()
                            .registerType(RpcClientMethod.class, MemberCategory.INVOKE_DECLARED_METHODS);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to register RpcClientMethod types for interface: {}", interfaceClass.getName(), e);
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

        // 如果是集合类型，尝试注册常见的实现类
        if (isCollectionType(type)) {
            registerCollectionImplementations(generationContext, type);
        }
    }

    /**
     * 注册RPC客户端相关的核心类
     */
    private void registerRpcClientCoreClasses(GenerationContext generationContext) {
        // 注册RPC客户端代理相关类
        generationContext.getRuntimeHints()
                .reflection()
                .registerType(RpcClientProxy.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS);

        // 注册动态代理相关类
        generationContext.getRuntimeHints()
                .reflection()
                .registerType(java.lang.reflect.Proxy.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(java.lang.reflect.InvocationHandler.class, MemberCategory.INVOKE_DECLARED_METHODS);
    }

    /**
     * 判断是否为集合类型
     */
    private boolean isCollectionType(Class<?> type) {
        return java.util.Collection.class.isAssignableFrom(type) || java.util.Map.class.isAssignableFrom(type);
    }

    /**
     * 注册集合类型的常见实现类
     */
    private void registerCollectionImplementations(GenerationContext generationContext, Class<?> collectionType) {
        try {
            // 根据接口类型注册常见的实现类
            if (java.util.List.class.isAssignableFrom(collectionType)) {
                generationContext.getRuntimeHints()
                        .reflection()
                        .registerType(java.util.ArrayList.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
            } else if (java.util.Set.class.isAssignableFrom(collectionType)) {
                generationContext.getRuntimeHints()
                        .reflection()
                        .registerType(java.util.HashSet.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
            } else if (java.util.Map.class.isAssignableFrom(collectionType)) {
                generationContext.getRuntimeHints()
                        .reflection()
                        .registerType(java.util.HashMap.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);
            }
        } catch (Exception e) {
            log.debug("Failed to register collection implementations for: {}", collectionType.getName());
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

        return !type.isPrimitive() && !typeName.startsWith("java.lang") && !typeName.startsWith("java.time") && !typeName.startsWith("java.math") && !typeName.startsWith("java.io") && !typeName.startsWith("java.nio") && !typeName.startsWith("java.net") && !typeName.startsWith("java.security") && !typeName.startsWith("javax.") && (!typeName.startsWith("org.springframework.") ||
                // 允许RPC框架相关的Spring类
                typeName.contains("rpc")) &&
                // 允许用户自定义类型和RPC框架类型
                (typeName.startsWith("vip.toby.rpc") || !typeName.startsWith("java.") && !typeName.startsWith("javax.") && !typeName.startsWith("org.springframework.core") && !typeName.startsWith("org.springframework.beans"));
    }
}
