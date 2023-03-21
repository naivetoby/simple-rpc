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
@Component
@Inherited
public @interface RpcServer {

    String value();

    int xMessageTTL() default 500;

    int threadNum() default 10;

    RpcType[] type() default {RpcType.SYNC, RpcType.ASYNC};

}
