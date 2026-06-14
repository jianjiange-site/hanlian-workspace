# docs

这里存放 `hanlian-workspace` 的工程复盘、接入说明和阶段性状态文档。

根目录只保留工程入口文件和核心代码目录：

```text
ai-chat/
dating-server/
proto/
README.md
.gitignore
```

## 当前文档

```text
2026-06-13-user-service-PG-Redis-接入复盘.md
```

记录 `user-service` 接入 PostgreSQL / Redis 的配置、环境变量、验证接口和排障过程。

```text
2026-06-14-mobile-gateway-user-service-gRPC-调用链复盘.md
```

记录 `mobile-gateway -> user-service` 第一条 gRPC 调用链，包括 proto 生成、gRPC server、grpcurl 和 gateway check 接口。

```text
2026-06-14-mobile-gateway-match-service-gRPC-调用链复盘.md
```

记录 `mobile-gateway -> match-service` 第二条 gRPC 调用链，包括 `match-proto` 生成、`match-service` gRPC server、gateway check 接口，以及 IDEA Maven 和多 `ManagedChannel` 注入排障。

```text
2026-06-14-mobile-gateway-im-service-gRPC-调用链复盘.md
```

记录 `mobile-gateway -> im-service` 第三条 gRPC 调用链，包括 `im-proto` 生成、`im-service` gRPC server、gateway check 接口，以及多 `ManagedChannel` 的 `@Qualifier` 约定。

```text
2026-06-14-当前工程骨架状态总览.md
```

记录当前工程骨架全局状态，方便后续判断哪些能力已经完成、哪些还只是占位。

```text
gRPC服务接入模板.md
```

记录当前已验证的 gRPC server/client 接入方式，后续服务可按此模板复制。

## 约定

- 复盘文档、接入说明、阶段总结放在 `docs/`。
- 根目录 `README.md` 保留为项目入口说明。
- 文档里不写真实密码、token、AK/SK。
- 具体密码只放本机环境变量、IDE Run Configuration 或后续 Secret/Nacos 配置。
