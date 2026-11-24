# Native RPC 往返测试

客户端和服务端是两个独立的 Spring Boot 应用，通过 RabbitMQ 通信。客户端调用 `native-smoke` 服务的 `echo` 方法：发送随机字符串时校验原值回显；发送空白字符串时校验通信成功、业务失败及服务端 `@NotBlank` 提示。两端使用同一个 `ProbeDTO`，服务端无状态。

## 构建

准备 Maven 和 GraalVM JDK 25，将 `JAVA_HOME` 指向 GraalVM，并将 `$JAVA_HOME/bin` 加入 `PATH`。在仓库根目录运行：

```sh
bash src/test/native/build.sh
```

`native-interop` profile 将框架源码与 `src/test/native/server` 或 `src/test/native/client` 下的测试入口一起编译，产物分别写入 `target/native-server/` 和 `target/native-client/`。

## 运行

为测试准备一个 RabbitMQ vhost，赋予测试用户配置、读、写权限。在两个终端设置相同的连接参数：

```sh
export SPRING_RABBITMQ_HOST=127.0.0.1
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_VIRTUAL_HOST=simple-rpc-it
export SPRING_RABBITMQ_USERNAME=it
export SPRING_RABBITMQ_PASSWORD=it
```

在仓库根目录分别运行：

```sh
# 终端一：启动服务端，等待输出 Started Application
target/native-server/native-server
```

```sh
# 终端二：验证回显和参数校验
target/native-client/native-client
```

客户端输出 `RPC_SMOKE_OK` 并以状态码 `0` 退出，表示两种响应均符合预期。按 `Ctrl+C` 停止服务端。
