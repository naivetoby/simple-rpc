package vip.toby.rpc.client;

import com.alibaba.fastjson2.JSONB;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import vip.toby.demo.entity.DelayPlusDTO;
import vip.toby.rpc.annotation.RpcClientMethod;
import vip.toby.rpc.entity.*;
import vip.toby.rpc.properties.RpcProperties;

import java.lang.reflect.Proxy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientContractTest {
    interface Contract {
        @RpcClientMethod("sum") RpcResult sync(int x, JSONObject extra);
        @RpcClientMethod(partitionKey = "x") RpcResult partitionedSync(JSONObject data);
        @RpcClientMethod(partitionKey = "x") void keyed(JSONObject data);
        @RpcClientMethod void ordinary(JSONObject data);
        @RpcClientMethod(partitionKey = "x") void later(DelayPlusDTO dto);
        void unannotated();
    }
    final RabbitTemplate transport = mock(RabbitTemplate.class);

    Contract client(RpcType type, int partitions) {
        var handler = new RpcClientProxy<>(Contract.class, "test", type, transport, new RpcProperties(), 3000, partitions);
        return (Contract) Proxy.newProxyInstance(Contract.class.getClassLoader(), new Class[]{Contract.class}, handler);
    }

    @Test void syncEnvelopeAndMissingReply() {
        var result = client(RpcType.SYNC, 1).sync(2, JSONObject.of("y", 3));
        assertEquals(RpcStatus.UNAVAILABLE.getCode(), result.getStatusCode());
        var sent = ArgumentCaptor.forClass(Message.class);
        verify(transport).sendAndReceive(eq("simple.rpc.sync"), eq("test"), sent.capture(), any(CorrelationData.class));
        assertEquals(JSONObject.of("command", "sum", "data", JSONObject.of("x", 2, "y", 3)), JSONB.parseObject(sent.getValue().getBody()));
        assertNotNull(sent.getValue().getMessageProperties().getCorrelationId());
    }

    @Test void unkeyedAsyncKeepsMainQueueAndKeyedUsesStablePartition() {
        var async = client(RpcType.ASYNC, 4);
        var data = JSONObject.of("x", 9);
        async.ordinary(data);
        async.keyed(data);
        verify(transport).send(eq("simple.rpc.async"), eq("test"), any(Message.class), any(CorrelationData.class));
        verify(transport).send(eq("simple.rpc.async"), eq("test.1"), any(Message.class), any(CorrelationData.class));
    }

    @Test void delayedPartitionPreservesDelayHeader() {
        var dto = new DelayPlusDTO();
        dto.setX(10);
        dto.setDelay(350);
        client(RpcType.DELAY, 4).later(dto);
        var sent = ArgumentCaptor.forClass(Message.class);
        verify(transport).send(eq("simple.rpc.delay"), eq("test.3"), sent.capture(), any(CorrelationData.class));
        assertEquals(350L, sent.getValue().getMessageProperties().getHeaders().get("x-delay"));
    }

    @Test void invalidPartitionDeclarationsCannotPublish() {
        var empty = new JSONObject();
        assertThrows(RuntimeException.class, () -> client(RpcType.SYNC, 2).sync(1, empty));
        assertThrows(RuntimeException.class, () -> client(RpcType.SYNC, 1).partitionedSync(empty));
        assertThrows(RuntimeException.class, () -> client(RpcType.ASYNC, 1).keyed(JSONObject.of("x", 2)));
        assertThrows(RuntimeException.class, () -> client(RpcType.ASYNC, 2).keyed(empty));
        empty.put("x", null);
        assertThrows(RuntimeException.class, () -> client(RpcType.ASYNC, 2).keyed(empty));
        verifyNoInteractions(transport);
    }

    @Test void invalidSignaturesCannotPublish() {
        var data = new JSONObject();
        assertThrows(RuntimeException.class, () -> client(RpcType.SYNC, 1).unannotated());
        assertThrows(RuntimeException.class, () -> client(RpcType.SYNC, 1).ordinary(data));
        assertThrows(RuntimeException.class, () -> client(RpcType.ASYNC, 1).sync(1, data));
        assertThrows(RuntimeException.class, () -> client(RpcType.DELAY, 1).ordinary(data));
        verifyNoInteractions(transport);
    }
}
