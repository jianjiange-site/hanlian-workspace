# post-service 接口链路设计

> 目的：把 post-service 当前所有 gRPC 和 HTTP debug 功能，从 proto request / response 参数，到 Controller / gRPC 入口、Service、Manager、Mapper、DB / Redis / Job 影响串起来。  
> 当前范围：`Ping`、发帖、删帖、帖子详情、用户帖子列表、点赞 / 取消点赞、评论 / 评论列表、推荐 Feed，以及 DB / Redis / MinIO debug 检查。  
> 当前状态：P0 主链路已可验收；计数使用 Redis 增量 + 定时刷盘；Feed 已有推荐池 + 冷启动池 + 已看过滤，但好友写扩散、楼中楼、图片 presign 等仍未完成。

---

## 1. 总体入口和分层

### 1.1 proto 文件

```text
proto/post/src/main/proto/post.proto
proto/post/post.proto
```

修改 proto 后需要安装 proto 模块：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\post
mvn clean install -DskipTests
```

### 1.2 gRPC 服务入口

```text
dating-server/post-service/src/main/java/com/aurora/dating/post/grpc/PostGrpcService.java
```

所有 proto RPC 都进入 `PostGrpcService`，再分发到 `PostWriteService`、`PostReadService`、`PostLikeService`、`PostCommentService`、`FeedService`。

### 1.3 HTTP debug 入口

| 路径 | 方法 | 说明 |
| --- | --- | --- |
| `/internal/ping` | GET | 服务存活检查 |
| `/internal/check/db` | GET | DB 连通性检查 |
| `/internal/check/redis` | GET | Redis 连通性检查 |
| `/internal/check/minio` | GET | MinIO / S3 连通性检查 |
| `/debug/posts` | POST | 新建帖子 |
| `/debug/posts/{postId}` | GET | 查看帖子详情 |
| `/debug/posts/{postId}` | DELETE | 删除帖子 |
| `/debug/posts/{postId}/like` | POST | 点赞 / 取消点赞 |
| `/debug/posts/{postId}/comments` | POST | 创建评论 |
| `/debug/posts/{postId}/comments` | GET | 评论列表 |
| `/debug/posts/user/{targetUserId}` | GET | 用户帖子列表 |
| `/debug/posts/feed` | GET | 推荐 Feed |

### 1.4 核心业务分层

```text
Controller / Grpc
  -> Service
     -> Manager / RedisService
        -> Mapper
           -> PostgreSQL / Redis / S3
```

主要类：

| 层级 | 文件 | 作用 |
| --- | --- | --- |
| gRPC | `PostGrpcService` | 实现 9 个 proto RPC |
| HTTP debug | `PostDebugController` | 本地 HTTP 1:1 验收入口 |
| Service | `PostWriteService` | 发帖、删帖 |
| Service | `PostReadService` | 详情、用户帖子列表 |
| Service | `PostLikeService` | 点赞幂等和计数增量 |
| Service | `PostCommentService` | 一级评论创建和查询 |
| Service | `FeedService` | 推荐池 / 冷启动池混排 |
| Redis | `PostStatRedisService` | 点赞 / 评论计数 Redis 增量 |
| Redis | `FeedRedisService` | feed pool、已看集合 |
| Job | `PostStatFlushJob` | 每 5 分钟刷盘计数 |
| Job | `FeedScoreJob` | 每 5 分钟重建推荐池 |
| Manager | `PostManager` | 统一组织 5 张表读写 |

---

## 2. Ping 连通性检查

### 2.1 proto 定义

```proto
rpc Ping (PingRequest) returns (PingResponse);
message PingRequest { string message = 1; }
message PingResponse { string message = 1; }
```

### 2.2 gRPC 接口

```text
dating.post.v1.PostService/Ping
```

grpcurl 示例：

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/post/src/main/proto/post.proto -d '@' localhost:19084 dating.post.v1.PostService/Ping
```

### 2.3 HTTP debug

```http
GET /internal/ping
```

不读写 DB / Redis。

---

## 3. CreatePost 发帖

### 3.1 proto 定义

```proto
rpc CreatePost (CreatePostRequest) returns (CreatePostResponse);

message CreatePostRequest {
  int64 user_id = 1;
  string content = 2;
  repeated string image_keys = 3;
}

message CreatePostResponse { int64 post_id = 1; }
```

### 3.2 参数

| request 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | `int64` | 发帖人业务 ID，必须大于 0 |
| `content` | `string` | 帖子文本，必填，长度 <= 1024 |
| `image_keys` | `repeated string` | 图片对象 key，最多 9 张 |

| response 字段 | 类型 | 说明 |
| --- | --- | --- |
| `post_id` | `int64` | 雪花 ID 生成的帖子业务主键 |

### 3.3 gRPC / HTTP debug

```text
dating.post.v1.PostService/CreatePost
```

```http
POST /debug/posts
Content-Type: application/json

{"userId":10001,"content":"hello","imageKeys":["post-image/10001/a.jpg"]}
```

### 3.4 服务逻辑全流程

```text
PostGrpcService.createPost / PostDebugController.createPost
  -> PostWriteService.createPost(userId, content, imageKeys)
     -> 校验 userId > 0、content 非空且 <= 1024、imageKeys <= 9
     -> SnowflakeIdGenerator.nextId() 生成 postId
     -> PostManager.createPost(postId, userId, content, imageKeys)
        -> insert posts(status=1, deleted=0)
        -> insert post_images(sort_order 0..8)
        -> insert post_stats(like_count=0, comment_count=0)
     -> FeedRedisService.addToColdStartPool(postId, userId)
        -> UserClient.isMale(authorUserId)
        -> ZADD {prefix}:feed:cold_start:pool:{male|female}
```

### 3.5 DB / Redis 影响

DB：写入 `posts`、`post_images`、`post_stats`。

Redis：写入冷启动 ZSet，TTL 7 天。

### 3.6 当前边界

发帖接口只接收已上传的 `image_keys`，当前 post-service 没有图片 presign / confirm 接口。

---

## 4. DeletePost 删除帖子

### 4.1 proto 定义

```proto
rpc DeletePost (DeletePostRequest) returns (DeletePostResponse);

message DeletePostRequest {
  int64 user_id = 1;
  int64 post_id = 2;
}

message DeletePostResponse { bool success = 1; }
```

### 4.2 链路

```text
PostGrpcService.deletePost / DELETE /debug/posts/{postId}
  -> PostWriteService.deletePost(userId, postId)
     -> 校验 userId / postId
     -> PostManager.markPostDeleted(postId, userId)
        -> SQL 条件：post_id = ? AND user_id = ? AND deleted = 0
        -> status = 0, deleted = 1
     -> updated=0 则返回 403：帖子不存在或没有操作权限
     -> FeedRedisService.removePostFromFeedPools(postId)
        -> 从推荐池 / 冷启动池男女桶移除
```

### 4.3 并发与权限

删除依赖 DB 条件更新校验 owner，非作者无法删除。重复删除第二次会因为 `deleted=1` 返回无权限 / 不存在。

---

## 5. GetPostDetail 查询帖子详情

### 5.1 proto 定义

```proto
rpc GetPostDetail (GetPostDetailRequest) returns (GetPostDetailResponse);

message GetPostDetailRequest {
  int64 user_id = 1;
  int64 post_id = 2;
}

message GetPostDetailResponse { Post post = 1; }
```

`Post` 字段：`post_id`、`user_id`、`content`、`image_keys`、`like_count`、`comment_count`、`liked_by_me`、`created_at`。

### 5.2 gRPC / HTTP debug

```text
dating.post.v1.PostService/GetPostDetail
GET /debug/posts/{postId}?userId={viewerUserId}
```

### 5.3 链路

```text
PostGrpcService.getPostDetail / PostDebugController.getPostDetail
  -> PostReadService.getPostDetail(viewerUserId, postId)
     -> PostManager.findNormalPostByPostId(status=1, deleted=0)
     -> PostManager.listImagesByPostId(order by sort_order)
     -> PostManager.findStatByPostId
     -> PostManager.findLikeByUserIdAndPostId(viewerUserId, postId)
     -> PostStatRedisService.getLikeDelta(postId)
     -> PostStatRedisService.getCommentDelta(postId)
     -> like_count = max(DB like_count + Redis delta, 0)
     -> comment_count = max(DB comment_count + Redis delta, 0)
```

### 5.4 数据影响

只读 DB 和 Redis，不写数据。

---

## 6. ListUserPosts 用户帖子列表

### 6.1 proto 定义

```proto
rpc ListUserPosts (ListUserPostsRequest) returns (ListUserPostsResponse);

message ListUserPostsRequest {
  int64 viewer_user_id = 1;
  int64 target_user_id = 2;
  int64 cursor_post_id = 3;
  int32 page_size = 4;
}
```

### 6.2 参数

| 字段 | 说明 |
| --- | --- |
| `viewer_user_id` | 当前查看人，用于计算 `liked_by_me` |
| `target_user_id` | 要查看哪个用户的帖子 |
| `cursor_post_id` | 游标，传 0 查第一页，下一页传上次 `next_cursor_post_id` |
| `page_size` | 小于等于 0 默认 20，最大 50 |

### 6.3 链路

```text
PostGrpcService.listUserPosts / GET /debug/posts/user/{targetUserId}
  -> PostReadService.listUserPosts(viewerUserId, targetUserId, cursorPostId, pageSize)
     -> size = default 20, max 50
     -> PostManager.listNormalPostsByUserId(targetUserId, cursorPostId, size + 1)
        -> posts where user_id=? and status=1 and deleted=0 and post_id < cursor
     -> 多查 1 条判断 hasMore
     -> 对每条 postId 调 getPostDetail(viewerUserId, postId)
```

### 6.4 当前边界

列表详情是逐条调用 `getPostDetail`，存在 N+1 查询；P0 可用，后续可批量查图片、计数、点赞状态优化。

---

## 7. LikePost 点赞 / 取消点赞

### 7.1 proto 定义

```proto
rpc LikePost (LikePostRequest) returns (LikePostResponse);

message LikePostRequest {
  int64 user_id = 1;
  int64 post_id = 2;
  bool liked = 3;
}

message LikePostResponse {
  bool success = 1;
  int32 like_count = 2;
  bool liked = 3;
}
```

### 7.2 gRPC / HTTP debug

```text
dating.post.v1.PostService/LikePost
POST /debug/posts/{postId}/like?userId={userId}&liked=true
```

### 7.3 链路

```text
PostGrpcService.likePost / PostDebugController.likePost
  -> PostLikeService.likePost(userId, postId, liked)
     -> 校验 userId / postId
     -> PostManager.existsNormalPost(postId)
     -> PostManager.findLikeByUserIdAndPostId(userId, postId)
     -> newStatus = liked ? 1 : 0
     -> oldStatus = 已有记录 status，否则 0
     -> 没有记录：PostManager.upsertLike(userId, postId, newStatus)
     -> 状态不同：PostManager.updateLikeStatus(userId, postId, newStatus)
     -> delta = 状态变化 ? +1 / -1 : 0
     -> delta != 0 时 PostStatRedisService.increaseLikeDelta(postId, delta)
     -> 返回 DB like_count + Redis delta
```

### 7.4 DB / Redis 影响

DB：写入或更新 `post_likes`。计数底座 `post_stats` 不在请求内直接更新。

Redis：

```text
{prefix}:post:stat:incr:{postId}:likes
{prefix}:post:updated_set
```

### 7.5 幂等设计

同一用户重复点赞，`oldStatus == newStatus`，delta = 0，不重复增加计数。重复取消点赞同理。

---

## 8. CreateComment 创建评论

### 8.1 proto 定义

```proto
rpc CreateComment (CreateCommentRequest) returns (CreateCommentResponse);

message CreateCommentRequest {
  int64 user_id = 1;
  int64 post_id = 2;
  string content = 3;
  int64 root_id = 4;
  int64 parent_id = 5;
  int64 reply_to_user_id = 6;
}

message CreateCommentResponse {
  int64 comment_id = 1;
  int32 comment_count = 2;
}
```

### 8.2 链路

```text
PostGrpcService.createComment / POST /debug/posts/{postId}/comments
  -> PostCommentService.createComment(userId, postId, content)
     -> 校验 userId / postId / content，content trim 后 <= 512
     -> PostManager.existsNormalPost(postId)
     -> SnowflakeIdGenerator.nextId() 生成 commentId
     -> insert post_comments(root_id=0, parent_id=0, reply_to_user_id=0, status=1, deleted=0)
     -> PostStatRedisService.increaseCommentDelta(postId, +1)
     -> 返回 DB comment_count + Redis delta
```

### 8.3 当前边界

proto 已有楼中楼字段 `root_id` / `parent_id` / `reply_to_user_id`，但当前 gRPC service 没有把这些字段传入业务方法，实际只创建一级评论。

---

## 9. ListComments 评论列表

### 9.1 proto 定义

```proto
rpc ListComments (ListCommentsRequest) returns (ListCommentsResponse);

message ListCommentsRequest {
  int64 user_id = 1;
  int64 post_id = 2;
  int64 cursor_comment_id = 3;
  int32 page_size = 4;
}
```

### 9.2 链路

```text
PostGrpcService.listComments / GET /debug/posts/{postId}/comments
  -> PostCommentService.listComments(userId, postId, cursorCommentId, pageSize)
     -> size = default 20, max 50
     -> PostManager.listRootCommentsByPostId(postId, cursorCommentId, size + 1)
        -> where post_id=? and root_id=0 and deleted=0 and status=1 and comment_id < cursor
        -> order by comment_id desc
     -> 多查 1 条判断 hasMore
     -> 转 Comment proto
```

### 9.3 数据影响

只读 `post_comments`，当前不使用 Redis 评论 ZSet。

---

## 10. GetRecommendFeed 推荐 Feed

### 10.1 proto 定义

```proto
rpc GetRecommendFeed (GetRecommendFeedRequest) returns (GetRecommendFeedResponse);

message GetRecommendFeedRequest {
  int64 user_id = 1;
  int32 page_size = 2;
}
```

### 10.2 链路

```text
PostGrpcService.getRecommendFeed / GET /debug/posts/feed
  -> FeedService.getRecommendFeed(userId, pageSize)
     -> size = default 10, max 20
     -> FeedRedisService.listRecommendPostIds(userId, size + 1)
        -> UserClient.isMale(viewerUserId)
        -> 读取异性 recommend ZSet
     -> FeedRedisService.listColdStartPostIds(userId, size + 1)
        -> 读取异性 cold_start ZSet
     -> mixFeedPostIds
        -> 70% 推荐池 + 30% 冷启动池
        -> 过滤重复 postId
        -> 过滤 FeedRedisService.hasSeen(userId, postId)
     -> 多取 1 条判断 hasMore
     -> FeedRedisService.markSeen(userId, mergedIds)
     -> 对每个 postId 调 PostReadService.getPostDetail
        -> 被删或异常的帖子跳过
```

### 10.3 Redis key

```text
{prefix}:feed:pool:recommend:male
{prefix}:feed:pool:recommend:female
{prefix}:feed:cold_start:pool:male
{prefix}:feed:cold_start:pool:female
{prefix}:feed:seen:{userId}
```

### 10.4 当前边界

`next_cursor_post_id` 只是返回本页最后一个 postId，当前 Feed 读侧没有消费 cursor 参数；翻页主要依赖 `seen` Set 过滤。

---

## 11. PostStatFlushJob 计数刷盘

### 11.1 触发

```text
PostStatFlushJob.flushPostStats
@Scheduled(fixedDelay = 300000)
```

### 11.2 链路

```text
PostStatFlushJob.flushPostStats
  -> PostStatRedisService.listUpdatedPosts(100)
  -> 对每个 postId：
     -> popLikeDelta: GETSET likes key = 0
     -> popCommentDelta: GETSET comments key = 0
     -> likeDelta != 0：PostManager.increaseLikeCount(postId, delta)
     -> commentDelta != 0：PostManager.increaseCommentCount(postId, delta)
     -> PostStatRedisService.removeUpdatedPost(postId)
```

### 11.3 并发边界

当前没有 ShedLock；多实例部署时可能重复刷盘或误删 updated_set，需要补多实例互斥或 Lua 原子脚本。

---

## 12. FeedScoreJob 推荐池重建

### 12.1 触发

```text
FeedScoreJob.rebuildRecommendPool
@Scheduled(fixedDelay = 300000)
```

### 12.2 链路

```text
FeedScoreJob.rebuildRecommendPool
  -> PostManager.listRecentNormalPosts(now - 3 days, 3000)
  -> 对每条帖子：
     -> PostManager.findStatByPostId(postId)
     -> score = (10 + like_count + comment_count * 3) / pow(ageHours + 2, 1.5)
     -> UserClient.isMale(authorUserId)
     -> 按作者性别放入 male / female candidates
  -> FeedRedisService.rebuildRecommendPool(true, maleCandidates)
  -> FeedRedisService.rebuildRecommendPool(false, femaleCandidates)
```

### 12.3 当前边界

Job 查询每条帖子时逐个查 `post_stats` 和 `UserClient.isMale`，大规模时需要批量优化。当前 `UserClient` 是性别分桶依赖，user-service 不可用时以 UserClient 当前降级逻辑为准。

---

## 13. 当前功能完成状态

| 功能 | gRPC | HTTP debug | DB | Redis / Job | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| Ping | 有 | 有 | 无 | 无 | 已完成 |
| CreatePost | 有 | 有 | `posts` / `post_images` / `post_stats` | 冷启动池 | 已完成 |
| DeletePost | 有 | 有 | 逻辑删除 `posts` | 移除 feed pool | 已完成 |
| GetPostDetail | 有 | 有 | 读帖子 / 图片 / 计数 / 点赞 | 读计数 delta | 已完成 |
| ListUserPosts | 有 | 有 | 读 `posts` | 读计数 delta | 已完成 |
| LikePost | 有 | 有 | `post_likes` | 计数 delta + updated_set | 已完成，幂等 |
| CreateComment | 有 | 有 | `post_comments` | 评论 delta + updated_set | 已完成一级评论 |
| ListComments | 有 | 有 | `post_comments` | 无 | 已完成一级评论列表 |
| GetRecommendFeed | 有 | 有 | 详情回源 | 推荐池 / 冷启动池 / seen Set | 已完成基础版 |
| PostStatFlushJob | 无 | 无 | 更新 `post_stats` | 消费 delta | 已完成单实例版 |
| FeedScoreJob | 无 | 无 | 读近 3 天帖子 | 重建推荐池 | 已完成基础版 |

---

## 14. 技术方案中未完成 / 待补齐

| 设计项 | 当前代码状态 | 后续建议 |
| --- | --- | --- |
| 帖子图片 presign / confirm | 当前只接收 image_keys | 增加 post 图片上传签名或接 media-service |
| 好友写扩散 timeline | 当前没有 `PostFanoutService` | 接 user-service 好友列表后实现 `user:timeline:{userId}` |
| Feed 三路固定位置混排 | 当前是 70% 推荐 + 30% 冷启动 | 如要对齐设计，增加好友强插和位置规则 |
| BloomFilter 已读去重 | 当前使用 Redis Set `{prefix}:feed:seen:{userId}` | 用户量大后换 Redisson BloomFilter 或分页 cursor 方案 |
| 评论 Redis ZSet 窗口 | 当前评论列表直接查 DB | 增加 `post:comments:{postId}` 热评论窗口 |
| 楼中楼评论 | proto 有字段，service 固定写 0 | 让 gRPC 传 root / parent / reply，并补权限和查询 |
| 删除评论接口 | 当前无 RPC / HTTP | 增加 DeleteComment，并同步评论计数 delta |
| ShedLock 多实例互斥 | 当前 Job 没有互斥 | 多实例部署前必须补 |
| Feed cursor | response 有 cursor，request 无 cursor | proto 增加 cursor 或明确 seen Set 翻页语义 |
| 用户资料展示聚合 | post 只返回 author user_id | gateway / App 聚合 user-service 资料 |
| 通知推送 | 当前点赞 / 评论不通知 | 后续经 im-service 发系统消息 |

---

## 15. 推荐学习顺序

1. `proto/post/src/main/proto/post.proto`：看 9 个 RPC 和 `Post` / `Comment` 字段。
2. `PostGrpcService`：看 gRPC 如何调用 service。
3. `PostDebugController`：用 HTTP debug 对照 gRPC 验收。
4. `PostWriteService` + `PostManager.createPost`：理解发帖事务。
5. `PostLikeService` + `PostStatRedisService`：理解点赞幂等和 Redis 增量。
6. `PostCommentService`：理解一级评论。
7. `FeedService` + `FeedRedisService`：理解推荐池、冷启动池和已看过滤。
8. `PostStatFlushJob` / `FeedScoreJob`：理解异步刷盘和推荐池重建。
