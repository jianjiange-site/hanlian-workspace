# 2026-06-14 mobile-gateway -> im-service gRPC 调用链复盘

## 1. 文档目的

这份文档记录今天完成的第三条服务间 gRPC 调用链：

```text
浏览器 / curl
  -> mobile-gateway HTTP 8080
  -> mobile-gateway gRPC client
  -> im-service gRPC 19082
  -> ImService/Ping
  -> 返回 pong
```

这条链路的意义是：

- 证明 `im-service` 也可以按现有 gRPC 模板接入。
- 证明 `proto/im` 可以生成 Java gRPC stub。
- 证明 `mobile-gateway` 可以继续增加第三个后端 gRPC client。
- 继续验证多 `ManagedChannel` 通过 `@Qualifier` 明确注入的方式是可行的。

这次只做 IM 服务边界验证，不接 OpenIM 真实注册、发消息、会话编排。

---

## 2. 本次完成内容

本次已经完成：

- `proto/im` 增加 Java 生成模块。
- `proto/im` 本地执行 `mvn clean install` 成功。
- `im-service` 引入本地 `im-proto:0.1.0`。
- `im-service` 启动一个最小 gRPC server，端口 `19082`。
- `mobile-gateway` 引入本地 `im-proto:0.1.0`。
- `mobile-gateway` 增加 gRPC client，连接 `127.0.0.1:19082`。
- `mobile-gateway` 暴露 `/internal/check/im-grpc`，通过 HTTP 触发 gRPC 调用。
- 浏览器访问 `http://localhost:8080/internal/check/im-grpc` 成功。

成功返回：

```json
{"gateway":"ok","imGrpc":"ok","message":"im-service pong: hello from mobile-gateway"}
```

---

## 3. 为什么只做 im-service Ping

`im-service` 后续会负责 IM 相关编排，例如：

```text
OpenIM 用户注册
OpenIM token 获取
发消息
会话入口
```

但这些属于真实业务集成，会涉及 OpenIM API、账号映射、错误处理、重试、权限和消息状态。

当前阶段先做最小 `Ping` 链路，是为了先确认：

- `im-service` 能作为独立服务启动。
- `im-proto` 能生成并被依赖。
- `mobile-gateway` 能调到 `im-service`。
- gRPC 端口、依赖、Spring Bean 注入都没有问题。

这样后续接 OpenIM 时，如果出问题，可以更明确地判断问题来自 OpenIM 集成，而不是工程骨架。

---

## 4. proto/im Java 生成

相关目录：

```text
proto/im
```

关键文件：

```text
proto/im/pom.xml
proto/im/src/main/proto/im.proto
proto/im/README.md
```

生成命令：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\im
mvn clean install
```

为什么要先执行这一步：

```text
im-service 和 mobile-gateway 都依赖 im-proto。
如果 im-proto 没有 install 到本机 Maven 仓库，IDEA 和 Maven 都可能找不到：
com.dating.hanlian.proto:im-proto:0.1.0
```

本地安装位置：

```text
C:\Users\admin\.m2\repository\com\dating\hanlian\proto\im-proto\0.1.0
```

生成出来的核心类：

```text
PingRequest
PingResponse
ImServiceGrpc
```

---

## 5. im-service gRPC 服务端

相关文件：

```text
dating-server/im-service/pom.xml
dating-server/im-service/src/main/resources/application.yml
dating-server/im-service/src/main/java/com/jianjiange/dating/im/grpc/ImGrpcService.java
dating-server/im-service/src/main/java/com/jianjiange/dating/im/grpc/ImGrpcServerLifecycle.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>im-proto</artifactId>
    <version>0.1.0</version>
</dependency>

<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <version>1.68.1</version>
</dependency>
```

配置：

```yaml
grpc:
  server:
    port: 19082
```

为什么 gRPC 用 `19082`：

```text
im-service 原来的 HTTP 端口是 18082。
gRPC 单独使用 19082，避免 HTTP 和 gRPC 抢同一个端口。
```

启动成功日志：

```text
im-service gRPC server started on port 19082
```

---

## 6. mobile-gateway gRPC 客户端

相关文件：

```text
dating-server/mobile-gateway/pom.xml
dating-server/mobile-gateway/src/main/resources/application.yml
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/client/ImGrpcClientConfig.java
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/ImGrpcCheckController.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>im-proto</artifactId>
    <version>0.1.0</version>
</dependency>
```

新增配置：

```yaml
im-service:
  grpc:
    host: 127.0.0.1
    port: 19082
```

验证接口：

```text
GET http://localhost:8080/internal/check/im-grpc
```

---

## 7. 本次注意点：继续使用 @Qualifier

当前 `mobile-gateway` 已经有三个 gRPC channel：

```text
userServiceManagedChannel
matchServiceManagedChannel
imServiceManagedChannel
```

它们的 Java 类型都是：

```text
io.grpc.ManagedChannel
```

所以创建 stub 时必须明确指定：

```java
@Qualifier("imServiceManagedChannel") ManagedChannel imServiceManagedChannel
```

原因：

```text
如果只写 ManagedChannel，Spring 会发现多个同类型 Bean，不知道该注入哪一个。
```

后续再接 `post-service`、`payment-service` 时也要保持这个写法。

---

## 8. 验证命令

先启动 `im-service`：

```text
ImServiceApplication
```

再启动 `mobile-gateway`：

```text
MobileGatewayApplication
```

验证 gateway 到 im-service：

```powershell
curl http://localhost:8080/internal/check/im-grpc
```

成功返回：

```json
{"gateway":"ok","imGrpc":"ok","message":"im-service pong: hello from mobile-gateway"}
```

---

## 9. 当前结论

当前已经完成三条服务间 gRPC 调用链：

```text
mobile-gateway -> user-service
mobile-gateway -> match-service
mobile-gateway -> im-service
```

这说明当前工程骨架已经具备：

- proto 生成 Java stub。
- 多个业务服务提供 gRPC server。
- gateway 通过多个 gRPC client 调业务服务。
- 多个 `ManagedChannel` 在同一个 Spring Boot 应用中共存。

下一条服务接入时，应继续复用 `docs/gRPC服务接入模板.md`，并优先保留 `@Qualifier` 写法。
