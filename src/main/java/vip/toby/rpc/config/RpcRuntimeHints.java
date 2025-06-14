package vip.toby.rpc.config;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import vip.toby.rpc.annotation.*;
import vip.toby.rpc.client.RpcClientProxy;

/**
 * RPC框架AOT运行时提示注册器
 * SpringBoot 3.x 简化版本
 *
 * @author toby
 */
public class RpcRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {

        // 1. 注册RPC核心注解（运行时读取必须）
        hints.reflection()
                .registerType(RpcClient.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(RpcServer.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(RpcClientMethod.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(RpcServerMethod.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(RpcDTO.class, MemberCategory.INVOKE_DECLARED_METHODS);

        // 2. 注册动态代理核心类（必须）
        hints.reflection()
                .registerType(RpcClientProxy.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(java.lang.reflect.Proxy.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(java.lang.reflect.InvocationHandler.class, MemberCategory.INVOKE_DECLARED_METHODS);

        // 3. 注册JSON序列化（FastJSON2）
        hints.reflection()
                .registerType(com.alibaba.fastjson2.JSON.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(com.alibaba.fastjson2.JSONB.class, MemberCategory.INVOKE_DECLARED_METHODS)
                .registerType(com.alibaba.fastjson2.JSONObject.class, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS);

        // 4. 注册RPC实体类
        registerRpcEntities(hints);
    }

    /**
     * 注册RPC实体类
     */
    private void registerRpcEntities(RuntimeHints hints) {
        // 只注册确实存在的类
        String[] entityClasses = {"vip.toby.rpc.entity.RpcType", "vip.toby.rpc.entity.RpcMode", "vip.toby.rpc.entity.RpcResult", "vip.toby.rpc.entity.RpcStatus", "vip.toby.rpc.entity.RStatus", "vip.toby.rpc.entity.R", "vip.toby.rpc.properties.RpcProperties"};

        for (String className : entityClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                if (clazz.isEnum()) {
                    hints.reflection().registerType(clazz, MemberCategory.values());
                } else {
                    hints.reflection()
                            .registerType(clazz, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.INVOKE_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS);
                }
            } catch (ClassNotFoundException e) {
                // 类不存在则跳过
            }
        }
    }
}
