package vip.toby.rpc.annotation;

import org.springframework.context.annotation.Import;
import vip.toby.rpc.config.RpcAotConfiguration;
import vip.toby.rpc.config.RpcDeferredImportSelector;
import vip.toby.rpc.entity.RpcMode;

import java.lang.annotation.*;

/**
 * EnableSimpleRpc
 *
 * @author toby
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RpcDeferredImportSelector.class, RpcAotConfiguration.class})
public @interface EnableSimpleRpc {

    RpcMode[] mode() default {RpcMode.RPC_CLIENT_AUTO_SCANNER, RpcMode.RPC_SERVER_AUTO_SCANNER, RpcMode.RPC_CLIENT_CONFIGURER};

}
