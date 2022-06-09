package vip.toby.rpc.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.client.RpcClientScannerRegistrar;
import vip.toby.rpc.entity.RpcMode;
import vip.toby.rpc.server.RpcServerPostProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RpcDeferredImportSelector
 *
 * @author toby
 */
@Order
public class RpcDeferredImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        List<String> definitionRegistrars = new ArrayList<>();
        Map<String, Object> annotationAttributes = importingClassMetadata.getAnnotationAttributes(EnableSimpleRpc.class.getCanonicalName());
        if (annotationAttributes != null) {
            RpcMode[] rpcModes = (RpcMode[]) annotationAttributes.get("mode");
            for (RpcMode rpcMode : rpcModes) {
                switch (rpcMode) {
                    case RPC_CLIENT -> definitionRegistrars.add(RpcClientScannerRegistrar.class.getName());
                    case RPC_SERVER -> definitionRegistrars.add(RpcServerPostProcessor.class.getName());
                    default -> {
                    }
                }
            }
        }
        return definitionRegistrars.toArray(new String[0]);
    }

}
