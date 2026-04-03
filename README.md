## Simple-RPC

 非常轻量级的 RPC 调用框架，基于 RabbitMQ 消息队列，使用 Spring-Boot 开发。

## Spring-Boot && Simple-RPC Dependency

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.0-M2</version>
    </parent>
    
    <groupId>com.demo</groupId>
    <artifactId>demo</artifactId>
    <version>3.4.6</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>vip.toby.rpc</groupId>
            <artifactId>simple-rpc</artifactId>
            <version>3.4.6</version>
        </dependency>
    </dependencies>
</project>
```

## BizCode
```java
@Getter
public enum BizCode implements ICode {

    PLUS_ERROR(1000, "计算错误"); // 计算错误

    private final int code;
    private final String message;

    BizCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}
```


## RpcDTO
```java
@Data
@RpcDTO
public class PlusDTO {

    @NotNull
    @Min(1)
    private Integer x;

    private int y;

}

@Data
@RpcDTO
public class DelayPlusDTO extends RpcDelayDTO {

    @NotNull
    private Long createTime;

    @NotNull
    @Min(1)
    private Integer x;

    private int y;

}
```

## RpcClientConfig
```java
@Component
public class RpcClientConfig implements RpcClientConfigurer {

    @Override
    public void addRpcClientRegistry(RpcClientRegistry rpcClientRegistry) {
        // 如果 @RpcClient 不在当前项目，可以手动配置
        rpcClientRegistry.addRegistration(OtherSyncClient.class);
    }

}
```

## RpcServer Demo

Spring AOP 代理场景下会自动读取目标类，参数上的 `@Validated` / `@Valid` 校验仍可正常生效。

```java
@RpcServer(name = "rpc-queue-name", type = {RpcType.SYNC, RpcType.ASYNC}, xMessageTTL = 1000, threadNum = 4, partitionNum = 16, queueCapacity = 1000)
public class Server {

    @RpcServerMethod
    public R methodName1(@Validated PlusDTO plusDTO) {
        final int x = plusDTO.getX();
        final int y = plusDTO.getY();
        return R.okResult(x + y);
    }

    @RpcServerMethod(value = "methodName2-alias", partitionKey = "x")
    public R methodName2(@Validated PlusDTO plusDTO) {
        return R.build(BizCode.PLUS_ERROR);
    }

}

@RpcServer(name = "rpc-queue-name-other", type = RpcType.SYNC)
public class OtherServer {

    @RpcServerMethod
    public R methodName3(PlusDTO plusDTO) {
        final int x = plusDTO.getX();
        final int y = plusDTO.getY();
        return R.okResult(x + y).message("计算成功, x: {}, y: {}", x, y);
    }

}

@RpcServer(name = "delay-plus", type = RpcType.DELAY)
@Slf4j
public class DelayPlusServer {

    @RpcServerMethod
    public R delayPlus(@Validated DelayPlusDTO delayPlusDTO) {
        final int x = delayPlusDTO.getX();
        final int y = delayPlusDTO.getY();
        final int delay = delayPlusDTO.getDelay();
        final long createTime = delayPlusDTO.getCreateTime();
        final long now = System.currentTimeMillis();
        log.info("delayPlusDTO: {}, result: {}, delay: {}, duration: {}", delayPlusDTO, x + y, delay, now - createTime);
        return R.ok();
    }

}
```

## Error Detail

```java
return R.failMessage("参数错误").detail(Map.of("field", "role", "reason", "不能为空"));
```

```json
{"code":1,"msg":"参数错误","det":{"field":"role","reason":"不能为空"}}
```

## RpcClient Demo
```java
@RpcClient(name = "rpc-queue-name", type = RpcType.SYNC)
public interface SyncClient {

    @RpcClientMethod
    RpcResult methodName1(PlusDTO plusDTO);

    @RpcClientMethod("methodName2-alias")
    RpcResult methodName2(PlusDTO plusDTO);

}

@RpcClient(name = "rpc-queue-name", type = RpcType.ASYNC)
public interface AsyncClient {

    @RpcClientMethod
    void methodName1(PlusDTO plusDTO);

    @RpcClientMethod("methodName2-alias")
    void methodName2(PlusDTO plusDTO);

}

@RpcClient(name = "rpc-queue-name-other", type = RpcType.SYNC)
public interface OtherSyncClient {

    @RpcClientMethod
    RpcResult methodName3(PlusDTO plusDTO);

}

@RpcClient(name = "delay-plus", type = RpcType.DELAY)
public interface DelayClient {

    @RpcClientMethod
    void delayPlus(DelayPlusDTO delayPlusDTO);

}
```

## Partition Key

只有异步队列和延迟队列支持分区键顺序消费；同步队列不支持。

生产端不需要关心分区键，仍然像原来一样往普通队列发送消息。只有消费端在方法上声明了 `partitionKey`，框架才会在进程内按字段值进行串行分发。

- `@RpcServer.partitionNum`
  只对 `ASYNC` / `DELAY` 生效，表示消费端内部按键顺序执行的分区数量；`<= 1` 时不启用分区执行
- `@RpcServer.queueCapacity`
  本地异步线程池和分区 worker 的排队容量上限，避免把 MQ 积压无限搬进 JVM 内存
- `@RpcServerMethod.partitionKey`
  指定当前异步/延迟方法使用请求体中的哪个字段作为分区键
- `@RpcClient`
  不需要额外配置分区参数，仍然只负责发送消息
- 没有配置 `partitionKey` 的异步/延迟方法
  继续普通并发执行
- 配置了 `partitionKey` 的异步/延迟方法
  会在消费端按字段值哈希到固定分区 worker，同一分区键值顺序执行
- `SYNC` 方法
  不支持分区顺序消费
- 当存在分区方法时
  MQ 入口会退化为单 consumer 拉取，再在进程内并行分发；`threadNum` 用于无序任务线程池大小，`partitionNum` 用于有序分区 worker 数量
- 当本地队列打满时
  新任务会退回当前 MQ listener 线程执行，主动形成背压，而不是继续无限堆积在内存里
- 多实例同时消费同一条队列时
  只能保证单实例内按键有序，不能保证跨实例全局有序
- 消息进入本地线程池后会立即向 MQ 确认
  如果进程在本地执行前崩溃，消息不会自动回到队列；这是一种“降低 producer/consumer 耦合，换取顺序分发能力”的取舍
- 分区模式不会消除积压
  它只是把一部分排队从 MQ 挪到当前实例的本地队列里，所以 `threadNum`、`partitionNum`、`queueCapacity` 都需要结合峰值流量评估

```java
@Data
@RpcDTO
public class CircleEventDTO {

    @NotNull
    private Long uid;

    @NotNull
    private Long circleId;

}
```

```java
@RpcServer(name = "circle-event", type = RpcType.ASYNC, threadNum = 8, partitionNum = 16, queueCapacity = 2000)
public class CircleEventServer {

    @RpcServerMethod
    public R refreshCircleStat(@Validated CircleEventDTO dto) {
        return R.ok();
    }

    @RpcServerMethod(partitionKey = "uid")
    public R handleCircleMemberEvent(@Validated CircleEventDTO dto) {
        return R.ok();
    }

}
```

```java
@RpcClient(name = "circle-event", type = RpcType.ASYNC)
public interface CircleEventClient {

    @RpcClientMethod
    void refreshCircleStat(CircleEventDTO dto);

    @RpcClientMethod
    void handleCircleMemberEvent(CircleEventDTO dto);

}
```

上面这个例子里：

- `refreshCircleStat` 没有配置 `partitionKey`，会继续普通并发消费
- `handleCircleMemberEvent` 配置了 `partitionKey = "uid"`，同一个 `uid` 的消息会顺序执行

注意：

- `partitionKey` 取的是请求 `data` 里的字段名，必须能在消息体中取到
- 只有 `ASYNC` / `DELAY` 支持分区键顺序消费，`SYNC` 不支持
- 分区执行是为了保证“同一分区键在当前消费实例内有序”，不是全局有序

## Application Demo
```java
@EnableSimpleRpc
@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class Application {

    private final AsyncClient asyncClient;
    private final SyncClient syncClient;
    private final OtherSyncClient otherSyncClient;
    private final DelayClient delayClient;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @PostConstruct
    public void test() {
        new Thread(() -> {

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }

            // 同步调用 1
            final PlusDTO plusDTO = new PlusDTO();
            plusDTO.setX(1);
            plusDTO.setY(1);
            RpcResult rpcResult = syncClient.methodName1(plusDTO);
            log.info("syncClient.methodName1, Ok: {}, Result: {}", rpcResult.isOk(), rpcResult.getResult());

            // 同步调用 1-1
            plusDTO.setX(0);
            rpcResult = syncClient.methodName1(plusDTO);
            log.info("syncClient.methodName1, Ok: {}, Message: {}, Code: {}", rpcResult.isOk(), rpcResult.getMessage(), rpcResult.getCode());

            // 同步调用 2
            plusDTO.setX(2);
            rpcResult = syncClient.methodName2(plusDTO);
            log.info("syncClient.methodName2, Ok: {}, Message: {}, Code: {}", rpcResult.isOk(), rpcResult.getMessage(), rpcResult.getCode());

            // 异步调用
            asyncClient.methodName2(plusDTO);

            // 同步调用 3
            rpcResult = otherSyncClient.methodName3(plusDTO);
            log.info("otherSyncClient.methodName3, Ok: {}, Result: {}", rpcResult.isOk(), rpcResult.getResult());

            // 延迟调用, 注意⚠️ RabbitMQ 需要启用插件 https://github.com/rabbitmq/rabbitmq-delayed-message-exchange
            final DelayPlusDTO delayPlusDTO = new DelayPlusDTO();
            delayPlusDTO.setCreateTime(System.currentTimeMillis());
            // 延迟 3 秒后调用
            delayPlusDTO.setDelay(3000);
            delayPlusDTO.setX(5);
            delayPlusDTO.setY(8);
            delayClient.delayPlus(delayPlusDTO);

        }).start();
    }

}
```

## Demo 源码
https://github.com/naivetoby/simple-rpc-demo

## application.yml 配置
```yaml
spring:
  rabbitmq:
    host: 127.0.0.1
    port: 5672
    username: admin
    password: admin
    virtual-host: default_vs
```

## 许可证

[![license](https://img.shields.io/github/license/naivetoby/simple-rpc.svg?style=flat-square)](https://github.com/naivetoby/simple-rpc/blob/main/LICENSE)

使用 Apache License - Version 2.0 协议开源。
