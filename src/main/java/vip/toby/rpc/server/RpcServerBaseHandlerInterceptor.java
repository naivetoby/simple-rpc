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
    public boolean duplicateHandle(String correlationId, String method, Object data) {
        return false;
    }

}
