# Simple-RPC

Simple-RPC 是基于 Spring Boot 和 RabbitMQ 的轻量级 Java RPC 框架。服务端用注解暴露方法，客户端通过接口调用，无需在业务代码中创建队列、绑定交换机或处理消息回复。

[快速开始](#快速开始) · [分区路由](#分区路由) · [测试与示例](#测试与示例) · [微服务示例](#微服务示例) · [Maven Central](https://central.sonatype.com/artifact/vip.toby.rpc/simple-rpc)

## 主要特性

| 特性 | 使用场景 |
| --- | --- |
| 同步调用 `SYNC` | 需要等待结果时使用。客户端通过 `RpcResult` 读取业务数据或错误明细。 |
| 异步调用 `ASYNC` | 只需提交任务时使用。客户端发送后返回，服务端从队列消费。 |
| 延迟调用 `DELAY` | 需要稍后执行时使用。请求 DTO 继承 `RpcDelayDTO`，通过 `setDelay(...)` 设置延迟毫秒数。需启用 [RabbitMQ 延迟消息插件](https://github.com/rabbitmq/rabbitmq-delayed-message-exchange)。 |
| 分区消费 | 异步和延迟方法可按业务字段路由到分区队列；未指定分区键的方法仍走主队列。 |
| 服务注册 | 自动扫描客户端接口，也支持手动注册扫描范围外的接口。客户端和服务端可用方法别名约定调用名称。 |
| 参数校验 | 请求可使用 `JSONObject` 或标注 `@RpcDTO` 的 JavaBean。服务端在 DTO 参数上使用 `@Valid` 或 `@Validated` 启用校验。 |
| 重复调用检测 | 通过 `RpcServerHandlerInterceptor` 按消息 ID 或请求内容实现去重策略。 |

## 快速开始

以下示例在一个 Spring Boot 应用中同时启动客户端和服务端，通过 RabbitMQ 完成一次加法调用。当前分支使用 **JDK 21、Spring Boot 4.2.0 和 Maven**。

### 1. 引入依赖并连接 RabbitMQ

```xml
<dependency>
    <groupId>vip.toby.rpc</groupId>
    <artifactId>simple-rpc</artifactId>
    <version>3.5.0</version>
</dependency>
```

依赖版本请以 [Maven Central 已发布版本](https://central.sonatype.com/artifact/vip.toby.rpc/simple-rpc/versions) 为准。在 `application.yml` 中配置连接，并赋予应用账号在目标 vhost 上配置、读、写的权限：

```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/}
```

### 2. 启用 Simple-RPC

在启动类上添加 `@EnableSimpleRpc`。本例使用拦截器的默认实现，允许重复调用：

```java
@SpringBootApplication
@EnableSimpleRpc
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Bean
    RpcServerHandlerInterceptor rpcServerHandlerInterceptor() {
        return new RpcServerHandlerInterceptor() {};
    }
}
```

### 3. 定义服务和客户端

客户端和服务端共用请求类型 `AddDTO`。用 `@RpcDTO` 标记请求对象，用 Lombok 的 `@Data` 生成 getter 和 setter：

```java
@Data
@RpcDTO
public class AddDTO {
    @NotNull(message = "x 不能为空")
    private Integer x;

    @NotNull(message = "y 不能为空")
    private Integer y;
}
```

将服务端和客户端放在启动类所在包或其子包下。两端通过服务名 `calculator` 和方法名 `add` 匹配，默认调用类型为 `SYNC`。

```java
@RpcServer(name = "calculator")
public class CalculatorServer {
    @RpcServerMethod
    public R add(@Validated AddDTO dto) {
        return R.okResult(dto.getX() + dto.getY());
    }
}
```

```java
@RpcClient(name = "calculator")
public interface CalculatorClient {
    @RpcClientMethod
    RpcResult add(AddDTO dto);
}
```

### 4. 发起调用

在 Spring Bean 中通过构造器注入 `CalculatorClient`，在应用就绪后调用：

```java
final AddDTO dto = new AddDTO();
dto.setX(2);
dto.setY(5);
final int sum = calculatorClient.add(dto).getIntResult(0); // 7
```

`getIntResult(0)` 直接读取整数结果；没有数值结果时返回默认值 `0`。如果业务需要区分「结果为 0」和「调用失败」，保留 `RpcResult` 查看状态和错误明细；对象结果可用 `getResult(类型.class)` 读取。

## 分区路由

如果同一订单的事件需要按入队顺序处理，可以在异步客户端方法上指定 `@RpcClientMethod(partitionKey = "orderId")`。客户端和服务端的 `partitionNum` 必须相同，且至少为 `2`；分区键必须是请求体中存在且非空的字段。

以服务名 `order`、分区数 `4` 为例：

| 调用类型 | 交换机 | 默认队列 | 指定分区键时 |
| --- | --- | --- | --- |
| `SYNC` | `simple.rpc.sync` | `order.sync` | 不支持分区 |
| `ASYNC` | `simple.rpc.async` | `order.async` | `order.async.0` 至 `order.async.3` 之一 |
| `DELAY` | `simple.rpc.delay` | `order.delay` | `order.delay.0` 至 `order.delay.3` 之一 |

相同键映射到同一分区，不同键也可能映射到同一分区。主队列按 `threadNum` 并发消费；每个分区队列使用单线程，并启用 RabbitMQ 的 `x-single-active-consumer`。顺序指**同一队列的入队顺序**，不是所有分区的全局顺序；对延迟消息，也不是客户端的发送顺序。

## 测试与示例

| 命令 | 验证范围 |
| --- | --- |
| `mvn clean test` | 单元测试：验证消息格式、路由、参数校验、去重和 ACK 行为，无需启动 RabbitMQ。 |
| `mvn -Prabbit-it clean test` | 单元测试 + 真实 RabbitMQ：验证同步往返、普通异步、同键顺序、分区收件队列，以及普通和分区延迟投递。 |

本地运行集成测试前，先启用 RabbitMQ 延迟消息插件，准备专用的空 vhost `simple-rpc-it`，并授予测试账号在该 vhost 上配置、读、写的权限。通过 `SPRING_RABBITMQ_HOST` 和 `SPRING_RABBITMQ_PORT` 指定服务地址，通过 `SPRING_RABBITMQ_USERNAME` 和 `SPRING_RABBITMQ_PASSWORD` 设置测试账号和密码。

[GitHub Actions](.github/workflows/test.yml) 在推送代码或提交 PR 时并行运行两项检查：

- **JVM tests**：使用 JDK 21 运行单元测试和 RabbitMQ 集成测试。
- **Native interop**：在 Linux 上使用 GraalVM 构建原生客户端和服务端，通过 RabbitMQ 验证同步回显和服务端参数校验。详见[原生测试说明](src/test/native/README.md)。

- [端到端用例](src/test/java/vip/toby/demo/DemoIT.java)：查看真实调用及预期结果。
- [客户端接口](src/test/java/vip/toby/demo/client) · [服务端方法](src/test/java/vip/toby/demo/server) · [手动注册示例](src/test/java/vip/toby/demo/config/RpcClientConfig.java)。

### 微服务示例

[Simple-RPC Showcase](https://github.com/naivetoby/simple-rpc-showcase) 展示了如何在两个独立的 Spring Boot 应用之间使用 RPC：HTTP 网关通过共享客户端接口调用话题服务，追加事件并查询已消费的事件列表。使用 Docker Compose 启动示例后，可以向同一话题分别发送同步、异步和延迟事件，观察按话题分区后的消费顺序。

## 许可证

[Apache License 2.0](LICENSE)
