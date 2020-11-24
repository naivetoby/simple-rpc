package vip.toby.rpc.server;

/**
 * RpcServerHandlerInterceptor
 *
 * @author toby
 */
public interface RpcServerHandlerInterceptor {

    /**
     * 用来做幂等处理, 仅针对 RpcType.SYNC
     *
     * @param correlationId 消息ID
     * @return 是否重复
     */
    boolean rpcDuplicateHandle(String correlationId);

    /**
     * 通过调用参数做重复调用检测
     *
     * @param method 方法
     * @param data 参数
     * @return 是否重复
     */
    boolean duplicateHandle(String method, Object data);

}
