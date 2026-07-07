# 小白看懂proto和gRPC

## 1. 这份文档是干什么的

这份文档是给自己以后回来看用的，也适合 proto 和 gRPC 零基础的人先建立整体感觉。

你可以把它理解成一句话：

```text
proto 是合同，gRPC 是按合同打电话的方式。
```

在 `hanlian-workspace` 里，我们已经把这些链路跑通了：

- `mobile-gateway -> user-service`
- `mobile-gateway -> match-service`
- `mobile-gateway -> im-service`
- `mobile-gateway -> post-service`
- `mobile-gateway -> payment-service`

当前这套项目没有直接用 HTTP 去调用别的业务服务，而是统一用：

```text
proto + gRPC
```

---

## 2. 先用最简单的话说清楚 proto 和 gRPC

### 2.1 proto 是什么

`proto` 可以理解成“服务之间的约定书”。

它负责写清楚：

- 这个服务叫什么
- 能提供什么方法
- 方法要什么参数
- 方法返回什么结果

比如 `user.proto` 里会有这种定义：

```proto
service UserService {
  rpc Ping (PingRequest) returns (PingResponse);
}
```

意思就是：

- 有一个 `UserService`
- 它有一个 `Ping` 方法
- 输入是 `PingRequest`
- 输出是 `PingResponse`

### 2.2 gRPC 是什么

gRPC 可以理解成“按 proto 合同打电话的方式”。

它负责真正把请求发出去、把响应拿回来。

你可以先记住这个关系：

```text
proto 负责定义
gRPC 负责通信
```

---

## 3. 为什么这个项目要用 proto + gRPC

我们这个项目后面会有很多服务：

- `user-service`
- `match-service`
- `im-service`
- `post-service`
- `payment-service`

如果每个服务都自己随便调接口，后面会很乱。

所以项目里统一用 proto + gRPC 的好处是：

1. 接口先定下来，大家按同一份合同做事。
2. Java 代码可以自动生成，不用手写一堆样板代码。
3. 服务间调用更清楚，不容易误调别人的数据库。
4. 后面做服务发现、超时、重试、链路追踪时，更好统一。

---

## 4. 我们这个项目里，proto 和 gRPC 的整体流程

可以先看这张图：

```mermaid
flowchart LR
  A[proto 文件] --> B[生成 Java stub]
  B --> C[服务端实现 gRPC Service]
  C --> D[Gateway 创建 channel 和 stub]
  D --> E[internal check 接口]
  E --> F[curl / 浏览器验证]
```

再翻译成人话：

```text
1. 先写 proto
2. 用 Maven 生成 Java 代码
3. 服务端实现生成出来的 base class
4. 网关拿生成出来的 stub 去调用
5. 用 /internal/check 接口验证整条链路
```

---

## 5. 当前项目已经跑通了什么

### 5.1 现有链路

我们已经跑通了五条链路：

| 链路 | 网关接口 | 服务端 gRPC 端口 |
|---|---|---:|
| mobile-gateway -> user-service | `/internal/check/user-grpc` | 19081 |
| mobile-gateway -> im-service | `/internal/check/im-grpc` | 19082 |
| mobile-gateway -> match-service | `/internal/check/match-grpc` | 19083 |
| mobile-gateway -> post-service | `/internal/check/post-grpc` | 19084 |
| mobile-gateway -> payment-service | `/internal/check/payment-grpc` | 19085 |

### 5.2 当前端口约定

我们现在的约定是：

| 服务 | HTTP | gRPC |
|---|---:|---:|
| user-service | 18081 | 19081 |
| im-service | 18082 | 19082 |
| match-service | 18083 | 19083 |
| post-service | 18084 | 19084 |
| payment-service | 18085 | 19085 |

这里有个很简单的记法：

```text
gRPC 端口 = HTTP 端口 + 1000
```

---

## 6. proto 在项目里长什么样

我们现在每个服务都有自己的 proto 模块，比如：

```text
proto/user
proto/im
proto/match
proto/post
proto/payment
```

每个 proto 模块里最核心的是：

```text
pom.xml
src/main/proto/<service>.proto
README.md
```

举个例子，`proto/user/src/main/proto/user.proto` 会长得像这样：

```proto
syntax = "proto3";

package dating.user.v1;

option java_multiple_files = true;
option java_package = "com.dating.hanlian.proto.user.v1";
option java_outer_classname = "UserProto";

service UserService {
  rpc Ping (PingRequest) returns (PingResponse);
}

message PingRequest {
  string message = 1;
}

message PingResponse {
  string message = 1;
}
```

你先不用死记语法，先记住三件事：

1. `service` 定义方法
2. `message` 定义请求和响应
3. `java_package` 决定生成到哪个 Java 包里

---

## 7. proto 怎么变成 Java 代码

我们现在不用手写 gRPC 的底层代码，而是用 Maven 生成。

命令是：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\user
mvn clean install
```

为什么是 `clean install`：

- `clean`：先清掉旧产物，避免看错结果
- `install`：把生成出来的 jar 放到本机 Maven 仓库

本地仓库位置是：

```text
C:\Users\admin\.m2\repository
```

生成后，服务端和网关就可以直接依赖这些包，比如：

```xml
<dependency>
    <groupId>com.dating.hanlian.proto</groupId>
    <artifactId>user-proto</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## 8. 服务端是怎么接 gRPC 的

服务端最关键的是两部分：

1. 实现生成出来的 `ImplBase`
2. 启动一个独立的 gRPC 端口

### 8.1 服务实现

以 `UserGrpcService` 为例：

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

你先记住这个套路：

```text
继承 base class
重写 rpc 方法
返回 response
onNext
onCompleted
```

### 8.2 服务启动

再用一个 `SmartLifecycle` 去启动 gRPC server：

```java
@Component
public class UserGrpcServerLifecycle implements SmartLifecycle {
    // start() 里启动 ServerBuilder
}
```

这样 Spring Boot 启动时，HTTP 和 gRPC 就能一起起来。

---

## 9. 网关是怎么调 gRPC 的

网关这边有三步：

1. 创建 `ManagedChannel`
2. 创建 `BlockingStub`
3. 用 internal check 接口触发调用

### 9.1 channel 是什么

`ManagedChannel` 可以先理解成“连接到某个 gRPC 服务的电话线”。

比如：

```java
ManagedChannelBuilder.forAddress("127.0.0.1", 19081)
```

意思就是连到本机的 `user-service` gRPC 端口。

### 9.2 stub 是什么

`Stub` 可以先理解成“能直接调用远程方法的客户端对象”。

比如：

```java
UserServiceGrpc.newBlockingStub(userServiceManagedChannel)
```

它让你可以像调用本地方法一样去调远程服务。

### 9.3 internal check 接口

网关里我们会放一个专门验证链路的接口，比如：

```text
/internal/check/user-grpc
/internal/check/match-grpc
/internal/check/im-grpc
/internal/check/post-grpc
/internal/check/payment-grpc
```

它们不是正式业务接口，只是给开发阶段验证链路用的。

---

## 10. 为什么我们现在都用固定地址

当前本地验证阶段，我们先写死：

```yaml
host: 127.0.0.1
port: 19081
```

或者其他服务对应端口。

原因很简单：

```text
先把链路跑通，再换成服务发现。
```

我们现在还没有在 gRPC 调用链上正式接 Nacos discovery，所以先用固定地址最稳。

以后会升级成：

```text
固定地址 -> Nacos 服务发现
```

---

## 11. 为什么现在特别要记住 @Qualifier

现在 `mobile-gateway` 里已经有五个 `ManagedChannel`：

```text
userServiceManagedChannel
matchServiceManagedChannel
imServiceManagedChannel
postServiceManagedChannel
paymentServiceManagedChannel
```

它们的类型都是：

```text
io.grpc.ManagedChannel
```

所以 Spring 只看类型时，不知道该选哪个。

这就是为什么我们要写：

```java
@Qualifier("matchServiceManagedChannel")
ManagedChannel matchServiceManagedChannel
```

记住一句话：

```text
多个同类型 Bean，共存时一定要用 @Qualifier 指明。
```

---

## 12. 新服务接入时怎么做

这是最实用的一章。

如果以后你要再接一个新服务，比如 `xxx-service`，就照这个顺序：

1. 先写 `proto/xxx/xxx.proto`
2. 再写 `proto/xxx/pom.xml`
3. 执行 `mvn clean install`
4. 服务端引入 `xxx-proto`
5. 服务端实现 `XxxGrpcService`
6. 服务端实现 `XxxGrpcServerLifecycle`
7. 网关引入 `xxx-proto`
8. 网关写 `XxxGrpcClientConfig`
9. 网关写 `/internal/check/xxx-grpc`
10. 启动后用 `curl` 验证

你可以把它记成一条固定流程：

```text
proto -> 生成包 -> 服务端 -> 网关 -> 验证
```

---

## 13. 常见坑

### 13.1 IDEA 红了，但命令行能编过

常见原因：

- IDEA 没刷新 Maven
- 本地 `match-proto / im-proto / post-proto / payment-proto` 没 install 成功
- IDEA 的 local repository 配错了

先试：

```text
Reload Maven
Invalidate Caches
确认本地仓库是 C:\Users\admin\.m2\repository
```

### 13.2 找不到 proto 依赖

先看本地仓库里有没有对应 jar：

```text
C:\Users\admin\.m2\repository\com\dating\hanlian\proto\<service>-proto\0.1.0
```

没有就先重新：

```powershell
mvn clean install
```

### 13.3 多个 ManagedChannel 报冲突

看到这种错误时：

```text
found 2 beans
```

基本就是没写 `@Qualifier`。

### 13.4 端口占用

如果服务启动失败，先看是不是同一个端口已经被别的进程占了。

我们现在的规则是：

```text
HTTP 端口和 gRPC 端口分开
gRPC 通常比 HTTP 高 1000
```

### 13.5 启动顺序不对

先启动服务端，再启动网关。

正确顺序：

```text
1. xxx-service
2. mobile-gateway
3. curl /internal/check/xxx-grpc
```

---

## 14. 这套模板现在适合怎么看

如果你现在是零基础，建议按这个顺序看：

1. 先看第 2 章，知道 proto 和 gRPC 是什么
2. 再看第 5 章和第 6 章，知道项目里怎么用
3. 再看第 8 章和第 9 章，知道服务端和网关怎么连
4. 再看第 12 章，知道以后自己怎么照着接新服务
5. 最后看第 13 章，知道出错时先查什么

---

## 15. 一句话总结

```text
proto 是约定，gRPC 是执行约定的通信方式。
在这个项目里，我们已经用 proto + gRPC 跑通了五条服务链。
```
