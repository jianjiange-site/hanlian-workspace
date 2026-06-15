# docs

这里存放 `hanlian-workspace` 的核心工程文档、接入说明、技术方案和阶段性时间线记录。

## 目录约定

```text
docs/
  README.md
  gRPC服务接入模板.md
  Nacos-Config-服务配置说明.md
  post-service-Nacos-Config-配置说明.md
  小白看懂proto和gRPC.md
  timeline/
    2026-06-13-user-service-PG-Redis-接入复盘.md
    2026-06-14-mobile-gateway-user-service-gRPC-调用链复盘.md
    2026-06-14-mobile-gateway-match-service-gRPC-调用链复盘.md
    2026-06-14-mobile-gateway-im-service-gRPC-调用链复盘.md
    2026-06-14-mobile-gateway-post-service-gRPC-调用链复盘.md
    2026-06-14-mobile-gateway-payment-service-gRPC-调用链复盘.md
    2026-06-14-当前工程骨架状态总览.md
```

## 文档分类

### 核心文档

核心文档放在 `docs/` 根目录，适合长期保留和反复查阅：

- `gRPC服务接入模板.md`：已验证的 gRPC server/client 接入模板，后续服务可按此复制。
- `Nacos-Config-服务配置说明.md`：各服务接入 Nacos Config 的统一约定、Data ID 命名、环境变量边界和配置模板。
- `post-service-Nacos-Config-配置说明.md`：`post-service` 从 Nacos Config 读取 PostgreSQL、Redis、MinIO 配置的说明。
- `post-service-4天企业交付需求单.md`：`post-service` 4 天压缩交付周期的功能工单、优先级、验收标准和风险收口。
- `小白看懂proto和gRPC.md`：面向 proto / gRPC 零基础的说明文档，包含项目里的实际用法和排错思路。

### 时间线文档

每日复盘、阶段状态、当天做了哪些事的记录，统一放在 `docs/timeline/`：

- `2026-06-13-user-service-PG-Redis-接入复盘.md`
- `2026-06-14-mobile-gateway-user-service-gRPC-调用链复盘.md`
- `2026-06-14-mobile-gateway-match-service-gRPC-调用链复盘.md`
- `2026-06-14-mobile-gateway-im-service-gRPC-调用链复盘.md`
- `2026-06-14-mobile-gateway-post-service-gRPC-调用链复盘.md`
- `2026-06-14-mobile-gateway-payment-service-gRPC-调用链复盘.md`
- `2026-06-14-当前工程骨架状态总览.md`

## 维护规则

- `docs/` 根目录只放长期有参考价值的核心文档、技术方案、接入模板和总览说明。
- `docs/timeline/` 只放带日期的过程记录、复盘、阶段状态和当天工作记录。
- 新增时间线文档命名格式建议为 `YYYY-MM-DD-主题.md`。
- 文档里不写真实密码、token、AK/SK。
- 具体密钥只放本机环境变量、IDE Run Configuration、Nacos 配置或后续 Secret 管理系统。
