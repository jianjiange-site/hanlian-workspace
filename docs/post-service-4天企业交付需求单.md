# post-service 4 天企业交付型功能开发需求单

> 目标：在 4 天压缩交付周期内，尽量贴近 `post-service` 技术方案完成第一版可联调、可部署、可观测、可继续迭代的微服务。
>
> 当前起点：`post-service` 已有连通性骨架，包含 HTTP/gRPC 端口、Nacos Config/Discovery、PG/Redis/MinIO 检查接口和 `Ping` RPC；业务模型、完整 proto、Flyway、Feed、定时任务等尚未展开。

## 1. 优先级定义

| 优先级 | 含义 |
|---|---|
| P0 | 4 天内必须完成，否则主链路不闭环 |
| P1 | 应尽量完成，影响体验或完整度，但不阻塞核心联调 |
| P2 | 保留设计或二期增强，可延期 |

## 2. 当前项目状态

### 已具备

- `dating-server/post-service` 工程目录已存在。
- HTTP 端口当前为 `18084`。
- gRPC 端口当前为 `19084`。
- Nacos Config / Discovery 已接入。
- PostgreSQL / Redis / MinIO 连通性检查 Controller 已有。
- `proto/post` 工程已存在。
- `PostService` 目前只有 `Ping` RPC。

### 待建设

- 完整 `post.proto`。
- Flyway 业务表和 `shedlock` 表。
- Entity / Mapper / Manager / Service 分层。
- 发帖、查帖、删帖、点赞、评论。
- Redis 写合并与定时刷盘任务。
- Feed 三路混合、冷启动池、好友时间线。
- `UserClient` 调用或临时桩实现。
- `mobile-gateway` 正式业务路由。
- 测试、监控、部署、灰度和风险收口。

## 3. Day 1：协议、数据模型、工程底座

目标：完成业务开发的地基，让后续服务层开发有稳定协议、表结构和基础依赖。

| 工单 | 优先级 | 功能目标 | 交付物 | 验收标准 |
|---|---|---|---|---|
| POST-001 | P0 | 扩展 `proto/post/post.proto` | 完整 `PostService` 协议 | 至少包含发帖、删帖、详情、用户帖子列表、点赞、评论、评论列表、Feed、健康检查 RPC |
| POST-002 | P0 | 更新并发布 `post-proto` | 新版 `post-proto` Maven 依赖 | `post-service` 能引用新 proto 并编译通过 |
| POST-003 | P0 | 补齐 `post-service` 依赖 | `pom.xml` 依赖更新 | MyBatis-Plus、Flyway、Redisson、ShedLock、Caffeine、protobuf util 等依赖齐全 |
| POST-004 | P0 | 初始化数据库结构 | Flyway SQL | `posts`、`post_images`、`post_stats`、`post_likes`、`post_comments`、`shedlock` 建表成功 |
| POST-005 | P0 | 初始化数据访问层 | Entity + Mapper | 5 张业务表都有 Entity + Mapper，`post_likes` 支持 upsert |
| POST-006 | P1 | 统一 Redis key 前缀 | Redis key 配置类 | 所有 key 从配置读取前缀，不在业务代码散落硬编码 |

### 你需要亲自攻坚的技术点

- 完整 `post.proto` 协议设计。
- Flyway 初始化 SQL。
- `post_likes` 的 `ON CONFLICT` 幂等 upsert。
- MyBatis-Plus 单表 Mapper。
- Redis key prefix 统一治理。

## 4. Day 2：核心业务闭环

目标：完成帖子和互动的核心读写闭环，保证业务主链路可以通过 REST/gRPC 开始联调。

| 工单 | 优先级 | 功能目标 | 交付物 | 验收标准 |
|---|---|---|---|---|
| POST-007 | P0 | 发帖 | `PostWriteService.createPost` | 写入 `posts`、`post_images`、`post_stats`，图片只存 `image_key` |
| POST-008 | P0 | 帖子详情 | `PostReadService.getPostDetail` | 返回正文、图片、点赞数、评论数、当前用户是否点赞 |
| POST-009 | P0 | 删除帖子 | `PostWriteService.deletePost` | 仅作者可删除，逻辑删除，Feed 读到删除帖时跳过 |
| POST-010 | P0 | 点赞/取消点赞 | `LikeService` | `post_likes` 幂等更新，Redis 增量计数 |
| POST-011 | P0 | 评论发布 | `CommentService.createComment` | 写入 `post_comments`，Redis ZSet 缓存最新 200 条 |
| POST-012 | P0 | 评论列表 | `CommentService.listComments` | 优先读 Redis，缺失回源 DB |
| POST-013 | P1 | 我的帖子列表 | 游标分页接口 | 按 `user_id + post_id desc` 查询用户帖子 |
| POST-014 | P1 | 统一异常模型 | `BizException` / `ErrorCode` / 全局异常处理 | REST/gRPC 都能返回清晰错误 |

### 你需要亲自攻坚的技术点

- `PostWriteService`、`PostReadService`、`LikeService`、`CommentService` 的事务边界。
- DB 基准值 + Redis 增量合并读取。
- 点赞/取消点赞幂等。
- 评论 ZSet 缓存和裁剪。
- 删除帖子后的读侧跳过逻辑。

## 5. Day 3：gRPC、Feed、定时任务、gateway 联调

目标：把核心业务暴露为正式 gRPC 能力，并补齐 Feed、刷盘和跨服务调用。

| 工单 | 优先级 | 功能目标 | 交付物 | 验收标准 |
|---|---|---|---|---|
| POST-015 | P0 | 完整实现 `PostGrpcService` | 业务 RPC 实现 | gateway 或 grpcurl 能调用核心业务 RPC |
| POST-016 | P0 | 实现 `UserClient` 桩 | `UserClient` | `getFriendUserIds` 先返回空，`isMale` 可用 `userId % 2` 临时兜底 |
| POST-017 | P0 | 点赞刷盘 | `LikeFlushJob` | Redis 点赞增量定时刷入 `post_stats` |
| POST-018 | P0 | 评论计数刷盘 | `CommentFlushJob` | 评论增量定时刷入 `post_stats` |
| POST-019 | P0 | 推荐池重建 | `FeedScoreJob` | 每 5 分钟重建推荐池和冷启动池 |
| POST-020 | P0 | Feed 三路混合 | `FeedService` | recommend + friend + cold_start 按文档位置混排 |
| POST-021 | P1 | 好友时间线写扩散 | `PostFanoutService` | 发帖后异步写入好友 timeline |
| POST-022 | P1 | gateway 接入业务路由 | `/post/*` 路由 | gateway 注入 `user_id`，转发到 post-service gRPC |
| POST-023 | P1 | Feed 已读去重 | BloomFilter | Feed 读取后写入用户已读 BloomFilter |

### 你需要亲自攻坚的技术点

- Redis ZSet 池设计。
- Hacker News 热度分计算。
- Feed 固定位置混排规则。
- ShedLock 防止多实例重复执行 Job。
- user-service 不可用时的降级策略。
- `@Transactional` 内不嵌套远程调用。

## 6. Day 4：部署、测试、监控、灰度、风险收口

目标：完成可交付闭环，确保服务能部署、能验证、能定位问题、能处理常见故障。

| 工单 | 优先级 | 功能目标 | 交付物 | 验收标准 |
|---|---|---|---|---|
| POST-024 | P0 | 本地完整联调 | 冒烟链路 | 发帖 -> 点赞 -> 评论 -> 刷盘 -> Feed 可跑通 |
| POST-025 | P0 | 容器化接入 | Dockerfile / compose 配置 | 容器能启动并注册到 Nacos |
| POST-026 | P0 | Jenkins 构建接入 | Jenkins 构建项 | 能 `mvn package`、build image、部署 |
| POST-027 | P0 | Nacos 配置核对 | `post-service.yaml` | PG、Redis、MinIO、Snowflake、Redis prefix 都从配置读取 |
| POST-028 | P1 | 关键日志补齐 | 业务日志 | create、like、comment、feed、flush、rebuild 都有可 grep 日志 |
| POST-029 | P1 | 健康检查 | Actuator health | `/actuator/health` 可用于部署检查 |
| POST-030 | P1 | 冒烟测试清单 | grpcurl / Postman / gateway 验证清单 | 三套验证路径可复现 |
| POST-031 | P1 | 失败恢复手册 | 运维处理说明 | 点赞刷盘失败、Feed 重建失败、Redis key 膨胀、DB 计数不一致有处理方案 |
| POST-032 | P2 | 集成测试 | Testcontainers 测试 | PG + Redis 端到端测试，可后续补 |
| POST-033 | P2 | 指标体系 | Prometheus 指标 | 先保留设计，后续接入 |

## 7. 4 天内必须守住的 P0 主链路

- 完整 proto。
- Flyway 表结构。
- 发帖。
- 查帖。
- 删帖。
- 点赞/取消点赞。
- 评论/评论列表。
- gRPC 暴露。
- gateway 可联调。
- Redis 计数增量。
- 定时刷盘。
- 基础 Feed。
- Nacos 配置。
- Docker/Jenkins 可部署。

## 8. 建议延期但保留设计的 P2

- 楼中楼评论。
- IM 通知。
- 敏感词审核。
- 图片 NSFW 审核。
- 完整 Prometheus 指标。
- Testcontainers 全量集成测试。
- 灰度发布自动化。
- 复杂黑名单/拉黑过滤。

## 9. 第 4 天企业验收演示链路

第 4 天结束时，至少需要能演示以下链路：

```text
1. gateway 携带用户身份调用 post-service
2. 用户发帖，带 0~9 张 image_key
3. 用户查看帖子详情
4. 另一个用户点赞、取消点赞
5. 用户评论
6. 定时任务把 Redis 增量刷到 PG
7. 用户刷新 Feed，能看到推荐/冷启动内容
8. 删除帖子后，详情不可见，Feed 自动跳过
9. 服务通过 Docker 启动，Nacos 注册正常
10. 日志能定位 create / like / comment / feed / flush
```

## 10. 风险与优化建议

### Feed 体验兜底

技术方案已有热门池、好友时间线、冷启动池、性别分桶和 Bloom 去重，但还需要在实现时明确：

- 新用户第一次进来看到什么。
- 没有好友时 Feed 如何不空。
- 连续刷新是否允许短时间重复。
- 用户自己刚发的帖子是否让自己优先看到。
- 删除或审核中的帖子在 Feed 中如何跳过。

### 评论范围边界

第一版建议明确只交付一级评论。`root_id`、`parent_id`、`reply_to_user_id` 字段保留，但楼中楼不作为 P0 交付范围，避免接口、排序、删除、计数和通知半完成。

### 一致性与恢复能力

需要补齐可运维处理手段：

- 手动触发点赞刷盘。
- 手动重建 Feed 池。
- `post_stats` 重算任务。
- Redis key 巡检。
- `updated_set` 长度报警。
- Feed 池重建失败时继续使用旧池。

## 11. 后续执行建议

建议开发顺序从 `POST-001 ~ POST-005` 开始，也就是完整 proto、表结构、Mapper 和基础工程依赖。这个阶段决定后面所有业务代码的稳定性，优先打牢。

