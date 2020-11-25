package vip.toby.rpc.server;

import org.springframework.stereotype.Component;

/**
 * RpcServerBaseHandlerInterceptor
 *
 * @author toby
 */
@Component
public class RpcServerBaseHandlerInterceptor implements RpcServerHandlerInterceptor {

    @Override
    public boolean rpcDuplicateHandle(String method, String correlationId) {
        return false;
    }

    @Override
    public boolean duplicateHandle(String method, Object data) {
        return false;
    }

}
