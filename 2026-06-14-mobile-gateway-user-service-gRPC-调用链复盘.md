# 2026-06-14 mobile-gateway -> user-service gRPC 调用链复盘

## 1. 文档目的

这份文档记录今天完成的第一条服务间 gRPC 调用链：

```text
浏览器 / curl
  -> mobile-gateway HTTP 8080
  -> mobile-gateway gRPC client
  -> user-service gRPC 19081
  -> UserService/Ping
  -> 返回 pong
```

这条链路的意义是：

- 证明 `proto/` 不是只放了文件，而是真的能生成 Java stub。
- 证明 `user-service` 可以实现生成出来的 gRPC 服务端。
- 证明 `mobile-gateway` 可以通过生成出来的 gRPC client 调用 `user-service`。
- 给后续 `gateway -> match-service`、`match-service -> user-service` 等链路提供模板。

这次仍然只做工程骨架，不进入真实业务表和复杂业务逻辑。

---

## 2. 本次完成内容

本次已经完成：

- `proto/user` 增加 Java 生成模块。
- `proto/user` 本地执行 `mvn clean install` 成功。
- `user-service` 引入本地 `user-proto:0.1.0`。
- `user-service` 启动一个最小 gRPC server，端口 `19081`。
- 使用 `grpcurl` 调用 `UserService/Ping` 成功。
- `mobile-gateway` 引入本地 `user-proto:0.1.0`。
- `mobile-gateway` 增加 gRPC client，连接 `127.0.0.1:19081`。
- `mobile-gateway` 暴露 `/internal/check/user-grpc`，通过 HTTP 触发 gRPC 调用。
- 浏览器访问 `http://localhost:8080/internal/check/user-grpc` 成功。

已提交记录：

```text
chore: add user proto java generation
feat: add user-service grpc server
feat: add gateway user grpc check
```

---

## 3. 为什么要先做这条链路

根据项目规范，服务间调用应该走：

```text
gRPC + Protobuf
```

不应该让服务之间直接用 HTTP 互调，也不应该直接访问别的服务数据库。

所以在真正写 `match-service` 业务之前，必须先证明：

```text
一个 proto 可以生成 Java 包
一个服务可以实现这个 proto
另一个服务可以通过生成 stub 调用它
```

这次选择 `mobile-gateway -> user-service`，原因是：

- `mobile-gateway` 是 App / 前端请求入口。
- `user-service` 是用户域核心服务。
- 这条链路最简单，适合作为第一条模板。
- 后续 match、im、payment 都可以复用同一套思路。

---

## 4. proto/user Java 生成

相关目录：

```text
proto/user
```

关键文件：

```text
proto/user/pom.xml
proto/user/src/main/proto/user.proto
proto/user/README.md
```

生成命令：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\user
mvn clean install
```

为什么是 `mvn clean install`：

- `clean`：清掉旧的生成产物，避免误判。
- `install`：把生成后的 jar 安装到本机 Maven 仓库。
- 当前没有 Nexus 凭据，所以不做 `deploy`。

本地安装位置：

```text
C:\Users\admin\.m2\repository\com\dating\hanlian\proto\user-proto\0.1.0
```

生成出来的核心类：

```text
PingRequest.java
PingResponse.java
UserServiceGrpc.java
```

其中：

- `PingRequest` 是请求结构。
- `PingResponse` 是响应结构。
- `UserServiceGrpc` 是服务端和客户端都会用到的 gRPC 骨架。

---

## 5. user-service gRPC 服务端

相关文件：

```text
dating-server/user-service/pom.xml
dating-server/user-service/src/main/resources/application.yml
dating-server/user-service/src/main/java/com/jianjiange/dating/user/grpc/UserGrpcService.java
dating-server/user-service/src/main/java/com/jianjiange/dating/user/grpc/UserGrpcServerLifecycle.java
```

新增依赖：

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

为什么要引入 `user-proto`：

```text
user-service 需要实现 proto 生成出来的 UserServiceGrpc.UserServiceImplBase。
```

为什么要引入 `grpc-netty-shaded`：

```text
user-proto 只提供接口和 stub，不负责真正启动网络服务。
grpc-netty-shaded 提供 gRPC server 的网络运行能力。
```

配置：

```yaml
grpc:
  server:
    port: 19081
```

为什么 gRPC 用 `19081`：

```text
user-service 原来的 HTTP 端口是 18081。
gRPC 单独使用 19081，避免 HTTP 和 gRPC 抢同一个端口。
```

启动成功日志：

```text
user-service gRPC server started on port 19081
```

---

## 6. grpcurl 本地验证

下载位置：

```text
C:\tmp\grpcurl\grpcurl.exe
```

版本：

```text
grpcurl.exe v1.9.3
```

调用命令：

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/user/src/main/proto/user.proto -d '@' localhost:19081 dating.user.v1.UserService/Ping
```

成功返回：

```json
{
  "message": "user-service pong: hello"
}
```

为什么要带 `-proto`：

```text
当前 gRPC server 没有开启 reflection。
grpcurl 不知道服务有哪些方法，所以需要把本地 user.proto 传给它。
```

为什么使用 `-d '@'`：

```text
PowerShell 里 JSON 双引号容易被吃掉。
把 JSON 通过管道传给 grpcurl，再用 -d '@' 读取标准输入，最稳。
```

---

## 7. mobile-gateway gRPC 客户端

相关文件：

```text
dating-server/mobile-gateway/pom.xml
dating-server/mobile-gateway/src/main/resources/application.yml
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/client/UserGrpcClientConfig.java
dating-server/mobile-gateway/src/main/java/com/jianjiange/dating/gateway/UserGrpcCheckController.java
```

新增依赖：

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

配置：

```yaml
user-service:
  grpc:
    host: 127.0.0.1
    port: 19081
```

为什么现在先写 `127.0.0.1:19081`：

```text
今天目标是最小调用链验证。
先用固定本机地址，减少变量。
后续再替换成 Nacos 服务发现。
```

`UserGrpcClientConfig` 的作用：

```text
创建一个可复用的 ManagedChannel。
创建一个 UserServiceBlockingStub。
```

为什么 channel 要复用：

```text
gRPC channel 是长连接资源。
每次请求都新建 channel 会浪费连接和线程资源。
```

`UserGrpcCheckController` 的作用：

```text
暴露 HTTP 接口 /internal/check/user-grpc。
浏览器或 curl 请求这个接口时，gateway 内部会调用 user-service 的 gRPC Ping。
```

---

## 8. 本地启动验证方式

### 8.1 启动 user-service

确认环境变量：

```powershell
echo $env:POSTGRES_PASSWORD
echo $env:REDIS_PASSWORD
echo $env:NACOS_PASSWORD
```

启动：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server\user-service
mvn spring-boot:run
```

确认日志：

```text
Tomcat started on port 18081
user-service gRPC server started on port 19081
Started UserServiceApplication
```

### 8.2 启动 mobile-gateway

可以用 IDEA 启动，也可以用 PowerShell。

PowerShell 方式：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server\mobile-gateway
$env:NACOS_PASSWORD="从开发环境接入文档复制 Nacos 密码"
mvn spring-boot:run
```

IDEA 方式：

```text
Run Configuration 里添加环境变量：
NACOS_PASSWORD=从开发环境接入文档复制 Nacos 密码
```

### 8.3 验证 gateway 调 user-service

浏览器访问：

```text
http://localhost:8080/internal/check/user-grpc
```

或 PowerShell：

```powershell
curl http://localhost:8080/internal/check/user-grpc
```

成功返回：

```json
{"userGrpc":"ok","gateway":"ok","message":"user-service pong: hello from mobile-gateway"}
```

说明链路已经打通：

```text
HTTP -> mobile-gateway -> gRPC -> user-service
```

---

## 9. 本次排障记录

### 9.1 user-service 启动失败：18081 被占用

现象：

```text
Web server failed to start. Port 18081 was already in use.
```

含义：

```text
已经有旧的 user-service 或其他进程占用了 18081。
```

排查：

```powershell
netstat -ano | findstr :18081
```

处理：

```powershell
taskkill /PID 进程号 /F
```

### 9.2 grpcurl JSON 参数报错

现象：

```text
invalid character 'm' looking for beginning of object key string
```

原因：

```text
PowerShell 把 JSON 双引号处理坏了。
```

解决：

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/user/src/main/proto/user.proto -d '@' localhost:19081 dating.user.v1.UserService/Ping
```

### 9.3 为什么这次没有使用 Nacos 发现 gRPC 地址

当前只做最小调用链，所以先写：

```text
127.0.0.1:19081
```

后续要演进为：

```text
mobile-gateway -> Nacos 查询 user-service 实例 -> 读取 gRPC 端口 -> 创建 channel -> 调用
```

---

## 10. 当前完成状态

已经完成：

- [x] `proto/user` Java stub 生成。
- [x] `proto/user` 本地 `mvn clean install`。
- [x] `user-service` 引入 `user-proto`。
- [x] `user-service` 暴露 gRPC server `19081`。
- [x] `grpcurl` 直连 `user-service` 验证成功。
- [x] `mobile-gateway` 引入 `user-proto`。
- [x] `mobile-gateway` 通过 gRPC 调用 `user-service`。
- [x] 浏览器验证 `/internal/check/user-grpc` 成功。

---

## 11. 当前限制

当前还没有做：

- gRPC 服务发现。
- gRPC 负载均衡。
- gRPC 超时配置。
- gRPC traceId 透传。
- gRPC 统一异常映射。
- common-starter 封装。
- 真实用户业务 RPC。

这些先不急。今天的重点是“骨架能跑、链路能通、文档可复盘”。

---

## 12. 下一步建议

下一步建议先做工程骨架层面的收口，而不是立刻建业务表：

1. 写一份当前工程骨架状态总览。
2. 在 `proto/README.md` 里补充当前 Java 生成路线。
3. 把 `user-service` / `mobile-gateway` 这套 gRPC 模式整理成后续服务复制模板。
4. 后续再选择是否进入 `match-service` 骨架复制，而不是马上写复杂匹配业务。

