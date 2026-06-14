# gRPC 服务接入模板

## 1. 文档目的

这份文档总结当前已经跑通的 gRPC 接入模式，方便后续复制到其他服务。

当前已验证模板来自：

```text
mobile-gateway -> user-service
mobile-gateway -> match-service
mobile-gateway -> im-service
mobile-gateway -> post-service
mobile-gateway -> payment-service
```

已验证链路形态：

```text
浏览器 / curl
  -> mobile-gateway HTTP 8080
  -> mobile-gateway gRPC client
  -> 业务服务 gRPC 1908x
  -> <Service>/Ping
  -> 返回 pong
```

后续接入新服务时，优先按这份模板复制。等模式稳定后，再考虑抽公共封装。

---

## 2. 基本原则

服务间调用使用：

```text
gRPC + Protobuf
```

不要做：

```text
服务之间直接 HTTP 互调
服务之间直接读对方数据库
业务服务复制 .proto 文件到自己的源码目录
```

应该做：

```text
proto 模块定义契约
proto 模块生成 Java 包
业务服务依赖生成包
服务端实现 generated base class
客户端通过 generated stub 调用
```

---

## 3. 标准接入顺序

每接入一条新的 gRPC 链路，按这个顺序走：

```text
1. 先有 proto 契约
2. 生成 Java proto 包
3. 服务端引入 proto 包
4. 服务端实现 gRPC service
5. 服务端启动独立 gRPC 端口
6. grpcurl 直连验证服务端
7. 客户端引入 proto 包
8. 客户端创建 channel 和 stub
9. 客户端暴露 internal check 接口
10. curl / 浏览器验证完整调用链
```

为什么这样排：

```text
先验证服务端，再验证客户端。
先验证直连，再验证经过 gateway。
这样出问题时能快速判断是 proto、server、client 还是 HTTP check 的问题。
```

---

## 4. Proto 模块模板

目录模板：

```text
proto/<service>/
  VERSION
  <service>.proto
  pom.xml
  README.md
  src/main/proto/<service>.proto
```

生成命令：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\<service>
mvn clean install
```

当前没有 Nexus 凭据，所以只做：

```text
mvn clean install
```

不要做：

```text
mvn deploy
```

业务服务通过 Maven dependency 引入：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId><service>-proto</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## 5. 服务端接入模板

以 `user-service` 为例。

### 5.1 pom.xml

服务端需要引入：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>user-proto</artifactId>
    <version>0.1.0</version>
</dependency>

<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <version>1.68.1</version>
</dependency>
```

为什么需要 `user-proto`：

```text
服务端要继承 UserServiceGrpc.UserServiceImplBase。
```

为什么需要 `grpc-netty-shaded`：

```text
proto 包只提供 generated classes。
真正监听端口、处理网络请求，需要 gRPC Netty server。
```

### 5.2 application.yml

服务端增加：

```yaml
grpc:
  server:
    port: 19081
```

端口建议：

| 服务 | HTTP | gRPC |
|---|---:|---:|
| user-service | 18081 | 19081 |
| im-service | 18082 | 19082 |
| match-service | 18083 | 19083 |
| post-service | 18084 | 19084 |
| payment-service | 18085 | 19085 |

### 5.3 gRPC Service

服务端类模板：

```java
@Component
public class UserGrpcService extends UserServiceGrpc.UserServiceImplBase {

    @Override
    public void ping(PingRequest request, StreamObserver<PingResponse> responseObserver) {
        PingResponse response = PingResponse.newBuilder()
                .setMessage("user-service pong: " + request.getMessage())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
```

关键点：

```text
继承 generated base class。
重写 proto 里定义的 rpc 方法。
unary RPC 要 onNext 后 onCompleted。
```

### 5.4 gRPC Server Lifecycle

当前最小模板使用 `SmartLifecycle`。

作用：

```text
让 gRPC server 跟 Spring Boot 一起启动和停止。
```

启动成功日志建议统一：

```text
<service-name> gRPC server started on port <port>
```

---

## 6. 服务端验证模板

先启动服务端。

以 `user-service` 为例：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server\user-service
mvn spring-boot:run
```

确认日志：

```text
user-service gRPC server started on port 19081
```

使用 grpcurl 直连：

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/user/src/main/proto/user.proto -d '@' localhost:19081 dating.user.v1.UserService/Ping
```

预期返回：

```json
{
  "message": "user-service pong: hello"
}
```

---

## 7. 客户端接入模板

以 `mobile-gateway` 调 `user-service` 为例。

### 7.1 pom.xml

客户端需要引入：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>user-proto</artifactId>
    <version>0.1.0</version>
</dependency>

<dependency>
    <groupId>io.grpc</groupId>
    <artifactId>grpc-netty-shaded</artifactId>
    <version>1.68.1</version>
</dependency>
```

### 7.2 application.yml

当前最小验证先写固定地址：

```yaml
user-service:
  grpc:
    host: 127.0.0.1
    port: 19081
```

为什么先固定地址：

```text
最小调用链阶段先减少变量。
确认链路通了，再替换成 Nacos 服务发现。
```

### 7.3 gRPC Client Config

客户端配置模板：

```java
@Configuration
public class UserGrpcClientConfig {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel userServiceManagedChannel(
            @Value("${user-service.grpc.host:127.0.0.1}") String host,
            @Value("${user-service.grpc.port:19081}") int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
    }

    @Bean
    public UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub(
            @Qualifier("userServiceManagedChannel") ManagedChannel userServiceManagedChannel) {
        return UserServiceGrpc.newBlockingStub(userServiceManagedChannel);
    }
}
```

关键点：

```text
ManagedChannel 要复用。
不要每次 HTTP 请求都创建新 channel。
本地开发用 usePlaintext。
Bean destroyMethod 用 shutdown。
stub 参数必须用 @Qualifier 指定具体 channel。
```

为什么必须用 `@Qualifier`：

```text
mobile-gateway 当前已经有五个 ManagedChannel。
它们的 Java 类型都是 io.grpc.ManagedChannel。
如果只按类型注入，Spring 不知道该选 user、match、im、post 还是 payment。
```

当前已验证的 channel 命名：

```text
userServiceManagedChannel
matchServiceManagedChannel
imServiceManagedChannel
postServiceManagedChannel
paymentServiceManagedChannel
```

当前已验证的 stub 命名：

```text
userServiceBlockingStub
matchServiceBlockingStub
imServiceBlockingStub
postServiceBlockingStub
paymentServiceBlockingStub
```

### 7.4 Internal Check Controller

验证接口模板：

```java
@GetMapping("/internal/check/user-grpc")
public Map<String, String> checkUserGrpc() {
    PingRequest request = PingRequest.newBuilder()
            .setMessage("hello from mobile-gateway")
            .build();

    PingResponse response = userServiceBlockingStub.ping(request);

    return Map.of(
            "gateway", "ok",
            "userGrpc", "ok",
            "message", response.getMessage()
    );
}
```

为什么需要 check controller：

```text
方便浏览器或 curl 直接验证完整调用链。
这不是正式业务接口，只是 internal 骨架验证接口。
```

---

## 8. 客户端验证模板

启动顺序：

```text
1. 启动 user-service
2. 确认 19081 gRPC server 已启动
3. 启动 mobile-gateway
4. 请求 /internal/check/user-grpc
```

验证：

```powershell
curl http://localhost:8080/internal/check/user-grpc
```

预期返回：

```json
{"userGrpc":"ok","gateway":"ok","message":"user-service pong: hello from mobile-gateway"}
```

---

## 9. 当前还不直接抽 common-starter

当前每个服务已经重复过：

```text
gRPC server lifecycle
gRPC client channel config
gRPC check controller
```

这说明模式已经基本稳定，但现在仍不建议立刻做大范围重构。

更稳的做法是先抽“约定”，再抽代码。

当前可以先形成这些约定：

```text
服务端 gRPC 端口 = HTTP 端口 + 1000。
服务端类名 = <Service>GrpcService。
服务端生命周期类名 = <Service>GrpcServerLifecycle。
客户端配置类名 = <Service>GrpcClientConfig。
channel bean 名称 = <service>ServiceManagedChannel。
stub bean 名称 = <service>ServiceBlockingStub。
stub 参数必须使用 @Qualifier。
internal check 路径 = /internal/check/<service>-grpc。
```

未来可以抽到 `common-starter` 或 `common`：

- gRPC server 自动启动。
- gRPC client 自动创建。
- Nacos discovery 集成。
- traceId interceptor。
- timeout / retry 配置。
- 统一异常映射。
- Micrometer 指标。

为什么现在不急着抽：

```text
当前虽然已经有五条链路，但 Nacos discovery、timeout、retry、traceId 都还没定。
如果现在直接抽很重的 common-starter，容易把“本地固定地址验证模式”固化。
更适合先抽命名、端口、Qualifier、验证接口这些低风险约定。
```

---

## 10. 当前限制

当前模板仍有限制：

- 客户端固定写 `127.0.0.1:port`。
- 没有 Nacos gRPC 服务发现。
- 没有负载均衡。
- 没有超时配置。
- 没有重试策略。
- 没有 traceId 透传。
- 没有统一错误码映射。
- 没有 TLS。

下一阶段再逐步补：

```text
固定地址 -> Nacos discovery
单实例 -> 多实例负载均衡
无拦截器 -> traceId / metrics / auth interceptor
手写配置 -> common-starter
```

---

## 11. 当前已验证链路

当前已经跑通：

```text
GET /internal/check/user-grpc
GET /internal/check/match-grpc
GET /internal/check/im-grpc
GET /internal/check/post-grpc
GET /internal/check/payment-grpc
```

对应端口：

| 链路 | 服务端 gRPC |
|---|---:|
| mobile-gateway -> user-service | 19081 |
| mobile-gateway -> im-service | 19082 |
| mobile-gateway -> match-service | 19083 |
| mobile-gateway -> post-service | 19084 |
| mobile-gateway -> payment-service | 19085 |

---

## 12. 下一步建议

核心服务最小 gRPC 骨架已经闭环。

下一步有两个方向：

```text
A. 抽轻量 gRPC 样板约定，不做大重构
B. 进入 user-service 最小业务表
```

推荐先做 A：

```text
先把命名、端口、@Qualifier、internal check、启动验证方式固定下来。
后续进入业务表时，服务边界就不容易乱。
```
