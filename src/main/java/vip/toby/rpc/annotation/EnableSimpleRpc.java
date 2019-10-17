package vip.toby.rpc.annotation;

import org.springframework.context.annotation.Import;
import vip.toby.rpc.client.RpcClientScanRegistrar;
import vip.toby.rpc.config.RabbitMqConfiguration;
import vip.toby.rpc.server.RpcServerPostProcessor;

import java.lang.annotation.*;

/**
 * EnableSimpleRpc
 *
 * @author toby
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RabbitMqConfiguration.class, RpcServerPostProcessor.class, RpcClientScanRegistrar.class})
public @interface EnableSimpleRpc {

    String[] clientPath() default {};
}
