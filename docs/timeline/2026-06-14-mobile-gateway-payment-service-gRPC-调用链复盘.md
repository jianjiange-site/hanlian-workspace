# 2026-06-14 mobile-gateway -> payment-service gRPC 调用链复盘

## 1. 文档目的

这份文档记录今天完成的第五条服务间 gRPC 调用链：

```text
浏览器 / curl
  -> mobile-gateway HTTP 8080
  -> mobile-gateway gRPC client
  -> payment-service gRPC 19085
  -> PaymentService/Ping
  -> 返回 pong
```

这条链路的意义是：

- 证明 `payment-service` 可以按现有 gRPC 模板接入。
- 证明 `proto/payment` 可以生成 Java gRPC stub。
- 证明 `mobile-gateway` 可以维护五个后端服务的 gRPC client。
- 让 `mobile-gateway -> user/match/im/post/payment` 最小链路闭环。

这次只做支付服务边界验证，不接真实支付、订阅、金币、订单或回调逻辑。

---

## 2. 本次完成内容

本次已经完成：

- `proto/payment` 增加 Java 生成模块。
- `proto/payment` 本地执行 `mvn clean install` 成功。
- `payment-service` 引入本地 `payment-proto:0.1.0`。
- `payment-service` 启动一个最小 gRPC server，端口 `19085`。
- `mobile-gateway` 引入本地 `payment-proto:0.1.0`。
- `mobile-gateway` 增加 gRPC client，连接 `127.0.0.1:19085`。
- `mobile-gateway` 暴露 `/internal/check/payment-grpc`，通过 HTTP 触发 gRPC 调用。
- 访问 `http://localhost:8080/internal/check/payment-grpc` 成功。

成功返回：

```json
{"gateway":"ok","paymentGrpc":"ok","message":"payment-service pong: hello from mobile-gateway"}
```

---

## 3. 为什么只做 payment-service Ping

`payment-service` 后续会负责支付相关能力，例如：

```text
订阅
金币
订单
支付回调
权益发放
```

这些属于高风险业务集成，会涉及金额、状态机、幂等、回调验签和对账。

当前阶段先做最小 `Ping` 链路，是为了先确认：

- `payment-service` 能作为独立服务启动。
- `payment-proto` 能生成并被依赖。
- `mobile-gateway` 能调到 `payment-service`。
- gRPC 端口、依赖、Spring Bean 注入都没有问题。

后续写真实支付业务时，应单独设计接口、状态机和幂等规则。

---

## 4. proto/payment Java 生成

相关目录：

```text
proto/payment
```

关键文件：

```text
proto/payment/pom.xml
proto/payment/src/main/proto/payment.proto
proto/payment/README.md
```

生成命令：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\payment
mvn clean install
```

为什么要先执行这一步：

```text
payment-service 和 mobile-gateway 都依赖 payment-proto。
如果 payment-proto 没有 install 到本机 Maven 仓库，IDEA 和 Maven 都可能找不到：
com.dating.hanlian.proto:payment-proto:0.1.0
```

本地安装位置：

```text
C:\Users\admin\.m2\repository\com\dating\hanlian\proto\payment-proto\0.1.0
```

生成出来的核心类：

```text
PingRequest
PingResponse
PaymentServiceGrpc
```

---

## 5. payment-service gRPC 服务端

相关文件：

```text
dating-server/payment-service/pom.xml
dating-server/payment-service/src/main/resources/application.yml
dating-server/payment-service/src/main/java/com/jianjiange/dating/payment/grpc/PaymentGrpcService.java
dating-server/payment-service/src/main/java/com/jianjiange/dating/payment/grpc/PaymentGrpcServerLifecycle.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>payment-proto</artifactId>
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
    port: 19085
```

为什么 gRPC 用 `19085`：

```text
payment-service 原来的 HTTP 端口是 18085。
gRPC 单独使用 19085，避免 HTTP 和 gRPC 抢同一个端口。
```

启动成功日志：

```text
payment-service gRPC server started on port 19085
```

---

## 6. mobile-gateway gRPC 客户端

相关文件：

```text
dating-server/mobile-gateway/pom.xml
dating-server/mobile-gateway/src/main/resources/application.yml
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/client/PaymentGrpcClientConfig.java
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/PaymentGrpcCheckController.java
```

新增依赖：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>payment-proto</artifactId>
    <version>0.1.0</version>
</dependency>
```

新增配置：

```yaml
payment-service:
  grpc:
    host: 127.0.0.1
    port: 19085
```

验证接口：

```text
GET http://localhost:8080/internal/check/payment-grpc
```

---

## 7. 本次注意点：五个 ManagedChannel 共存

当前 `mobile-gateway` 已经有五个 gRPC channel：

```text
userServiceManagedChannel
matchServiceManagedChannel
imServiceManagedChannel
postServiceManagedChannel
paymentServiceManagedChannel
```

它们的 Java 类型都是：

```text
io.grpc.ManagedChannel
```

所以创建 stub 时必须明确指定：

```java
@Qualifier("paymentServiceManagedChannel") ManagedChannel paymentServiceManagedChannel
```

后续抽公共模板时，也必须保留“按服务命名 channel 并显式 Qualifier”的约定。

---

## 8. 验证命令

先启动 `payment-service`：

```text
PaymentServiceApplication
```

再启动 `mobile-gateway`：

```text
MobileGatewayApplication
```

验证 gateway 到 payment-service：

```powershell
curl http://localhost:8080/internal/check/payment-grpc
```

成功返回：

```json
{"gateway":"ok","paymentGrpc":"ok","message":"payment-service pong: hello from mobile-gateway"}
```

---

## 9. 当前结论

当前已经完成五条服务间 gRPC 调用链：

```text
mobile-gateway -> user-service
mobile-gateway -> match-service
mobile-gateway -> im-service
mobile-gateway -> post-service
mobile-gateway -> payment-service
```

这说明今天的核心服务最小 gRPC 骨架已经闭环。

下一阶段可以考虑：

- 抽 `common` gRPC 客户端/服务端约定。
- 接入 Nacos gRPC 服务发现。
- 或开始 `user-service` 最小业务表。
