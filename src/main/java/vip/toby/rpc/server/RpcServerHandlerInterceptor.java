package vip.toby.rpc.server;

/**
 * RpcServerHandlerInterceptor
 *
 * @author toby
 */
public interface RpcServerHandlerInterceptor {

    /**
     * 通过 [消息 ID] 做重复调用检测
     *
     * @param method        方法
     * @param correlationId 消息 ID
     * @return 是否重复
     */
    default boolean rpcDuplicateHandle(String method, String correlationId) {
        return false;
    }

    /**
     * 通过 [参数] 做重复调用检测
     *
     * @param method 方法
     * @param data   参数
     * @return 是否重复
     */
    default boolean duplicateHandle(String method, Object data) {
        return false;
    }

}
