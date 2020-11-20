package vip.toby.rpc.server;

import java.lang.reflect.Method;

/**
 * RpcServerDuplicateHandler
 *
 * @author toby
 */
public interface RpcServerDuplicateHandler {

    default boolean duplicateHandle(String rpcType, String rpcName, Method method, Object data, String correlationId) {
        return false;
    }

}
