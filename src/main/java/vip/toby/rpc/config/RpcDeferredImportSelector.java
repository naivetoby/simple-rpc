package vip.toby.rpc.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.client.RpcClientConfigurerRegistrar;
import vip.toby.rpc.client.RpcClientScannerRegistrar;
import vip.toby.rpc.entity.RpcMode;
import vip.toby.rpc.server.RpcServerPostProcessor;

import javax.annotation.Nonnull;
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
    @Nonnull
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        final List<String> definitionRegistrars = new ArrayList<>();
        final Map<String, Object> annotationAttributes = importingClassMetadata.getAnnotationAttributes(EnableSimpleRpc.class.getCanonicalName());
        if (annotationAttributes != null) {
            final RpcMode[] rpcModes = (RpcMode[]) annotationAttributes.get("mode");
            for (RpcMode rpcMode : rpcModes) {
                switch (rpcMode) {
                    case RPC_CLIENT_AUTO_SCANNER -> definitionRegistrars.add(RpcClientScannerRegistrar.class.getName());
                    case RPC_SERVER_AUTO_SCANNER -> definitionRegistrars.add(RpcServerPostProcessor.class.getName());
                    case RPC_CLIENT_CONFIGURER ->
                            definitionRegistrars.add(RpcClientConfigurerRegistrar.class.getName());
                    default -> {
                    }
                }
            }
        }
        return definitionRegistrars.toArray(new String[0]);
    }

}
