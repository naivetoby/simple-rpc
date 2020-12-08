package vip.toby.rpc.server;

/**
 * RpcServerBaseHandlerInterceptor
 *
 * @author toby
 */
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
