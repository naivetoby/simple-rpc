package vip.toby.rpc.server;

import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * RpcServerHandlerInterceptorAdapter
 *
 * @author toby
 */
@Component
public class RpcServerHandlerInterceptorAdapter {

    public boolean duplicateHandle(String rpcType, String rpcName, Method method, Object data, String correlationId) {
        return false;
    }

}
