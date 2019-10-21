package vip.toby.rpc.annotation;

import java.lang.annotation.*;

/**
 * Param
 *
 * @author toby
 */
@Target({ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Param {

    String value();
}
