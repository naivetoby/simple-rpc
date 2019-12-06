## Simple-RPC

 非常轻量级的 RPC 调用框架，基于 RabbitMQ 消息队列，使用 Spring-Boot 开发。

## Spring-Boot Dependency

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-dependencies</artifactId>
            <version>2.2.1.RELEASE</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Simple-RPC Dependency

```xml
<dependency>
    <groupId>vip.toby.rpc</groupId>
    <artifactId>simple-rpc</artifactId>
    <version>1.1.3.RELEASE</version>
</dependency>
```

## RpcServer Demo
```java
@RpcServer(value="rpc-queue-name", type = {RpcType.SYNC, RpcType.ASYNC})
public class Server {

    @RpcServerMethod
    public ServerResult methodName1(JSONObject params) {
        String param1 = params.getString("param1");
        int param2 = params.getIntValue("param2");

        JSONObject result = new JSONObject();
        result.put("param1", param1);
        result.put("param2", param2);
        result.put("result", param1 + param2);

        return ServerResult.build(OperateStatus.SUCCESS).result(result).message("ok");
    }

    @RpcServerMethod("methodName2-alias")
    public ServerResult methodName2(JSONObject params) {
        return ServerResult.build(OperateStatus.FAILURE).message("失败").errorCode(233);
    }

}
```

## RpcClient Demo
```java
@RpcClient(value = "rpc-queue-name", type = RpcType.SYNC)
public interface SyncClient {

    @RpcClientMethod
    RpcResult methodName1(@Param("param1") String param1, @Param("param2") int param2);

    @RpcClientMethod("methodName2-alias")
    RpcResult methodName2(@Param("param1") String param1, @Param("param2") int param2);

}

@RpcClient(value = "rpc-queue-name", type = RpcType.ASYNC)
public interface AsyncClient {

    @RpcClientMethod
    void methodName1(@Param("param1") String param1, @Param("param2") int param2);

    @RpcClientMethod("methodName2-alias")
    void methodName2(@Param("param1") String param1, @Param("param2") int param2);

}
```

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
