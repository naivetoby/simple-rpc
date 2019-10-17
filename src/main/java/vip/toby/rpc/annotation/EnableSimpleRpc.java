package vip.toby.rpc.annotation;

import org.springframework.context.annotation.Import;
import vip.toby.rpc.config.RpcConfiguration;

import java.lang.annotation.*;

/**
 * EnableSimpleRpc
 *
 * @author toby
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({RpcConfiguration.class})
public @interface EnableSimpleRpc {

}
