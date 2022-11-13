package vip.toby.rpc.annotation;

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
public @interface RpcClient {

    String value();

    int replyTimeout() default 300;

    RpcType type() default RpcType.SYNC;

}
