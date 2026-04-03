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

    String name();

    int replyTimeout() default 3000;

    RpcType type() default RpcType.SYNC;

}
