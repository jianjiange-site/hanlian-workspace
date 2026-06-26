# AI 开工前必读

> 用途：这是一份给 AI 看的项目级开工导航。  
> 目标：新对话开始后，AI 读完本文档，应能理解项目资料在哪里、先看什么、怎么判断当前进度、怎么和用户协作、接口验收文档应该怎么写。  
> 注意：本文档不是具体服务技术方案。具体需求必须继续阅读 `docs/design` 和 `docs/design-detail`。

---

## 1. 项目基本信息

项目路径：

```text
E:\heart-dev\workspace\hanlian-workspace
```

主要目录：

```text
hanlian-workspace
├── dating-server
│   ├── common
│   ├── mobile-gateway
│   ├── user-service
│   ├── post-service
│   ├── match-service
│   ├── im-service
│   └── payment-service
├── proto
│   ├── user
│   ├── post
│   ├── match
│   ├── im
│   └── payment
└── docs
    ├── design
    ├── design-detail
    └── timeline
```

目录约定：

| 目录 | 作用 |
|---|---|
| `dating-server/{service-name}` | Java 微服务代码 |
| `dating-server/common` | 公共 Java 模块 |
| `proto/{service-name}` | proto 文件和生成代码模块 |
| `docs/design` | 技术方案、需求设计、服务设计 |
| `docs/design-detail` | 接口验收文档、优化清单、成熟产品差距分析 |
| `docs/timeline` | 阶段复盘、历史问题、接入记录 |

---

## 2. AI 开工前必须先读什么

AI 不要直接猜需求，也不要直接开始改代码。先按顺序阅读资料。

### 2.1 先确认当前服务

先判断用户当前要开发哪个服务，例如：

```text
user-service
post-service
mobile-gateway
match-service
im-service
payment-service
```

如果用户没有说清楚，先从最近对话和代码改动判断；仍不确定时再问用户。

### 2.2 必读文档顺序

针对某个服务开工时，优先读：

```text
docs/design/{service-name} 相关技术方案
docs/design-detail/{service-name} 相关接口验收文档
docs/design-detail/{service-name} 相关优化清单
docs/timeline 里和该服务相关的复盘
proto/{service-name}/src/main/proto
dating-server/{service-name}
```

如果没有完全对应的文件名，就用搜索找：

```powershell
rg "user-service|UserIdentity|ResolveOrCreate" docs
rg "post-service|CreatePost|Feed" docs
```

### 2.3 开工前必须判断的问题

AI 读完文档和代码后，要先判断：

- 当前服务做到 P0 / P1 / P2 哪一步。
- 技术方案里哪些已经完成，哪些没完成。
- proto 是否已经定义。
- proto 是否已经安装到本地 Maven 仓库。
- 数据库 migration 是否已经有。
- Flyway 是否已经执行过。
- HTTP debug 接口是否存在。
- gRPC 服务是否已经注册。
- 是否已有接口验收文档。
- 是否已有 Postman collection。
- 当前代码和文档有没有明显不一致。

---

## 3. 用户协作偏好

用户的学习方式：

- 用户想自己写代码。
- AI 先讲思路和步骤。
- 不要直接替用户写代码，除非用户明确说“你来改”。
- 每一步都解释为什么。
- 把用户当代码小白，但不要太啰嗦。
- 给代码时尽量给完整方法或完整类，不要只给碎片。
- 解释要写在代码外。
- 不要在代码里写注释，除非用户明确要求。

如果用户说：

```text
你来改
交给你做
开始吧
帮我完成
```

AI 可以直接改代码，但仍然要先说明准备改哪些文件和为什么。

---

## 4. AI 回答方式要求

回答尽量按这个结构：

```text
1. 下一步做什么
2. 为什么现在做这一步
3. 具体怎么做
4. 涉及哪些文件
5. 方法 / 参数 / 返回值解释
6. 怎么验证
7. 当前服务完成进度
```

如果只是排查问题，优先回答：

```text
1. 真正错误是什么
2. 哪些不是根因
3. 下一条最小排查命令
4. 根据结果再继续
```

不要只说“建议测试一下”。要给具体命令、预期结果、失败时怎么看。

---

## 5. 修改代码前规则

AI 修改代码前必须：

1. 先读相关代码。
2. 先确认影响范围。
3. 先告诉用户准备改哪些文件。
4. 用户确认或明确授权后再改。
5. 不要改无关文件。
6. 不要覆盖用户未提交改动。
7. 不要把未实现功能写成已完成。
8. 改完必须编译或给出无法编译的原因。

常用验证命令：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl {service-name} -am -DskipTests package
```

如果改了 `common`，且要在某个服务目录单独启动服务，需要先执行：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl common install -DskipTests
```

启动单个服务示例：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server\user-service
mvn spring-boot:run
```

---

## 6. P0 / P1 / P2 判断规则

通用判断：

| 优先级 | 含义 |
|---|---|
| P0 | 核心主链路能跑通，能创建、查询、校验、返回正确结果 |
| P1 | 幂等、异常、缓存、锁、文档、验收、基础优化 |
| P2 | 成熟产品增强、风控、性能、高可用、运营能力、复杂策略 |

示例：

```text
user-service P0:
- ResolveOrCreateByPhone
- ResolveOrCreateByDevice
- CheckBan
- 业务 user_id
- 数据库 migration
- gRPC 异常处理
- 接口验收文档
```

```text
post-service P0:
- 发帖
- 详情
- 删除
- 点赞 / 取消点赞
- 评论 / 评论列表
- 用户帖子列表
- Feed 基础推荐
- Redis 计数增量
- 接口验收文档
```

AI 需要结合 `docs/design` 里的技术方案和当前代码判断，不要机械套模板。

---

## 7. 接口验收文档标准

接口验收文档不是纯交付文档，而是：

```text
验收文档 + 简洁学习笔记
```

用途：

- 给用户自己测试用。
- 帮用户记忆和理解每个接口。
- 后续新 AI 可以快速理解当前服务完成了什么。

建议放在：

```text
docs/design-detail/{service-name}-接口验收文档.md
```

如果用户指定其他位置，以用户要求为准。

### 7.1 必须覆盖范围

接口验收文档要覆盖：

- HTTP debug 接口。
- gRPC 方法。
- HTTP 部分包含 Postman 里的测试内容。
- 数据库验收项。
- Redis 验收项。
- 异常验收项。
- 关键设计记忆点。
- 当前实现与技术方案差异。

### 7.2 每个接口必须包含什么

每个接口建议包含：

```text
1. 接口解决什么问题
2. HTTP 请求路径、请求方式、请求参数 / 请求体
3. HTTP 返回示例
4. 对应的 gRPC 方法
5. proto 请求字段和响应字段
6. 完整代码链路
7. 数据库验收项
8. Redis 验收项
9. 异常验收项
10. 关键设计记忆点
```

代码链路格式：

```text
Controller / Grpc
  -> Service
     -> Manager
        -> Mapper
           -> DB / Redis / Job
```

如果某个服务没有 HTTP 业务接口，只写 HTTP debug 接口，并明确说明：

```text
当前没有业务 HTTP 接口，HTTP 只做 debug 连通性检查。
```

如果某个接口没有 Redis 参与，也要写明：

```text
当前链路不依赖 Redis。
```

不要把未实现功能写成已完成。

### 7.3 文档风格

文档要：

- 简洁一点。
- 不放太多代码。
- 重点写测试命令、预期结果、链路、验收点。
- 提醒用户去 IDEA 里看关键文件。
- 不要写成长篇源码讲解。

---

## 8. AI 不能偷懒的地方

AI 不要这样做：

- 不读 `docs/design` 就开始写代码。
- 不看当前代码就凭记忆回答。
- 只给抽象建议，不给具体命令。
- 只写代码，不解释方法作用、参数、返回值。
- 跳过数据库验收。
- 跳过 Redis 验收。
- 跳过异常验收。
- 把 P1 / P2 功能说成 P0 已完成。
- 随便重构不相关代码。
- 覆盖用户未提交改动。
- 在代码里加很多注释。
- 把敏感密码写进文档或提交到 GitHub。

尤其注意：

```text
用户经常把数据库、Redis、Nacos 密码放在 Nacos 配置中心。
不要要求用户把密码写进本地配置文件。
如果用户贴出密码，提醒不要提交到 GitHub。
```

---

## 9. 常见技术约定

### 9.1 包名

后端 Java 包名使用：

```text
com.aurora.dating.{service}
```

例如：

```text
com.aurora.dating.user
com.aurora.dating.post
```

### 9.2 Maven groupId

后端 Maven groupId 使用：

```text
com.aurora.dating
```

### 9.3 proto groupId

proto 模块当前可能仍使用：

```text
com.dating.hanlian.proto
```

不要未经确认就改 proto 坐标。

### 9.4 业务 ID

真实对外业务 ID 应优先使用雪花 ID。

当前公共雪花 ID 在：

```text
dating-server/common/src/main/java/com/aurora/dating/common/id
```

各服务通过配置区分 workerId：

```yaml
aurora:
  id:
    datacenter-id: 1
    worker-id: 1
```

不要让多个服务共用同一个 `worker-id`。

---

## 10. AI 每次开工模板

AI 每次开始一个服务的开发时，建议按这个流程：

```text
1. 先确认当前服务和当前目标。
2. 阅读 docs/design 里的对应技术方案。
3. 阅读 docs/design-detail 里的接口验收文档 / 优化文档。
4. 阅读 docs/timeline 里的相关复盘。
5. 阅读 proto。
6. 阅读 dating-server/{service-name} 当前代码。
7. 判断 P0 / P1 / P2 当前进度。
8. 给用户说明下一步应该做什么，以及为什么。
9. 如果用户要自己写代码，先给步骤和完整方法。
10. 如果用户说“你来改”，先说明改动范围，再动手。
11. 改完编译验证。
12. 指导用户做 HTTP / gRPC / DB / Redis / 异常验收。
13. 更新接口验收文档或提醒用户更新。
```

---

## 11. 给 AI 的第一句话模板

用户以后可以这样开新对话：

```text
我要继续开发 hanlian-workspace 项目。
请先阅读 docs/AI-开工前必读.md。
当前要开发的是 {service-name}。
请你先看 docs/design 和 docs/design-detail 里的相关文档，再看代码，然后告诉我下一步应该做什么。
```

AI 收到后不要直接写代码，先完成阅读和判断。

---

## 12. 当前已知进度快照

这个快照只用于帮助 AI 初步定位，最终仍以代码和文档为准。

### post-service

大体已完成：

- 发帖、详情、删除。
- 点赞 / 取消点赞。
- 评论、评论列表。
- 用户帖子列表。
- Feed 冷启动池、推荐池、已看过滤、混排。
- Redis 计数增量 + 定时刷盘。
- HTTP / gRPC 异常处理。
- Postman 测试文档。
- 接口验收文档。
- 成熟产品差距与优化清单。

### user-service

当前 P0 身份链路已完成：

- `UserIdentityService` proto。
- gRPC 服务注册。
- `ResolveOrCreateByPhone`。
- `ResolveOrCreateByDevice`。
- `CheckBan`。
- 业务 `user_id`。
- common 雪花 ID。
- Flyway 身份表迁移。
- 参数校验。
- gRPC 异常转换。
- 接口验收文档。

仍未完成或待后续：

- `ResolveOrCreateByThirdParty`。
- 用户资料 Profile。
- 头像 presign / confirm。
- 兴趣、标签、资料审核。
- Redisson 注册锁。
- 封禁 Redis 短缓存。
- mobile-gateway 登录链路正式接入。

---

## 13. 最重要的一句话

AI 开工前先读文档、再看代码、再判断进度，最后给下一步。  
不要跳过用户的学习节奏，也不要把“能跑”误判成“已经企业级完成”。
