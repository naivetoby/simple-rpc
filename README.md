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
        <version>2.3.5.RELEASE</version>
    </parent>
    
    <groupId>com.demo</groupId>
    <artifactId>demo</artifactId>
    <version>1.0.0.RELEASE</version>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>vip.toby.rpc</groupId>
            <artifactId>simple-rpc</artifactId>
            <version>1.4.3.RELEASE</version>
        </dependency>
    </dependencies>
</project>
```

## RpcServer Demo
```java
@RpcServer(value="rpc-queue-name", type = {RpcType.SYNC, RpcType.ASYNC}, xMessageTTL = 1000, threadNum = 1)
public class Server {
    
    @RpcServerMethod
    public ServerResult methodName1(@Validated JavaBean param) {
        JSONObject result = new JSONObject();
        result.put("param1", param1);
        result.put("param2", param2);
        result.put("result", param1 + param2);
        return ServerResult.buildSuccessResult(result);
    }

    @RpcServerMethod("methodName2Alias")
    public ServerResult methodName2(@Validated({Group2.class}) JavaBean param) {
        return ServerResult.build(OperateStatus.FAILURE).errorCode(737);
    }

    @RpcServerMethod
    public ServerResult methodName3Alias(@Valid JavaBean param) {
        return ServerResult.build(OperateStatus.SUCCESS).message("操作成功");
    }

    @RpcServerMethod
    public ServerResult methodName4(JSONObject params) {
        return ServerResult.buildFailureMessage("失败").errorCode(233);
    }

}
```

## RpcClient Demo
```java
@RpcClient(value = "rpc-queue-name", type = RpcType.SYNC)
public interface SyncClient {

    @RpcClientMethod
    RpcResult methodName1(String param1, int param2);

    @RpcClientMethod
    RpcResult methodName2Alias(JavaBean param);
    
    @RpcClientMethod("methodName3Alias")
    RpcResult methodName3(String param1, int param2);

    @RpcClientMethod
    RpcResult methodName4(JavaBean param);

}

@RpcClient(value = "rpc-queue-name", type = RpcType.ASYNC)
public interface AsyncClient {

    @RpcClientMethod
    void methodName1(JavaBean param);

    @RpcClientMethod("methodName2Alias")
    void methodName2(JavaBean param);

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
