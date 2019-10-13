package vip.toby.rpc.annotation;

import java.lang.annotation.*;

/**
 * RpcMethod
 *
 * @author toby
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcMethod {

    String name();
}
