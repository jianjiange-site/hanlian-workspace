# 2026-06-17 post-service P0 开发记录

## 1. 今日目标

今天主要围绕 `post-service` 的 P0 主链路继续推进，目标是让帖子核心能力从工程骨架进入可联调业务链路。

重点不是一次性把所有功能写完，而是按企业交付节奏，把已经确定的 P0 功能逐个落地、编译通过、能用 Postman 验证，并且同步理解每段代码为什么这样写。

---

## 2. post-service 今日完成内容

### 2.1 完成帖子详情链路

完成 `GetPostDetail` 相关能力：

- 新增或完善 `PostReadService.getPostDetail`。
- 从 `posts` 查询帖子正文和作者。
- 从 `post_images` 查询图片 `image_key` 列表。
- 从 `post_stats` 查询点赞数和评论数。
- 返回 `likedByMe` 字段，后续点赞链路完成后改成真实判断。
- 在 `PostGrpcService` 中接入 `getPostDetail`。
- 在 `PostDebugController` 中提供调试接口。

调试接口：

```text
GET /debug/posts/{postId}?userId=10001
```

开发过程中处理过一个 Postman 常见问题：不能把 `{postId}` 原样发给后端，必须替换成真实帖子 ID，否则 Spring 会报参数类型转换失败。

---

### 2.2 完成删除帖子链路

完成 `DeletePost` 相关能力：

- 新增或完善 `PostWriteService.deletePost`。
- 在 `PostManager.markPostDeleted` 中使用 MyBatis-Plus 的 `LambdaUpdateWrapper` 构造逻辑删除 SQL。
- 删除采用逻辑删除，不物理删除数据库记录。
- 删除条件包含 `post_id`、`user_id`、`deleted = 0`，确保只有作者本人能删除自己的帖子。
- 删除后更新：

```text
status = 0
deleted = 1
updated_at = 当前时间
```

调试接口：

```text
DELETE /debug/posts/{postId}?userId=10001
```

验证结果：接口返回 `200` 和 `true` 后，数据库中记录仍然存在，这是正常的，因为当前设计是逻辑删除。

---

### 2.3 完成点赞 / 取消点赞链路

完成 `LikePost` 相关能力：

- 新增 `PostLikeService`。
- 在 `PostGrpcService.likePost` 中接入真实业务逻辑。
- 在 `PostDebugController` 中新增点赞调试接口。
- `PostManager` 增加点赞状态查询、点赞 upsert、点赞数更新能力。
- `PostLikeMapper` 增加 PostgreSQL `ON CONFLICT` upsert，避免重复插入 `(user_id, post_id)` 主键冲突。
- `PostReadService.getPostDetail` 中的 `likedByMe` 改为真实读取 `post_likes`。

调试接口：

```text
POST /debug/posts/{postId}/like?userId=10001&liked=true
POST /debug/posts/{postId}/like?userId=10001&liked=false
```

点赞链路核心规则：

- 第一次点赞：`post_likes` 写入状态，`post_stats.like_count + 1`。
- 重复点赞：状态没有变化，点赞数不重复增加。
- 取消点赞：`post_likes.status = 0`，`post_stats.like_count - 1`。
- 重复取消：状态没有变化，点赞数不重复减少。

目前为了方便本地联调，点赞时会同步更新 `post_stats.like_count`；同时也写入 Redis 增量 key，为后续 `LikeFlushJob` 做准备。

Redis key 使用今天确认过的个人前缀规范：

```text
hanlian:post:stat:like:<postId>
```

---

### 2.4 Redis key 前缀配置

根据 `student-dev-guide.md` 的规范，确认当前个人前缀为：

```text
hanlian
```

新增 `PostCacheProperties`，统一从配置读取 Redis key 前缀，不在业务代码里散落硬编码。

当前配置：

```yaml
app:
  cache:
    key-prefix: ${REDIS_KEY_PREFIX:hanlian}
    stat-delta-ttl: 1d
```

这意味着后续换环境或换人时，可以通过环境变量 `REDIS_KEY_PREFIX` 调整前缀。

---

### 2.5 Postman 调试集合维护

今天继续维护了 `docs/post-service-debug.postman_collection.json`，用于快速导入 Postman 测试接口。

目前集合中包含：

- Create Post
- Get Post Detail
- Delete Post
- Like Post
- Unlike Post

注意：这个 Postman collection 目前先不提交，等 `post-service` 当前模块的接口补完整后再统一提交。

---

## 3. 今日学习和协作记录

### 3.1 Git

今天继续确认了 Git 的提交边界：

- 大模块完成后再提交。
- 提交动作由开发者自己执行。
- assistant 只给出 `git add`、`git commit`、`git push` 命令。
- Postman 调试集合暂时不提交，避免半成品接口文档进入仓库。

---

### 3.2 Postman

今天用 Postman 验证了帖子相关调试接口，并处理了路径变量问题。

重点理解：

- `{postId}` 是占位符，不是真正传给后端的值。
- Postman 中要填真实帖子 ID，或者配置 collection variable。
- 已导入的 Postman collection 不会自动随着本地 JSON 文件刷新，需要重新导入或手动同步。

---

### 3.3 Redis 命名规范

从 `student-dev-guide.md` 中确认 Redis key 命名规范：

```text
<yourname>:<service>:<domain>:<id>
```

当前项目使用：

```text
hanlian:post:stat:like:<postId>
```

同时确认 Redis key 必须设置 TTL，开发环境建议不超过 1 天。

---

### 3.4 代码理解

今天重点讲解过：

- `markPostDeleted` 为什么是逻辑删除。
- `LambdaUpdateWrapper` 如何构造 update 条件。
- 为什么删除条件里要带 `userId`，保证只有作者本人能删除。
- 点赞链路为什么要先查旧状态，再判断 `delta`。
- 为什么重复点赞不能重复加点赞数。
- 为什么 `post_likes` 负责记录用户行为，`post_stats` 负责记录统计值。

---

## 4. 当前 P0 完成度

按 `docs/post-service-4天企业交付需求单.md` 统计，当前 P0 约完成 45%。

已经完成或基本完成：

- 完整 proto。
- post-proto 构建和引用。
- Flyway 建表。
- Entity + Mapper。
- 发帖。
- 帖子详情。
- 删除帖子。
- 点赞 / 取消点赞。
- 部分 gRPC 业务实现。
- Redis key 前缀配置。

还未完成：

- 评论发布。
- 评论列表。
- UserClient 桩。
- 点赞刷盘任务。
- 评论计数刷盘任务。
- 推荐池重建。
- Feed 三路混合。
- Docker / Jenkins / Nacos 最终联调核对。

---

## 5. 明天继续做什么

明天建议继续推进 P0 中的评论链路，因为它是 `发帖 -> 点赞 -> 评论 -> 查看详情` 这个核心互动闭环的最后一块。

建议顺序：

1. 实现 `CreateComment`。
   - 校验 `userId`、`postId`、`content`。
   - 确认帖子存在且未删除。
   - 写入 `post_comments`。
   - 更新或记录评论计数。

2. 实现 `ListComments`。
   - 支持按 `postId` 查询评论。
   - 支持游标分页。
   - 第一版先做一级评论，楼中楼字段保留但不展开。

3. 接入 gRPC 和 debug HTTP。
   - `PostGrpcService.createComment`
   - `PostGrpcService.listComments`
   - `PostDebugController` 增加 Postman 可测接口。

4. 更新 Postman collection。
   - 增加 Create Comment。
   - 增加 List Comments。
   - 暂时仍然不提交 collection，等模块接口完整后统一提交。

5. 编译和本地冒烟。
   - `mvn -pl post-service -am package -DskipTests`
   - Postman 跑通：发帖 -> 详情 -> 点赞 -> 评论 -> 评论列表。

评论链路完成后，再进入定时任务和 Feed：

- `LikeFlushJob`
- `CommentFlushJob`
- `FeedScoreJob`
- `FeedService.getRecommendFeed`

---

## 6. 今日结论

今天 `post-service` 已经从“能发帖、能查帖”推进到“帖子基础互动可跑”的阶段。

当前最重要的进展是：发帖、查看、删除、点赞这几条链路已经形成一个小闭环，并且都已经接入 debug HTTP 和 gRPC 入口。下一步补齐评论后，P0 的互动主链路就会更完整，后续才能自然进入 Redis 刷盘和 Feed 推荐。
