# 2026-06-14 mobile-gateway -> post-service gRPC 调用链复盘

## 1. 文档目的

这份文档记录今天完成的第四条服务间 gRPC 调用链：

```text
浏览器 / curl
  -> mobile-gateway HTTP 8080
  -> mobile-gateway gRPC client
  -> post-service gRPC 19084
  -> PostService/Ping
  -> 返回 pong
```

这条链路的意义是：

- 证明 `post-service` 可以按现有 gRPC 模板接入。
- 证明 `proto/post` 可以生成 Java gRPC stub。
- 证明 `mobile-gateway` 可以继续增加第四个后端 gRPC client。
- 继续验证多 `ManagedChannel` 通过 `@Qualifier` 明确注入的方式是可行的。

这次只做动态/帖子服务边界验证，不接真实发布、列表、审核、图片等业务。

---

## 2. 本次完成内容

本次已经完成：

- `proto/post` 增加 Java 生成模块。
- `proto/post` 本地执行 `mvn clean install` 成功。
- `post-service` 引入本地 `post-proto:0.1.0`。
- `post-service` 启动一个最小 gRPC server，端口 `19084`。
- `mobile-gateway` 引入本地 `post-proto:0.1.0`。
- `mobile-gateway` 增加 gRPC client，连接 `127.0.0.1:19084`。
- `mobile-gateway` 暴露 `/internal/check/post-grpc`，通过 HTTP 触发 gRPC 调用。
- 浏览器访问 `http://localhost:8080/internal/check/post-grpc` 成功。

成功返回：

```json
{"gateway":"ok","message":"post-service pong: hello from mobile-gateway","postGrpc":"ok"}
```

---

## 3. 为什么只做 post-service Ping

`post-service` 后续会负责动态/帖子相关能力，例如：

```text
发布动态
动态列表
图片或内容审核
点赞评论入口
```

这些属于真实业务开发，会涉及表结构、对象存储、审核状态、分页和权限。

当前阶段先做最小 `Ping` 链路，是为了先确认：

- `post-service` 能作为独立服务启动。
- `post-proto` 能生成并被依赖。
- `mobile-gateway` 能调到 `post-service`。
- gRPC 端口、依赖、Spring Bean 注入都没有问题。

后续写真实动态业务时，如果出问题，就能更容易区分是业务逻辑问题还是工程骨架问题。

---

## 4. proto/post Java 生成

相关目录：

```text
proto/post
```

关键文件：

```text
proto/post/pom.xml
proto/post/src/main/proto/post.proto
proto/post/README.md
```

生成命令：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\post
mvn clean install
```

为什么要先执行这一步：

```text
post-service 和 mobile-gateway 都依赖 post-proto。
如果 post-proto 没有 install 到本机 Maven 仓库，IDEA 和 Maven 都可能找不到：
com.dating.hanlian.proto:post-proto:0.1.0
```

本地安装位置：

```text
C:\Users\admin\.m2\repository\com\dating\hanlian\proto\post-proto\0.1.0
```

生成出来的核心类：

```text
PingRequest
PingResponse
PostServiceGrpc
```

---

## 5. post-service gRPC 服务端

相关文件：

```text
dating-server/post-service/pom.xml
dating-server/post-service/src/main/resources/application.yml
dating-server/post-service/src/main/java/com/jianjiange/dating/post/grpc/PostGrpcService.java
dating-server/post-service/src/main/java/com/jianjiange/dating/post/grpc/PostGrpcServerLifecycle.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>post-proto</artifactId>
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
    port: 19084
```

为什么 gRPC 用 `19084`：

```text
post-service 原来的 HTTP 端口是 18084。
gRPC 单独使用 19084，避免 HTTP 和 gRPC 抢同一个端口。
```

启动成功日志：

```text
post-service gRPC server started on port 19084
```

---

## 6. mobile-gateway gRPC 客户端

相关文件：

```text
dating-server/mobile-gateway/pom.xml
dating-server/mobile-gateway/src/main/resources/application.yml
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/client/PostGrpcClientConfig.java
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/PostGrpcCheckController.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>post-proto</artifactId>
    <version>0.1.0</version>
</dependency>
```

新增配置：

```yaml
post-service:
  grpc:
    host: 127.0.0.1
    port: 19084
```

验证接口：

```text
GET http://localhost:8080/internal/check/post-grpc
```

---

## 7. 本次注意点：继续使用 @Qualifier

当前 `mobile-gateway` 已经有四个 gRPC channel：

```text
userServiceManagedChannel
matchServiceManagedChannel
imServiceManagedChannel
postServiceManagedChannel
```

它们的 Java 类型都是：

```text
io.grpc.ManagedChannel
```

所以创建 stub 时必须明确指定：

```java
@Qualifier("postServiceManagedChannel") ManagedChannel postServiceManagedChannel
```

后续再接 `payment-service` 时也要保持这个写法。

---

## 8. 验证命令

先启动 `post-service`：

```text
PostServiceApplication
```

再启动 `mobile-gateway`：

```text
MobileGatewayApplication
```

验证 gateway 到 post-service：

```powershell
curl http://localhost:8080/internal/check/post-grpc
```

成功返回：

```json
{"gateway":"ok","message":"post-service pong: hello from mobile-gateway","postGrpc":"ok"}
```

---

## 9. 当前结论

当前已经完成四条服务间 gRPC 调用链：

```text
mobile-gateway -> user-service
mobile-gateway -> match-service
mobile-gateway -> im-service
mobile-gateway -> post-service
```

`post-service` 目前只完成工程边界，真实动态业务后续再做。
