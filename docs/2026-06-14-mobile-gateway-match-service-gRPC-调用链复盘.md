# 2026-06-14 mobile-gateway -> match-service gRPC 调用链复盘

## 1. 文档目的

这份文档记录今天完成的第二条服务间 gRPC 调用链：

```text
浏览器 / curl
  -> mobile-gateway HTTP 8080
  -> mobile-gateway gRPC client
  -> match-service gRPC 19083
  -> MatchService/Ping
  -> 返回 pong
```

这条链路的意义是：

- 证明 `user-service` 的 gRPC 接入模板可以复制到其他业务服务。
- 证明 `proto/match` 可以生成 Java gRPC stub。
- 证明 `match-service` 可以独立启动 gRPC server。
- 证明 `mobile-gateway` 可以同时维护多个后端服务的 gRPC client。
- 提前暴露并解决多个 `ManagedChannel` 注入冲突问题。

这次仍然只做工程骨架，不进入真实匹配业务。

---

## 2. 本次完成内容

本次已经完成：

- `proto/match` 增加 Java 生成模块。
- `proto/match` 本地执行 `mvn clean install` 成功。
- `match-service` 引入本地 `match-proto:0.1.0`。
- `match-service` 启动一个最小 gRPC server，端口 `19083`。
- `mobile-gateway` 引入本地 `match-proto:0.1.0`。
- `mobile-gateway` 增加 gRPC client，连接 `127.0.0.1:19083`。
- `mobile-gateway` 暴露 `/internal/check/match-grpc`，通过 HTTP 触发 gRPC 调用。
- 浏览器访问 `http://localhost:8080/internal/check/match-grpc` 成功。

成功返回：

```json
{"message":"match-service pong: hello from mobile-gateway","matchGrpc":"ok","gateway":"ok"}
```

---

## 3. 为什么要先做 match-service Ping

`match-service` 后续会承载匹配、推荐、滑卡等核心业务。

但在写真实业务前，先做最小 `Ping` 链路有几个好处：

- 先确认服务边界能跑通。
- 先确认 proto 生成和依赖方式正确。
- 先确认 gateway 能调到 match-service。
- 先确认端口、启动顺序、IDEA Maven 解析没有问题。

如果一开始就写真实匹配逻辑，遇到问题时很难判断是业务逻辑错，还是 gRPC / Maven / 启动配置错。

---

## 4. proto/match Java 生成

相关目录：

```text
proto/match
```

关键文件：

```text
proto/match/pom.xml
proto/match/src/main/proto/match.proto
proto/match/README.md
```

生成命令：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\match
mvn clean install
```

为什么要先执行这一步：

```text
match-service 和 mobile-gateway 都依赖 match-proto。
如果 match-proto 没有 install 到本机 Maven 仓库，IDEA 和 Maven 都可能找不到：
com.dating.hanlian.proto:match-proto:0.1.0
```

本地安装位置：

```text
C:\Users\admin\.m2\repository\com\dating\hanlian\proto\match-proto\0.1.0
```

生成出来的核心类：

```text
PingRequest
PingResponse
MatchServiceGrpc
```

---

## 5. match-service gRPC 服务端

相关文件：

```text
dating-server/match-service/pom.xml
dating-server/match-service/src/main/resources/application.yml
dating-server/match-service/src/main/java/com/jianjiange/dating/match/grpc/MatchGrpcService.java
dating-server/match-service/src/main/java/com/jianjiange/dating/match/grpc/MatchGrpcServerLifecycle.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>match-proto</artifactId>
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
    port: 19083
```

为什么 gRPC 用 `19083`：

```text
match-service 原来的 HTTP 端口是 18083。
gRPC 单独使用 19083，避免 HTTP 和 gRPC 抢同一个端口。
```

启动成功日志：

```text
match-service gRPC server started on port 19083
```

---

## 6. mobile-gateway gRPC 客户端

相关文件：

```text
dating-server/mobile-gateway/pom.xml
dating-server/mobile-gateway/src/main/resources/application.yml
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/client/MatchGrpcClientConfig.java
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/MatchGrpcCheckController.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>match-proto</artifactId>
    <version>0.1.0</version>
</dependency>
```

新增配置：

```yaml
match-service:
  grpc:
    host: 127.0.0.1
    port: 19083
```

验证接口：

```text
GET http://localhost:8080/internal/check/match-grpc
```

---

## 7. 本次踩坑 1：IDEA 找不到 match-proto

错误表现：

```text
未解析的依赖项: com.dating.hanlian.proto:match-proto:jar:0.1.0
```

原因：

```text
match-proto 是本地生成包，不是从 Maven Central 下载的公共包。
必须先在 proto/match 执行 mvn clean install。
```

处理方式：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\match
mvn clean install
```

如果 IDEA 仍然红：

```text
1. Maven 面板 Reload All Maven Projects
2. 确认 IDEA Maven Local repository 是 C:\Users\admin\.m2\repository
3. 必要时 File -> Invalidate Caches... -> Invalidate and Restart
```

如果本地仓库里有失败缓存，可以删除：

```powershell
Remove-Item C:\Users\admin\.m2\repository\com\dating\hanlian\proto\match-proto\0.1.0\*.lastUpdated -Force
```

---

## 8. 本次踩坑 2：多个 ManagedChannel 注入冲突

错误表现：

```text
No qualifying bean of type 'io.grpc.ManagedChannel' available:
expected single matching bean but found 2:
matchServiceManagedChannel,userServiceManagedChannel
```

原因：

```text
mobile-gateway 现在同时有 user-service 和 match-service 两个 gRPC channel。
它们的 Java 类型都是 io.grpc.ManagedChannel。
Spring 只按类型注入时，不知道应该选哪一个。
```

处理方式：

```java
@Qualifier("matchServiceManagedChannel") ManagedChannel matchServiceManagedChannel
```

以及：

```java
@Qualifier("userServiceManagedChannel") ManagedChannel userServiceManagedChannel
```

为什么不用 `@Primary`：

```text
以后 mobile-gateway 还会继续接 im-service、post-service、payment-service。
这些 channel 没有谁天然应该是主 Bean。
用 @Qualifier 明确指定，更适合多后端服务调用场景。
```

---

## 9. 验证命令

先启动 `match-service`：

```text
MatchServiceApplication
```

再启动 `mobile-gateway`：

```text
MobileGatewayApplication
```

验证 gateway 到 match-service：

```powershell
curl http://localhost:8080/internal/check/match-grpc
```

成功返回：

```json
{"message":"match-service pong: hello from mobile-gateway","matchGrpc":"ok","gateway":"ok"}
```

---

## 10. 当前结论

当前已经完成两条服务间 gRPC 调用链：

```text
mobile-gateway -> user-service
mobile-gateway -> match-service
```

这说明当前工程骨架已经具备：

- proto 生成 Java stub。
- 业务服务提供 gRPC server。
- gateway 通过 gRPC client 调业务服务。
- 多个 gRPC client 在同一个 Spring Boot 应用里共存。

下一条服务接入时，应直接复用 `docs/gRPC服务接入模板.md`。
