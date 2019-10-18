package vip.toby.rpc.annotation;

import org.springframework.context.annotation.Import;
import vip.toby.rpc.config.RpcConfiguration;
import vip.toby.rpc.entity.RpcMode;
import vip.toby.rpc.scan.RpcScanDefinitionRegistrar;

import java.lang.annotation.*;

/**
 * EnableSimpleRpc
 *
 * @author toby
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RpcConfiguration.class, RpcScanDefinitionRegistrar.class})
public @interface EnableSimpleRpc {

    String[] value() default {};

    RpcMode[] mode() default {RpcMode.RPC_CLIENT, RpcMode.RPC_SERVER};
}
