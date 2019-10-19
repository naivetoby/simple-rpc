package vip.toby.rpc.annotation;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Import;
import vip.toby.rpc.config.RpcAutoConfiguration;
import vip.toby.rpc.config.RpcDefinitionRegistrar;
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
@Import({RpcAutoConfiguration.class, RpcDefinitionRegistrar.class})
public @interface EnableSimpleRpc {

    String[] value() default {};

    RpcMode[] mode() default {RpcMode.RPC_CLIENT, RpcMode.RPC_SERVER};
}
