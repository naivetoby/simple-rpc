package vip.toby.rpc.server;

import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.JSONObject;
import com.rabbitmq.client.Channel;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.validation.annotation.Validated;
import vip.toby.rpc.annotation.RpcDTO;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.R;
import vip.toby.rpc.entity.RpcType;
import vip.toby.rpc.properties.RpcProperties;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EndpointValidationTest {
    interface Positive {}
    @RpcDTO public static class Input {
        @Min(0) @Min(value = 1, groups = Positive.class) public int x;
    }
    static class Endpoint {
        int calls;
        @RpcServerMethod public R defaultGroup(@Valid Input input) { calls++; return R.ok(); }
        @RpcServerMethod public R group(@Validated(Positive.class) Input input) { calls++; return R.ok(); }
    }

    @Test void targetAnnotationsAndGroupsSurviveClassProxy() throws Exception {
        var target = new Endpoint();
        var proxy = new ProxyFactory(target).getProxy();
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var handler = new RpcServerHandler(proxy, UUID.randomUUID().toString(), RpcType.ASYNC,
                    factory.getValidator(), new RpcProperties(), 0, null);
            handler.afterPropertiesSet();
            deliver(handler, "defaultGroup", -1);
            deliver(handler, "group", 0);
            assertEquals(0, target.calls);
            deliver(handler, "defaultGroup", 0);
            deliver(handler, "group", 1);
            assertEquals(2, target.calls);
        }
    }

    private void deliver(RpcServerHandler handler, String command, int x) throws Exception {
        var payload = JSONObject.of("command", command, "data", JSONObject.of("x", x));
        handler.onMessage(new Message(JSONB.toBytes(payload)), mock(Channel.class));
    }
}
