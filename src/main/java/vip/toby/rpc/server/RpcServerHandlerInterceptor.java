package vip.toby.rpc.server;

/**
 * RpcServerHandlerInterceptor
 *
 * @author toby
 */
public interface RpcServerHandlerInterceptor {

    boolean duplicateHandle(String correlationId, String method, Object data);

}
