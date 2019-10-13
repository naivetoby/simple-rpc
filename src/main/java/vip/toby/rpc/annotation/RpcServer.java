package vip.toby.rpc.annotation;

import org.springframework.stereotype.Component;
import vip.toby.rpc.entity.RpcServerType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RpcServer
 *
 * @author toby
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface RpcServer {

    String[] value() default "";

    String name();

    int xMessageTTL() default 10000;

    int threadNum() default 1;

    RpcServerType[] type() default {RpcServerType.SYNC};
}
