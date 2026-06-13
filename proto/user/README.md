# user-proto Java 生成说明

## 1. 这是什么

`user-proto` 是 `user-service` 的 gRPC 契约生成模块。

它的输入是：

```text
src/main/proto/user.proto
```

它的输出是 Java 代码和一个本地 Maven jar：

```text
target/generated-sources/protobuf/java
target/generated-sources/protobuf/grpc-java
target/user-proto-0.1.0.jar
```

---

## 2. 为什么要生成 Java 代码

`.proto` 文件本身只是接口定义，Java 服务不能直接调用它。

需要通过 Maven 插件把 `.proto` 生成 Java 代码：

```text
message PingRequest  -> PingRequest.java
message PingResponse -> PingResponse.java
service UserService  -> UserServiceGrpc.java
```

这样后续 `user-service` 可以实现 `UserServiceGrpc`，`mobile-gateway` 可以通过生成出来的 stub 调用 `user-service`。

---

## 3. 为什么先本地 install

规范里要求 proto 包最终发布到 Nexus。

但当前还没有 Nexus 凭据，所以先做本地验证：

```text
mvn clean install
```

这一步会把 jar 安装到本机 Maven 仓库：

```text
C:\Users\admin\.m2\repository\com\dating\hanlian\proto\user-proto\0.1.0
```

后续本机的 Java 服务可以先用这个本地 jar 验证调用链。

---

## 4. 怎么生成

进入目录：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\user
```

执行：

```powershell
mvn clean install
```

成功时会看到：

```text
BUILD SUCCESS
```

并且日志里会出现：

```text
Compiling 1 proto file(s) to ...\target\generated-sources\protobuf\java
Compiling 1 proto file(s) to ...\target\generated-sources\protobuf\grpc-java
Installing ...\user-proto-0.1.0.jar to C:\Users\admin\.m2\repository\...
```

---

## 5. 当前已验证生成的类

当前 `user.proto` 会生成：

```text
PingRequest.java
PingRequestOrBuilder.java
PingResponse.java
PingResponseOrBuilder.java
UserProto.java
UserServiceGrpc.java
```

说明：

- `PingRequest.java` / `PingResponse.java` 是请求和响应数据结构。
- `UserServiceGrpc.java` 是 gRPC 服务端和客户端都会用到的骨架类。
- `UserProto.java` 是 protoc 生成的外层描述类。

---

## 6. 不要提交什么

不要提交：

```text
target/
```

原因：

```text
target/ 是 Maven 生成产物，每个人本地都可以重新生成。
Git 只需要提交 pom.xml 和 src/main/proto/user.proto。
```

---

## 7. 当前限制

当前只完成了：

```text
user proto -> Java stub -> 本地 Maven install
```

还没有完成：

- 发布到 Nexus。
- `user-service` 引入 `user-proto`。
- `user-service` 实现 gRPC 服务端。
- `mobile-gateway` 调用 `user-service`。

