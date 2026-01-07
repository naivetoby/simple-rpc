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
@Component // 让 IDEA 正常识别
public @interface RpcClient {

    String value();

    int replyTimeout() default 1000;

    RpcType type() default RpcType.SYNC;

}
