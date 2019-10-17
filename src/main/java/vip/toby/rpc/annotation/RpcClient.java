package vip.toby.rpc.annotation;

import org.springframework.stereotype.Component;
import vip.toby.rpc.entity.RpcType;

import java.lang.annotation.*;

/**
 * RpcClient
 *
 * @author toby
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RpcClient {

    String[] value() default "";

    String name();

    int replyTimeout() default 2000;

    int maxAttempts() default 3;

    RpcType type() default RpcType.SYNC;
}
