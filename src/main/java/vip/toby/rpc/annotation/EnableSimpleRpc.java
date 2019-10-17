package vip.toby.rpc.annotation;

import org.springframework.context.annotation.Import;
import vip.toby.rpc.config.RabbitMqConfiguration;
import vip.toby.rpc.RpcBeanPostProcessor;

import java.lang.annotation.*;

/**
 * EnableSimpleRpc
 *
 * @author toby
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RabbitMqConfiguration.class, RpcBeanPostProcessor.class})
public @interface EnableSimpleRpc {

}
