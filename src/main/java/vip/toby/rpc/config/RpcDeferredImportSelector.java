package vip.toby.rpc.config;

import org.springframework.context.annotation.DeferredImportSelector;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;

/**
 * RpcDeferredImportSelector
 *
 * @author toby
 */
@Order
public class RpcDeferredImportSelector implements DeferredImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata importingClassMetadata) {
        return new String[]{RpcDefinitionRegistrar.class.getName()};
    }

}
