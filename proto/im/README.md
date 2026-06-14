# im-proto

`im-proto` 用来从 `im.proto` 生成 Java gRPC 代码。

## 为什么要有这个模块

`im-service` 和 `mobile-gateway` 都需要使用同一份 gRPC 契约。

后续真实 OpenIM 注册、发消息、会话编排可以继续扩展这份 proto；当前只保留最小 `Ping`，用于验证工程链路。

## 本地生成命令

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\im
mvn clean install
```

当前还没有 Nexus 凭据，所以只做本地 `install`，不要执行 `deploy`。
