package vip.toby.rpc.annotation;

import org.springframework.stereotype.Component;
import vip.toby.rpc.entity.RpcType;

import java.lang.annotation.*;

/**
 * RpcServer
 *
 * @author toby
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RpcServer {

    String name();

    int xMessageTTL() default 1000;

    int threadNum() default 1;

    RpcType[] type() default {RpcType.SYNC, RpcType.ASYNC};
}
