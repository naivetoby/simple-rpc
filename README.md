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
        <version>3.2.2</version>
    </parent>
    
    <groupId>com.demo</groupId>
    <artifactId>demo</artifactId>
    <version>3.0.1</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>vip.toby.rpc</groupId>
            <artifactId>simple-rpc</artifactId>
            <version>3.0.1</version>
        </dependency>
    </dependencies>
</project>
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
```java
@RpcServer(value = "rpc-queue-name", type = {RpcType.SYNC, RpcType.ASYNC}, xMessageTTL = 1000, threadNum = 1)
public class Server {

    @RpcServerMethod
    public R methodName1(@Validated PlusDTO plusDTO) {
        final int x = plusDTO.getX();
        final int y = plusDTO.getY();
        return R.okResult(x + y);
    }

    @RpcServerMethod("methodName2-alias")
    public R methodName2(@Validated PlusDTO plusDTO) {
        return R.failMessage("计算失败").errorCode(-9999);
    }

}

@RpcServer(value = "rpc-queue-name-other", type = RpcType.SYNC)
public class OtherServer {

    @RpcServerMethod
    public R methodName3(PlusDTO plusDTO) {
        final int x = plusDTO.getX();
        final int y = plusDTO.getY();
        return R.okResult(x + y).message("计算成功, x: {}, y: {}", x, y);
    }

}

@RpcServer(value = "delay-plus", type = RpcType.DELAY)
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

## RpcClient Demo
```java
@RpcClient(value = "rpc-queue-name", type = RpcType.SYNC)
public interface SyncClient {

    @RpcClientMethod
    RpcResult methodName1(PlusDTO plusDTO);

    @RpcClientMethod("methodName2-alias")
    RpcResult methodName2(PlusDTO plusDTO);

}

@RpcClient(value = "rpc-queue-name", type = RpcType.ASYNC)
public interface AsyncClient {

    @RpcClientMethod
    void methodName1(PlusDTO plusDTO);

    @RpcClientMethod("methodName2-alias")
    void methodName2(PlusDTO plusDTO);

}

@RpcClient(value = "rpc-queue-name-other", type = RpcType.SYNC)
public interface OtherSyncClient {

    @RpcClientMethod
    RpcResult methodName3(PlusDTO plusDTO);

}

@RpcClient(value = "delay-plus", type = RpcType.DELAY)
public interface DelayClient {

    @RpcClientMethod
    void delayPlus(DelayPlusDTO delayPlusDTO);

}
```

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
            log.info("syncClient.methodName1, RStatusOk: {}, RResult: {}", rpcResult.isRStatusOk(), rpcResult.getRResult());

            // 同步调用 1-1
            plusDTO.setX(0);
            rpcResult = syncClient.methodName1(plusDTO);
            log.info("syncClient.methodName1, RStatusOk: {}, ErrorMessage: {}, ErrorCode: {}", rpcResult.isRStatusOk(), rpcResult.getResult()
                    .getMessage(), rpcResult.getResult().getErrorCode());

            // 同步调用 2
            plusDTO.setX(2);
            rpcResult = syncClient.methodName2(plusDTO);
            log.info("syncClient.methodName2, RStatusOk: {}, ErrorMessage: {}, ErrorCode: {}", rpcResult.isRStatusOk(), rpcResult.getResult()
                    .getMessage(), rpcResult.getResult().getErrorCode());

            // 异步调用
            asyncClient.methodName2(plusDTO);

            // 同步调用 3
            rpcResult = otherSyncClient.methodName3(plusDTO);
            log.info("otherSyncClient.methodName3, RStatusOk: {}, RResult: {}", rpcResult.isRStatusOk(), rpcResult.getRResult());

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

## 目前适配 JDK 17 版本
启动时，需要添加启动参数 vm: --add-opens java.base/java.lang=ALL-UNNAMED

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
