# match-proto

`match-proto` 用来从 `match.proto` 生成 Java gRPC 代码。

## 为什么要有这个模块

`match-service` 和 `mobile-gateway` 都需要使用同一份 gRPC 契约。

如果各服务自己复制 `.proto` 文件，后续很容易出现版本不一致；所以这里先把 proto 生成成一个本地 Maven 包，再由业务服务依赖它。

## 本地生成命令

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\match
mvn clean install
```

当前还没有 Nexus 凭据，所以只做本地 `install`，不要执行 `deploy`。
