package vip.toby.demo.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vip.toby.rpc.server.RpcServerHandlerInterceptor;

@Component
@Slf4j
public class RpcServerDefaultDuplicateHandler implements RpcServerHandlerInterceptor {

    @Override
    public boolean rpcDuplicateHandle(String method, String correlationId) {
        // log.info("rpcDuplicateHandle...method: {}, correlationId: {}", method, correlationId);
        return false;
    }

    @Override
    public boolean duplicateHandle(String method, Object data) {
        // log.info("duplicateHandle...method: {}, data: {}", method, data);
        return false;
    }

}
