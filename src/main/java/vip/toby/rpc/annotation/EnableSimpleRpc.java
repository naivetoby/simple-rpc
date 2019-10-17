package vip.toby.rpc.annotation;

import org.springframework.context.annotation.Import;
import vip.toby.rpc.scan.RpcScanDefinitionRegistrar;
import vip.toby.rpc.config.RabbitMqConfiguration;

import java.lang.annotation.*;

/**
 * EnableSimpleRpc
 *
 * @author toby
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RabbitMqConfiguration.class, RpcScanDefinitionRegistrar.class})
public @interface EnableSimpleRpc {

    String[] value() default {};
}
