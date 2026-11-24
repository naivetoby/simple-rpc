package vip.toby.demo;

import com.demo.client.OtherSyncClient;
import org.junit.jupiter.api.*;
import com.alibaba.fastjson2.JSONB;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import vip.toby.demo.client.*;
import vip.toby.demo.entity.*;
import vip.toby.demo.server.*;
import vip.toby.rpc.annotation.EnableSimpleRpc;
import vip.toby.rpc.entity.*;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoIT {
    @EnableSimpleRpc
    @SpringBootApplication
    static class Application {}

    private ConfigurableApplicationContext context;

    @BeforeAll void start() {
        context = new SpringApplicationBuilder(Application.class).run("--spring.rabbitmq.virtual-host=simple-rpc-it");
    }

    @AfterAll void stop() {
        if (context != null) context.close();
    }

    // Record the payload marker at the consumer, not the producer's intended routing key.
    private BlockingQueue<Integer> observeQueue(String queue) {
        BlockingQueue<Integer> received = new LinkedBlockingQueue<>();
        var listener = context.getBean("MessageListenerContainer-" + queue, SimpleMessageListenerContainer.class);
        listener.addAfterReceivePostProcessors(message -> {
            if (queue.equals(message.getMessageProperties().getConsumerQueue())) {
                received.add(JSONB.parseObject(message.getBody()).getJSONObject("data").getInteger("y"));
            }
            return message;
        });
        return received;
    }

    private PlusDTO plus(int x, int y) {
        var dto = new PlusDTO();
        dto.setX(x);
        dto.setY(y);
        return dto;
    }

    @Test void scannedClientAndExplicitlyRegisteredClientRoundTrip() {
        var dto = plus(2, 5);
        assertEquals(7, context.getBean(SyncClient.class).methodName1(dto).getIntResult(-1));
        var other = context.getBean(OtherSyncClient.class).methodName3(dto);
        assertEquals(7, other.getIntResult(-1));
        assertTrue(other.getMessage().contains("计算成功"));
    }

    @Test void validationAndAliasedBusinessErrorsAreNotTransportErrors() {
        var sync = context.getBean(SyncClient.class);
        var invalid = sync.methodName1(plus(0, 1));
        assertTrue(invalid.isOk());
        assertEquals(RCode.FAIL.getCode(), invalid.getCode());
        var error = sync.methodName2(plus(2, 5));
        assertTrue(error.isOk());
        assertEquals(BizCode.PLUS_ERROR.getCode(), error.getCode());
        assertEquals(BizCode.PLUS_ERROR.getMessage(), error.getMessage());
        assertEquals(Map.of("x", 2), error.getDetail());
    }

    @Test void ordinaryAsyncAndSameKeyOrderedDelivery() throws InterruptedException {
        var async = context.getBean(AsyncClient.class);
        var server = context.getBean(Server.class);
        var ordinary = observeQueue("rpc-queue-name.async");
        var partition1 = observeQueue("rpc-queue-name.async.1");
        var partition3 = observeQueue("rpc-queue-name.async.3");
        async.methodName2(plus(9, 42));
        assertEquals(42, ordinary.poll(5, TimeUnit.SECONDS));
        // The alias method is shared with SYNC; discard any earlier SYNC observations.
        Integer seen;
        do { seen = server.normal.poll(5, TimeUnit.SECONDS); } while (seen != null && seen != 42);
        assertEquals(42, seen);
        async.methodName3(plus(9, 1));
        async.methodName3(plus(9, 2));
        assertEquals(1, server.partition.poll(5, TimeUnit.SECONDS));
        assertEquals(2, server.partition.poll(5, TimeUnit.SECONDS));
        async.methodName3(plus(10, 3));
        assertEquals(3, server.partition.poll(5, TimeUnit.SECONDS));
        assertEquals(1, partition1.poll(5, TimeUnit.SECONDS));
        assertEquals(2, partition1.poll(5, TimeUnit.SECONDS));
        assertEquals(3, partition3.poll(5, TimeUnit.SECONDS));
    }

    @Test void queueDeclarationsKeepTtlDurabilityAndPartitionIsolation() {
        var sync = context.getBean("Queue-rpc-queue-name.sync", Queue.class);
        assertTrue(sync.isAutoDelete());
        assertEquals(1500, sync.getArguments().get("x-message-ttl"));
        var ordinary = context.getBean("Queue-rpc-queue-name.async", Queue.class);
        assertTrue(ordinary.isDurable());
        assertFalse(ordinary.getArguments().containsKey("x-single-active-consumer"));
        for (int i = 0; i < 4; i++) {
            var part = context.getBean("Queue-rpc-queue-name.async." + i, Queue.class);
            assertTrue(part.isDurable());
            assertEquals(true, part.getArguments().get("x-single-active-consumer"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void delayedExchangeRoutesAndDeliversAfterDelay(boolean keyed) throws Exception {
        var dto = new DelayPlusDTO();
        dto.setX(5);
        dto.setY(8);
        dto.setCreateTime(System.currentTimeMillis());
        dto.setDelay(400);
        var client = context.getBean(DelayClient.class);
        var queue = observeQueue(keyed ? "delay-plus.delay.1" : "delay-plus.delay");
        long start = System.nanoTime();
        if (keyed) client.delayPlusByKey(dto);
        else client.delayPlus(dto);
        var delivery = context.getBean(DelayPlusServer.class).received.poll(8, TimeUnit.SECONDS);
        assertNotNull(delivery, "delayed handler did not receive the message");
        assertEquals(8, queue.poll(1, TimeUnit.SECONDS));
        assertEquals(13, delivery.sum());
        assertTrue(delivery.receivedAt() - start >= TimeUnit.MILLISECONDS.toNanos(390));
    }
}
