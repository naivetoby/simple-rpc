package vip.toby.rpc.annotation;

import java.lang.annotation.*;

/**
 * RpcServerMethod
 *
 * @author toby
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcServerMethod {

    String name();
}
