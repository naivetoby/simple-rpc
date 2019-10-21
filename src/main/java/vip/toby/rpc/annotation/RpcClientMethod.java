package vip.toby.rpc.annotation;

import java.lang.annotation.*;

/**
 * RpcClientMethod
 *
 * @author toby
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcClientMethod {

    String value() default "";
}
