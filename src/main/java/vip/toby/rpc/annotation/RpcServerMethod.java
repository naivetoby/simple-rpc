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
@Inherited
public @interface RpcServerMethod {

    String value() default "";

    boolean allowDuplicate() default false;

}
