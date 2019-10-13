package vip.toby.rpc.annotation;

import org.springframework.stereotype.Component;
import vip.toby.rpc.entity.RpcServerType;

import java.lang.annotation.*;

/**
 * RpcServer
 *
 * @author toby
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface RpcServer {

    String[] value() default "";

    String name();

    int xMessageTTL() default 10000;

    int threadNum() default 1;

    RpcServerType[] type() default {RpcServerType.SYNC};
}
