package vip.toby.rpc.server;

import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.JSONObject;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import vip.toby.rpc.annotation.RpcServerMethod;
import vip.toby.rpc.entity.*;
import vip.toby.rpc.properties.RpcProperties;

import java.io.IOException;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServerContractTest {
    static class Endpoint {
        int calls;
        @RpcServerMethod public R once(JSONObject data) { calls++; return R.ok(); }
        @RpcServerMethod(allowDuplicate = true) public R repeat(JSONObject data) { calls++; return R.ok(); }
        @RpcServerMethod public R broken(JSONObject data) { throw new IllegalStateException("fixture failure"); }
    }
    final Endpoint endpoint = new Endpoint();
    final RpcServerHandlerInterceptor duplicates = mock(RpcServerHandlerInterceptor.class);
    final String name = "server-test-" + UUID.randomUUID(); // main caches handlers per service name
    final Channel channel = mock(Channel.class);

    RpcServerHandler handler(RpcType type) {
        var handler = new RpcServerHandler(endpoint, name, type, mock(Validator.class), new RpcProperties(), 0, duplicates);
        handler.afterPropertiesSet();
        return handler;
    }

    void deliver(RpcServerHandler handler, String command) throws IOException {
        var props = new MessageProperties();
        props.setDeliveryTag(7);
        props.setCorrelationId("request-id");
        props.setReplyTo("reply");
        handler.onMessage(new Message(JSONB.toBytes(JSONObject.of("command", command, "data", new JSONObject())), props), channel);
    }

    JSONObject reply(String command) throws IOException {
        deliver(handler(RpcType.SYNC), command);
        var body = ArgumentCaptor.forClass(byte[].class);
        var props = ArgumentCaptor.forClass(BasicProperties.class);
        verify(channel).basicPublish(eq(""), eq("reply"), props.capture(), body.capture());
        assertEquals("request-id", props.getValue().getCorrelationId());
        verify(channel).basicAck(7, false);
        return JSONB.parseObject(body.getValue());
    }

    @Test void correlationDuplicateEvenWhenPayloadDuplicatesAllowed() throws IOException {
        when(duplicates.rpcDuplicateHandle(name + "_repeat", "request-id")).thenReturn(true);
        assertEquals(RCode.DUPLICATE.getCode(), reply("repeat").getJSONObject("data").getIntValue("code"));
        assertEquals(0, endpoint.calls);
    }

    @Test void repeatedPayloadRejected() throws IOException {
        when(duplicates.duplicateHandle(eq(name + "_once"), any())).thenReturn(true);
        assertEquals(RCode.DUPLICATE.getCode(), reply("once").getJSONObject("data").getIntValue("code"));
        assertEquals(0, endpoint.calls);
    }

    @Test void allowDuplicateSkipsOnlyPayloadCheck() throws IOException {
        assertEquals(RCode.OK.getCode(), reply("repeat").getJSONObject("data").getIntValue("code"));
        verify(duplicates, never()).duplicateHandle(anyString(), any());
        assertEquals(1, endpoint.calls);
    }

    @Test void missingCommandReturnsNotFound() throws IOException {
        assertEquals(RpcStatus.NOT_FOUND.getCode(), reply("absent").getIntValue("code"));
    }

    @Test void invocationFailureReturnsFail() throws IOException {
        assertEquals(RpcStatus.FAIL.getCode(), reply("broken").getIntValue("code"));
    }

    @Test void asyncFailureAcknowledgedWithoutReply() throws IOException {
        deliver(handler(RpcType.ASYNC), "broken");
        verify(channel).basicAck(7, false);
        verifyNoMoreInteractions(channel);
    }

    @Test void delayInvocationFailureNotAcknowledged() throws IOException {
        deliver(handler(RpcType.DELAY), "broken");
        verifyNoInteractions(channel);
    }

    @Test void successfulDelayAcknowledgedWithoutReply() throws IOException {
        deliver(handler(RpcType.DELAY), "once");
        assertEquals(1, endpoint.calls);
        verify(channel).basicAck(7, false);
        verifyNoMoreInteractions(channel);
    }
}
