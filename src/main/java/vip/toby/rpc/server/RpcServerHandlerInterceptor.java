package vip.toby.rpc.server;

/**
 * RpcServerHandlerInterceptor
 *
 * @author toby
 */
public interface RpcServerHandlerInterceptor {

    /**
     * 用来做幂等处理, 仅针对 RpcType.SYNC
     */
    boolean rpcDuplicateHandle(String correlationId);

    /**
     * 通过调用参数做重复调用检测
     */
    boolean duplicateHandle(String method, Object data);

}
