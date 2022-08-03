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
        <version>2.7.2</version>
    </parent>
    
    <groupId>com.demo</groupId>
    <artifactId>demo</artifactId>
    <version>1.0.0</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>vip.toby.rpc</groupId>
            <artifactId>simple-rpc</artifactId>
            <version>1.5.0</version>
        </dependency>
    </dependencies>
</project>
```

## RpcServer Demo
```java
@RpcServer(value="rpc-queue-name", type = {RpcType.SYNC, RpcType.ASYNC}, xMessageTTL = 1000, threadNum = 1)
public class Server {

    @RpcServerMethod
    public ServerResult methodName1(JSONObject params) {
        String param1 = params.getString("param1");
        int param2 = params.getIntValue("param2");
        JSONObject result = new JSONObject();
        result.put("param1", param1);
        result.put("param2", param2);
        result.put("result", param1 + param2);
        return ServerResult.buildSuccessResult(result).message("ok");
    }

    @RpcServerMethod("methodName2-alias")
    public ServerResult methodName2(JSONObject params) {
        return ServerResult.buildFailureMessage("失败").errorCode(233);
    }

    @RpcServerMethod
    public ServerResult methodName3(@Validated PlusDTO plusDTO) {
        int x = plusDTO.getX();
        int y = plusDTO.getY();
        JSONObject result = new JSONObject();
        result.put("x", x);
        result.put("y", y);
        result.put("result", x + y);
        return ServerResult.buildSuccessResult(result).message("ok");
    }

}
```

## RpcClient Demo
```java
@RpcClient(value = "rpc-queue-name", type = RpcType.SYNC)
public interface SyncClient {

    @RpcClientMethod
    RpcResult methodName1(String param1, int param2);

    @RpcClientMethod("methodName2-alias")
    RpcResult methodName2(String param1, int param2);

    @RpcClientMethod
    RpcResult methodName3(PlusDTO plusDTO, JSONObject data, int x, int y);

}

@RpcClient(value = "rpc-queue-name", type = RpcType.ASYNC)
public interface AsyncClient {

    @RpcClientMethod
    void methodName1(String param1, int param2);

    @RpcClientMethod("methodName2-alias")
    void methodName2(String param1, int param2);

}
```

## Application Demo
```java
@EnableSimpleRpc
@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
public class Application {

    private final SyncClient syncClient;
    private final AsyncClient asyncClient;

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @PostConstruct
    public void test() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            syncClient.methodName1("param1", 2);
            syncClient.methodName1("param1", 2);
            syncClient.methodName1("dew", 46);
            PlusDTO plusDTO = new PlusDTO();
            plusDTO.setX(1);
            plusDTO.setY(1);
            JSONObject data = new JSONObject();
            data.put("x", 2);
            data.put("y", 2);
            RpcResult rpcResult = syncClient.methodName3(plusDTO, data, 3, 3);
            log.info("result: {}", rpcResult.getServerResult().getResult());
            syncClient.methodName1("yyy", 2121);
            asyncClient.methodName2("sss", 27);
        }).start();
    }

}
```

## Demo 源码
https://github.com/thinktkj/simple-rpc-demo

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

[![license](https://img.shields.io/github/license/thinktkj/smrpc.svg?style=flat-square)](https://github.com/thinktkj/smrpc/blob/master/LICENSE)

使用 Apache License - Version 2.0 协议开源。
