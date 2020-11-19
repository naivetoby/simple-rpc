package vip.toby.rpc.server;

import java.lang.reflect.Method;

/**
 * RpcServerHandlerInterceptorAdapter
 *
 * @author toby
 */
public interface RpcServerHandlerInterceptorAdapter {

    default boolean duplicateHandle(String rpcType, String rpcName, Method method, Object data, String correlationId) {
        return false;
    }

}
