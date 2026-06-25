# post-service 接口验收文档

> 用途：本文件用于你自己做 Postman / gRPC 验收，同时帮助复习每个接口背后的代码链路和设计逻辑。  
> 范围：HTTP debug 接口、对应 gRPC 方法、核心代码链路、数据库 / Redis 验收点、异常测试点。  
> 本地 HTTP 地址：`http://localhost:18084`  
> Postman 集合：`docs/post-service-debug.postman_collection.json`

---

## 1. 验收前准备

### 1.1 服务准备

1. IDEA 里对 `post-service` 执行 `clean package`，确认编译通过。
2. 启动 `post-service`。
3. 确认配置连接的是：
   - PostgreSQL：`dating_dev_hanlian`
   - Redis：`database: 1`
   - HTTP 端口：`18084`
   - gRPC 端口：`19084`

### 1.2 Postman 变量

Postman collection 里需要关注这些变量：

| 变量 | 示例值 | 说明 |
|---|---:|---|
| `baseUrl` | `http://localhost:18084` | post-service HTTP debug 地址 |
| `userId` | `10001` | 当前测试用户 |
| `postId` | 实际创建出的帖子 ID | 建议创建帖子后手动更新 |
| `missingPostId` | `9999999999999999` | 用于异常测试 |

### 1.3 常用数据库表

| 表 | 作用 |
|---|---|
| `posts` | 帖子主表 |
| `post_images` | 帖子图片表，只存 `image_key` |
| `post_stats` | 点赞数 / 评论数刷盘后的底座 |
| `post_likes` | 用户点赞状态，联合主键 `(user_id, post_id)` |
| `post_comments` | 评论表 |

### 1.4 常用 Redis key

| Key | 类型 | 作用 |
|---|---|---|
| `hanlian:post:stat:incr:{postId}:likes` | String | 点赞未刷盘增量 |
| `hanlian:post:stat:incr:{postId}:comments` | String | 评论未刷盘增量 |
| `hanlian:post:updated_set` | Set | 待刷盘 postId 集合 |
| `hanlian:feed:cold_start:pool:male` | ZSet | 男性作者冷启动池 |
| `hanlian:feed:cold_start:pool:female` | ZSet | 女性作者冷启动池 |
| `hanlian:feed:pool:recommend:male` | ZSet | 男性作者推荐池 |
| `hanlian:feed:pool:recommend:female` | ZSet | 女性作者推荐池 |
| `hanlian:feed:seen:{userId}` | Set | 用户已看帖子 |

注意：`updated_set` 和 `seen` 是 Set，要用 `SMEMBERS` 看；Feed 池是 ZSet，要用 `ZRANGE` / `ZREVRANGE` 看；计数增量是 String，要用 `GET` 看。

---

## 2. 总体代码分层

当前 post-service 的主要调用方向：

```text
HTTP:
PostDebugController
  -> Service
  -> PostManager / RedisService
  -> Mapper
  -> PostgreSQL / Redis

gRPC:
PostGrpcService
  -> Service
  -> PostManager / RedisService
  -> Mapper
  -> PostgreSQL / Redis
```

关键原则：

- `controller` / `grpc` 只负责接请求、组装响应。
- `service` 负责业务逻辑和事务边界。
- `manager` 负责单表数据库操作。
- `mapper` 只做具体 SQL / MyBatis-Plus 调用。
- Redis 不作为最终数据库，核心明细仍然落 PostgreSQL。
- 点赞 / 评论计数采用“DB 底座 + Redis 增量 + 定时刷盘”。

---

## 3. 发帖接口

### 3.1 HTTP 接口

```http
POST /debug/posts
Content-Type: application/json
```

请求体：

```json
{
  "userId": 10001,
  "content": "my first post-service post",
  "imageKeys": [
    "post-image/10001/1.jpg"
  ]
}
```

成功响应：

```json
{
  "postId": 7297833297494016
}
```

### 3.2 gRPC 方法

```text
CreatePost(CreatePostRequest) returns (CreatePostResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 发帖用户 ID |
| `content` | 帖子内容，最多 1024 字符 |
| `image_keys` | 图片对象存储 key，最多 9 张 |

响应字段：

| 字段 | 说明 |
|---|---|
| `post_id` | 新帖子业务 ID |

### 3.3 完整链路

```text
PostDebugController.createPost
PostGrpcService.createPost
  -> PostWriteService.createPost
     -> SnowflakeIdGenerator.nextId
     -> PostManager.createPost
        -> PostMapper.insert
        -> PostImageMapper.insert
        -> PostStatMapper.insert
     -> FeedRedisService.addToColdStartPool
        -> Redis ZADD cold_start pool
```

### 3.4 设计记忆点

- `posts` 存正文和用户 ID。
- `post_images` 只存 `image_key`，不存真实图片 URL。
- `post_stats` 初始化点赞数、评论数为 0。
- 发帖成功后同步写入冷启动池，让新帖马上有曝光机会。

### 3.5 验收点

Postman：

- 跑 `Create Post`。
- 拿响应里的 `postId` 更新 Postman 变量 `postId`。

数据库：

- `posts` 有新记录，`status = 1`，`deleted = 0`。
- `post_images` 有对应图片记录。
- `post_stats` 有对应记录，`like_count = 0`，`comment_count = 0`。

Redis：

- 根据作者性别，下面某个 ZSet 里能看到该 `postId`：
  - `hanlian:feed:cold_start:pool:male`
  - `hanlian:feed:cold_start:pool:female`

异常：

- 内容为空：HTTP `400`，返回 `内容不能为空`。
- 图片超过 9 张：HTTP `400`，返回 `图片数量不能超过9张`。

---

## 4. 帖子详情接口

### 4.1 HTTP 接口

```http
GET /debug/posts/{postId}?userId={userId}
```

示例：

```http
GET /debug/posts/7297833297494016?userId=10001
```

成功响应：

```json
{
  "postId": 7297833297494016,
  "userId": 10001,
  "content": "my first post-service post",
  "imageKeys": ["post-image/10001/1.jpg"],
  "likeCount": 0,
  "commentCount": 0,
  "likedByMe": false,
  "createdAt": 1760000000000
}
```

### 4.2 gRPC 方法

```text
GetPostDetail(GetPostDetailRequest) returns (GetPostDetailResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 当前查看用户，用于判断 `liked_by_me` |
| `post_id` | 要查看的帖子 ID |

响应字段：

| 字段 | 说明 |
|---|---|
| `post` | 帖子详情 |

### 4.3 完整链路

```text
PostDebugController.getPostDetail
PostGrpcService.getPostDetail
  -> PostReadService.getPostDetail
     -> PostManager.findNormalPostByPostId
        -> PostMapper.selectOne
     -> PostManager.listImagesByPostId
        -> PostImageMapper.selectList
     -> PostManager.findStatByPostId
        -> PostStatMapper.selectById
     -> PostStatRedisService.getLikeDelta
     -> PostStatRedisService.getCommentDelta
     -> PostManager.findLikeByUserIdAndPostId
        -> PostLikeMapper.selectOne
```

### 4.4 设计记忆点

- 详情接口读的是“实时计数”：

```text
实时点赞数 = post_stats.like_count + Redis like delta
实时评论数 = post_stats.comment_count + Redis comment delta
```

- `likedByMe` 不是从帖子表读，而是查 `post_likes` 当前用户对这个帖子的状态。
- 被删除的帖子不会返回，因为查询条件要求 `status = 1`、`deleted = 0`。

### 4.5 验收点

Postman：

- 跑 `Get Post Detail`。
- 确认返回正文、图片、计数、`likedByMe`。

数据库：

- `posts` 中该帖子必须是正常状态。
- `post_likes` 会影响 `likedByMe`。

Redis：

- 如果点赞 / 评论刚发生但还没刷盘，详情计数仍然应该能体现 Redis 增量。

异常：

- 帖子不存在：HTTP `404`，返回 `帖子不存在`。

---

## 5. 删除帖子接口

### 5.1 HTTP 接口

```http
DELETE /debug/posts/{postId}?userId={userId}
```

成功响应：

```json
{
  "success": true
}
```

### 5.2 gRPC 方法

```text
DeletePost(DeletePostRequest) returns (DeletePostResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 操作用户 ID |
| `post_id` | 要删除的帖子 ID |

响应字段：

| 字段 | 说明 |
|---|---|
| `success` | 是否删除成功 |

### 5.3 完整链路

```text
PostDebugController.deletePost
PostGrpcService.deletePost
  -> PostWriteService.deletePost
     -> PostManager.markPostDeleted
        -> PostMapper.update
     -> FeedRedisService.removePostFromFeedPools
        -> Redis ZREM cold_start male/female
        -> Redis ZREM recommend male/female
```

### 5.4 设计记忆点

- 删除是逻辑删除，不是物理删除。
- 只有作者本人可以删除。
- 删除成功后清理 Feed 相关 Redis 池，避免 Feed 继续推荐已删帖。
- 如果 Feed 里仍残留旧 `postId`，`FeedService` 读取详情时也会跳过无效帖子。

### 5.5 验收点

Postman：

- 跑 `Delete Post`。

数据库：

- `posts.status = 0`
- `posts.deleted = 1`

Redis：

- 四个池中都不应该再有该 `postId`：
  - `hanlian:feed:cold_start:pool:male`
  - `hanlian:feed:cold_start:pool:female`
  - `hanlian:feed:pool:recommend:male`
  - `hanlian:feed:pool:recommend:female`

异常：

- 帖子不存在或无权限：HTTP `403`，返回 `帖子不存在或没有操作权限`。

---

## 6. 点赞 / 取消点赞接口

### 6.1 HTTP 接口

点赞：

```http
POST /debug/posts/{postId}/like?userId={userId}&liked=true
```

取消点赞：

```http
POST /debug/posts/{postId}/like?userId={userId}&liked=false
```

成功响应：

```json
{
  "success": true,
  "liked": true,
  "likeCount": 1
}
```

### 6.2 gRPC 方法

```text
LikePost(LikePostRequest) returns (LikePostResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 操作用户 ID |
| `post_id` | 目标帖子 ID |
| `liked` | `true` 点赞，`false` 取消点赞 |

响应字段：

| 字段 | 说明 |
|---|---|
| `success` | 是否成功 |
| `liked` | 当前是否点赞 |
| `like_count` | 当前实时点赞数 |

### 6.3 完整链路

```text
PostDebugController.likePost
PostGrpcService.likePost
  -> PostLikeService.likePost
     -> PostManager.existsNormalPost
        -> PostMapper.selectOne
     -> PostManager.findLikeByUserIdAndPostId
        -> PostLikeMapper.selectOne
     -> PostManager.upsertLike / updateLikeStatus
        -> PostLikeMapper.upsertLike / update
     -> PostStatRedisService.increaseLikeDelta
        -> Redis INCRBY likes delta
        -> Redis SADD updated_set
     -> PostManager.findStatByPostId
     -> PostStatRedisService.getLikeDelta
```

### 6.4 设计记忆点

- `post_likes` 不删除数据，取消点赞只是把 `status` 改为 0。
- 点赞状态变化才产生 `delta`：
  - 未点赞 -> 点赞：`delta = +1`
  - 已点赞 -> 取消：`delta = -1`
  - 重复点赞 / 重复取消：`delta = 0`
- 计数不直接频繁更新 `post_stats`，而是先写 Redis 增量，后面由定时任务刷盘。

### 6.5 验收点

Postman：

- 跑 `Like Post`。
- 再跑一次 `Like Post`，确认重复点赞不会继续加计数。
- 跑 `Unlike Post`，确认计数减少。
- 再跑一次 `Unlike Post`，确认重复取消不会继续减少。

数据库：

- `post_likes` 中 `(user_id, post_id)` 只有一条记录。
- 点赞后 `status = 1`。
- 取消点赞后 `status = 0`。

Redis：

- `GET hanlian:post:stat:incr:{postId}:likes`
- `SMEMBERS hanlian:post:updated_set`

异常：

- 帖子不存在：HTTP `404`，返回 `帖子不存在`。

---

## 7. 创建评论接口

### 7.1 HTTP 接口

```http
POST /debug/posts/{postId}/comments
Content-Type: application/json
```

请求体：

```json
{
  "userId": 10001,
  "content": "this is my first comment"
}
```

成功响应：

```json
{
  "commentId": 7297833297494017,
  "commentCount": 1
}
```

### 7.2 gRPC 方法

```text
CreateComment(CreateCommentRequest) returns (CreateCommentResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 评论用户 ID |
| `post_id` | 目标帖子 ID |
| `content` | 评论内容，最多 512 字符 |
| `root_id` | 预留楼中楼字段，当前一级评论为 0 |
| `parent_id` | 预留楼中楼字段，当前为 0 |
| `reply_to_user_id` | 预留楼中楼字段，当前为 0 |

响应字段：

| 字段 | 说明 |
|---|---|
| `comment_id` | 新评论 ID |
| `comment_count` | 当前实时评论数 |

### 7.3 完整链路

```text
PostDebugController.createComment
PostGrpcService.createComment
  -> PostCommentService.createComment
     -> PostManager.existsNormalPost
        -> PostMapper.selectOne
     -> SnowflakeIdGenerator.nextId
     -> PostManager.createComment
        -> PostCommentMapper.insert
     -> PostStatRedisService.increaseCommentDelta
        -> Redis INCRBY comments delta
        -> Redis SADD updated_set
     -> PostManager.findStatByPostId
     -> PostStatRedisService.getCommentDelta
```

### 7.4 设计记忆点

- 当前只做一级评论。
- `rootId`、`parentId`、`replyToUserId` 先统一为 0，为以后楼中楼保留。
- 评论明细实时落库。
- 评论数走 Redis 增量，后续定时刷入 `post_stats`。

### 7.5 验收点

Postman：

- 跑 `Create Comment`。

数据库：

- `post_comments` 新增一条记录。
- `root_id = 0`
- `parent_id = 0`
- `reply_to_user_id = 0`
- `status = 1`
- `deleted = 0`

Redis：

- `GET hanlian:post:stat:incr:{postId}:comments`
- `SMEMBERS hanlian:post:updated_set`

异常：

- 内容为空：HTTP `400`，返回 `内容不能为空`。
- 评论超过 512 字符：HTTP `400`，返回 `评论内容不能超过512个字符`。
- 帖子不存在：HTTP `404`，返回 `帖子不存在`。

---

## 8. 评论列表接口

### 8.1 HTTP 接口

```http
GET /debug/posts/{postId}/comments?userId={userId}&pageSize=20&cursor=0
```

成功响应：

```json
{
  "comments": [
    {
      "commentId": 7297833297494017,
      "postId": 7297833297494016,
      "userId": 10001,
      "rootId": 0,
      "parentId": 0,
      "replyToUserId": 0,
      "content": "this is my first comment",
      "createdAt": 1760000000
    }
  ],
  "nextCursorCommentId": 7297833297494017,
  "hasMore": false
}
```

### 8.2 gRPC 方法

```text
ListComments(ListCommentsRequest) returns (ListCommentsResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 当前查看用户 |
| `post_id` | 目标帖子 ID |
| `cursor_comment_id` | 游标，第一页传 0 |
| `page_size` | 每页数量，默认 20，最大 50 |

响应字段：

| 字段 | 说明 |
|---|---|
| `comments` | 评论列表 |
| `next_cursor_comment_id` | 下一页游标 |
| `has_more` | 是否还有更多 |

### 8.3 完整链路

```text
PostDebugController.listComments
PostGrpcService.listComments
  -> PostCommentService.listComments
     -> PostManager.listRootCommentsByPostId
        -> PostCommentMapper.selectList
     -> toProto
```

### 8.4 设计记忆点

- 当前查的是一级评论：`root_id = 0`。
- 按 `comment_id desc` 倒序分页。
- 使用 `pageSize + 1` 多查一条判断 `hasMore`。
- `nextCursorCommentId` 是本页最后一条评论 ID。

### 8.5 验收点

Postman：

- 跑 `List Comments`。
- 如果 `hasMore = true`，下一页把 `cursor` 改成 `nextCursorCommentId`。

数据库：

- 查询的是 `post_comments`。
- 条件包括：`post_id`、`root_id = 0`、`deleted = 0`、`status = 1`。

异常：

- `userId` 为空：HTTP `400`。
- `postId` 为空：HTTP `400`。

---

## 9. 用户帖子列表接口

### 9.1 HTTP 接口

```http
GET /debug/posts/user/{targetUserId}?viewerUserId={viewerUserId}&pageSize=20&cursor=0
```

示例：

```http
GET /debug/posts/user/10001?viewerUserId=10001&pageSize=20&cursor=0
```

成功响应：

```json
{
  "posts": [],
  "nextCursorPostId": 0,
  "hasMore": false
}
```

### 9.2 gRPC 方法

```text
ListUserPosts(ListUserPostsRequest) returns (ListUserPostsResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `viewer_user_id` | 当前查看用户，用于判断 `liked_by_me` |
| `target_user_id` | 要查看谁发布的帖子 |
| `cursor_post_id` | 游标，第一页传 0 |
| `page_size` | 每页数量 |

响应字段：

| 字段 | 说明 |
|---|---|
| `posts` | 帖子列表 |
| `next_cursor_post_id` | 下一页游标 |
| `has_more` | 是否还有更多 |

### 9.3 完整链路

```text
PostDebugController.listUserPosts
PostGrpcService.listUserPosts
  -> PostReadService.listUserPosts
     -> PostManager.listNormalPostsByUserId
        -> PostMapper.selectList
     -> PostReadService.getPostDetail
        -> 补充图片、计数、likedByMe
```

### 9.4 设计记忆点

- 列表先查 `posts`，再逐条复用详情接口组装完整返回。
- 这样虽然多一步，但代码复用清晰，能保证列表里的计数和 `likedByMe` 与详情一致。
- 游标使用 `post_id`，按 `post_id desc` 查。

### 9.5 验收点

Postman：

- 跑 `List User Posts`。
- 发多条帖子后，检查分页字段。

数据库：

- 查询 `posts`。
- 条件包括：`user_id = targetUserId`、`status = 1`、`deleted = 0`。

异常：

- `viewerUserId` 为空：HTTP `400`，返回 `查看用户ID不能为空`。
- `targetUserId` 为空：HTTP `400`，返回 `目标用户ID不能为空`。

---

## 10. 推荐 Feed 接口

### 10.1 HTTP 接口

```http
GET /debug/posts/feed?userId={userId}&pageSize=10
```

成功响应：

```json
{
  "posts": [],
  "nextCursorPostId": 0,
  "hasMore": false
}
```

### 10.2 gRPC 方法

```text
GetRecommendFeed(GetRecommendFeedRequest) returns (GetRecommendFeedResponse)
```

请求字段：

| 字段 | 说明 |
|---|---|
| `user_id` | 当前用户 ID |
| `page_size` | 每页数量，默认 10，最大 20 |

响应字段：

| 字段 | 说明 |
|---|---|
| `posts` | 推荐帖子列表 |
| `next_cursor_post_id` | 下一页游标 |
| `has_more` | 是否还有更多 |

### 10.3 完整链路

```text
PostDebugController.getRecommendFeed
PostGrpcService.getRecommendFeed
  -> FeedService.getRecommendFeed
     -> FeedRedisService.listRecommendPostIds
        -> Redis ZREVRANGE recommend pool
     -> FeedRedisService.listColdStartPostIds
        -> Redis ZREVRANGE cold_start pool
     -> FeedService.mixFeedPostIds
        -> 按 70% recommend + 30% cold_start 混排
        -> FeedRedisService.hasSeen 过滤已看
     -> FeedRedisService.markSeen
        -> Redis SADD feed:seen:{userId}
     -> PostReadService.getPostDetail
        -> 查帖子详情，已删除帖子跳过
```

### 10.4 推荐池相关定时链路

```text
FeedScoreJob.rebuildRecommendPool
  -> PostManager.listRecentNormalPosts
     -> PostMapper.selectList 最近 3 天正常帖子
  -> PostManager.findStatByPostId
     -> PostStatMapper.selectById
  -> UserClient.isMale
     -> 判断作者性别
  -> FeedRedisService.rebuildRecommendPool
     -> Redis DELETE old pool
     -> Redis ZADD recommend pool
```

### 10.5 设计记忆点

- 冷启动池：发帖时立即写入，解决新帖没有点赞导致没人看到的问题。
- 推荐池：定时任务根据发布时间、点赞数、评论数计算热度分。
- 已看过滤：`hanlian:feed:seen:{userId}`，防止用户反复刷到同一批帖子。
- 删除帖子：删除时清理 Feed 池；读取时也会兜底跳过已删除帖子。
- 当前版本实现的是“推荐池 + 冷启动池”，好友时间线仍属于后续增强。

### 10.6 验收点

Postman：

- 跑 `Get Recommend Feed`。
- 确认响应包含：
  - `posts`
  - `nextCursorPostId`
  - `hasMore`

Redis：

- 新发帖后检查冷启动池：
  - `ZRANGE hanlian:feed:cold_start:pool:male 0 -1 WITHSCORES`
  - `ZRANGE hanlian:feed:cold_start:pool:female 0 -1 WITHSCORES`
- 等 `FeedScoreJob` 跑完后检查推荐池：
  - `ZRANGE hanlian:feed:pool:recommend:male 0 -1 WITHSCORES`
  - `ZRANGE hanlian:feed:pool:recommend:female 0 -1 WITHSCORES`
- 调用 Feed 后检查已看集合：
  - `SMEMBERS hanlian:feed:seen:{userId}`

异常：

- `userId` 为空：HTTP `400`，返回 `用户ID不能为空`。

---

## 11. 计数刷盘验收

点赞和评论都不是每次直接更新 `post_stats`，而是先写 Redis 增量，再由定时任务刷盘。

### 11.1 点赞 / 评论写入时

```text
PostLikeService.likePost
PostCommentService.createComment
  -> PostStatRedisService.increaseLikeDelta / increaseCommentDelta
     -> Redis INCRBY delta
     -> Redis SADD updated_set
```

### 11.2 定时刷盘时

```text
PostStatFlushJob
  -> Redis SMEMBERS updated_set
  -> Redis GET like delta / comment delta
  -> PostManager.increaseLikeCount / increaseCommentCount
     -> PostStatMapper.update
  -> Redis 清理或归零增量
```

### 11.3 验收点

操作：

1. 点赞或评论。
2. 立刻看 Redis 增量。
3. 查看帖子详情，确认返回计数已经变化。
4. 等定时任务执行。
5. 看 `post_stats` 中计数是否更新。
6. 看 Redis 增量是否被刷掉。

Redis 命令：

```redis
GET hanlian:post:stat:incr:{postId}:likes
GET hanlian:post:stat:incr:{postId}:comments
SMEMBERS hanlian:post:updated_set
```

数据库：

```sql
SELECT * FROM post_stats WHERE post_id = {postId};
```

记忆点：

- 明细表立即写库：`post_likes`、`post_comments`。
- 计数表延迟刷盘：`post_stats`。
- 详情接口读实时值：`post_stats + Redis delta`。

---

## 12. 异常返回验收

HTTP debug 接口统一由：

```text
PostDebugExceptionHandler
```

处理。

gRPC 接口统一由：

```text
GrpcExceptionHandler
```

处理。

### 12.1 HTTP 异常

| 场景 | HTTP 状态码 | 返回 message |
|---|---:|---|
| 参数错误 | 400 | 中文参数错误 |
| 帖子不存在 | 404 | `帖子不存在` |
| 删除无权限 / 不存在 | 403 | `帖子不存在或没有操作权限` |
| 未知异常 | 500 | `服务器内部错误` |

示例：

```json
{
  "success": false,
  "message": "帖子不存在"
}
```

### 12.2 gRPC 异常

| Java 异常 | gRPC 状态 |
|---|---|
| `IllegalArgumentException` | `INVALID_ARGUMENT` |
| `BusinessException(HttpStatus.NOT_FOUND)` | `NOT_FOUND` |
| `BusinessException(HttpStatus.FORBIDDEN)` | `PERMISSION_DENIED` |
| 其他异常 | `INTERNAL` |

### 12.3 Postman 异常测试

Postman collection 里已经有 `Exception Tests` 文件夹，建议每次大改后跑一遍：

- `Get Missing Post - 404`
- `Like Missing Post - 404`
- `Comment Missing Post - 404`
- `Delete Missing Or Forbidden Post - 403`
- `Create Post Empty Content - 400`
- `Create Post Too Many Images - 400`
- `Create Comment Empty Content - 400`
- `Get Recommend Feed Response Shape`

---

## 13. 推荐验收顺序

建议你按下面顺序完整跑一遍：

1. `Create Post`
2. 更新 Postman 变量 `postId`
3. `Get Post Detail`
4. `Like Post`
5. 再次 `Like Post`，验证幂等
6. `Unlike Post`
7. `Create Comment`
8. `List Comments`
9. `List User Posts`
10. `Get Recommend Feed`
11. 等待定时任务，检查 `post_stats` 刷盘
12. `Delete Post`
13. 再查详情，确认返回 `404`
14. 跑 `Exception Tests`

---

## 14. IDEA 复习入口

你复习每个接口时，可以按下面文件顺序看。

HTTP 入口：

```text
dating-server/post-service/src/main/java/com/jianjiange/dating/post/controller/PostDebugController.java
```

gRPC 入口：

```text
dating-server/post-service/src/main/java/com/jianjiange/dating/post/grpc/PostGrpcService.java
```

业务层：

```text
PostWriteService.java
PostReadService.java
PostLikeService.java
PostCommentService.java
FeedService.java
```

Redis 相关：

```text
PostStatRedisService.java
FeedRedisService.java
```

定时任务：

```text
PostStatFlushJob.java
FeedScoreJob.java
```

数据访问：

```text
PostManager.java
PostMapper.java
PostImageMapper.java
PostStatMapper.java
PostLikeMapper.java
PostCommentMapper.java
```

异常处理：

```text
PostDebugExceptionHandler.java
GrpcExceptionHandler.java
BusinessException.java
```

---

## 15. 当前实现与技术方案的差异

当前版本已经完成企业交付主链路，但和完整技术方案相比，还有一些保留项：

| 技术方案项 | 当前状态 |
|---|---|
| 发帖 / 详情 / 删除 | 已完成 |
| 点赞幂等 | 已完成 |
| 评论一级列表 | 已完成 |
| DB 底座 + Redis 增量 | 已完成 |
| 定时刷盘 | 已完成 |
| 冷启动池 | 已完成 |
| 推荐池定时重建 | 已完成 |
| Feed 已看过滤 | 已完成，使用 Redis Set |
| Feed 混排 | 已完成 recommend + cold_start |
| 好友时间线 | 暂未完整实现 |
| BloomFilter 去重 | 当前用 Redis Set 替代 |
| ShedLock 多实例互斥 | 可后续增强 |
| 评论 Redis ZSet 缓存最新 200 条 | 当前主要走 DB 列表 |

这份文档验收的是当前实现版本，不要求你一次性完成所有二期增强。

