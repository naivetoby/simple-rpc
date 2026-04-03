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
@RpcServer(name = "rpc-queue-name", type = {RpcType.SYNC, RpcType.ASYNC}, xMessageTTL = 1000, threadNum = 4, partitionNum = 16)
public class Server {

    @RpcServerMethod
    public R methodName1(@Validated PlusDTO plusDTO) {
        final int x = plusDTO.getX();
        final int y = plusDTO.getY();
        return R.okResult(x + y);
    }

    @RpcServerMethod("methodName2-alias")
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

@RpcClient(name = "rpc-queue-name", type = RpcType.ASYNC, partitionNum = 16)
public interface AsyncClient {

    @RpcClientMethod
    void methodName1(PlusDTO plusDTO);

    @RpcClientMethod(value = "methodName2-alias", partitionKey = "x")
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

只有 `ASYNC` / `DELAY` 并且声明了 `partitionKey` 的调用才会走分区队列；其他调用继续走原来的普通队列。

- `@RpcClient.partitionNum` / `@RpcServer.partitionNum`
  分区队列数量；默认值为 `1`，表示不启用分区；`>= 2` 时启用分区能力
- `@RpcClientMethod.partitionKey`
  指定当前异步/延迟方法使用请求体中的哪个字段作为分区键
- 没有配置 `partitionKey` 的方法
  继续发送到原来的 `rpcName` 队列，按 `threadNum` 并发消费
- 配置了 `partitionKey` 的方法
  会按字段值哈希到 `rpcName.0 ... rpcName.N-1` 中的一条分区队列
- 分区队列
  由 RabbitMQ 队列本身保证 FIFO，并且每条分区队列固定单线程消费
- `SYNC`
  不支持分区路由；如果配置了 `partitionNum` 或 `partitionKey`，会直接报错
- `ASYNC` / `DELAY`
  支持分区路由；是否启用取决于客户端是否声明了 `partitionKey`
- 多实例部署时
  分区队列开启 `x-single-active-consumer`，同一时刻每个分区队列只有一个活跃 consumer，从而保证同一分区键不会被多个实例并发消费
- 分区顺序语义
  保证的是“同一分区键映射到同一分区队列后的顺序消费”，不是整个服务所有消息的全局顺序
- 配置要求
  `client` 和 `server` 两边必须使用相同的 `partitionNum`，否则会出现 producer 发送到了 server 未声明的分区队列

例如：

```java
@RpcServer(name = "order", type = {RpcType.SYNC, RpcType.ASYNC, RpcType.DELAY}, threadNum = 8, partitionNum = 16)
```

启动后会拆成三组监听资源：

- `SYNC`
  - exchange: `simple.rpc.sync`
  - queue: `order.sync`
  - listener: 并发 `threadNum`
- `ASYNC`
  - exchange: `simple.rpc.async`
  - 主队列: `order.async`，未配置 `partitionKey` 的 method 会到这里
  - 分区队列: `order.async.0 ~ order.async.15`，配置了 `partitionKey` 的 method 会按 key 路由到这里
  - 主队列 listener: 并发 `threadNum`
  - 分区队列 listener: 每条并发 `1`
- `DELAY`
  - exchange: `simple.rpc.delay`
  - 主队列: `order.delay`，未配置 `partitionKey` 的 method 会到这里
  - 分区队列: `order.delay.0 ~ order.delay.15`，配置了 `partitionKey` 的 method 会按 key 路由到这里
  - 主队列 listener: 并发 `threadNum`
  - 分区队列 listener: 每条并发 `1`

也就是说，同一个 `@RpcServer` 会按 `type` 拆成不同的 exchange、queue 和 listener；`partitionNum` 只会在 `ASYNC` / `DELAY` 分支里生效。

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
@RpcServer(name = "circle-event", type = RpcType.ASYNC, threadNum = 8, partitionNum = 16)
public class CircleEventServer {

    @RpcServerMethod
    public R refreshCircleStat(@Validated CircleEventDTO dto) {
        return R.ok();
    }

    @RpcServerMethod
    public R handleCircleMemberEvent(@Validated CircleEventDTO dto) {
        return R.ok();
    }

}
```

```java
@RpcClient(name = "circle-event", type = RpcType.ASYNC, partitionNum = 16)
public interface CircleEventClient {

    @RpcClientMethod
    void refreshCircleStat(CircleEventDTO dto);

    @RpcClientMethod(partitionKey = "uid")
    void handleCircleMemberEvent(CircleEventDTO dto);

}
```

上面这个例子里：

- `refreshCircleStat` 没有配置 `partitionKey`，会继续普通并发消费
- `handleCircleMemberEvent` 配置了 `partitionKey = "uid"`，同一个 `uid` 的消息会顺序执行

注意：

- `partitionKey` 取的是请求 `data` 里的字段名，必须能在消息体中取到
- `client` 和 `server` 两边都需要配置相同的 `partitionNum`
- `SYNC` 不支持分区；只有 `ASYNC` / `DELAY` 支持
- 分区顺序消费依赖 RabbitMQ 分区队列，而不是进程内本地队列
- 单个分区队列在多实例下依赖 RabbitMQ `x-single-active-consumer` 保持单活消费

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
