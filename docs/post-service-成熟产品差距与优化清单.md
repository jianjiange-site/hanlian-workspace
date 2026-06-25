# post-service 成熟产品差距与优化清单

> 用途：记录当前 post-service 与成熟产品级实现之间的差距，方便后续迭代优化。  
> 定位：当前版本是 4 天企业交付型第一版，目标是主链路闭环、可测试、可理解、可继续扩展；本文不是否定当前实现，而是给后续升级排路线图。

---

## 1. 当前版本定位

当前 post-service 已完成核心业务闭环：

- 发帖、详情、删除
- 点赞 / 取消点赞
- 评论发布、评论列表
- 用户帖子列表
- Redis 计数增量 + 定时刷盘
- Feed 冷启动池
- Feed 推荐池定时重建
- Feed 已看过滤
- 推荐池 + 冷启动池混排
- HTTP debug 中文异常
- gRPC 异常状态码处理
- Postman 验收文档

当前版本适合：

- 本地调试
- 学习微服务分层
- 理解 Redis 写合并
- 理解 Feed 第一版推荐链路
- 做企业交付型 demo

但距离成熟产品仍有不少差距，主要集中在：

- Feed 推荐完整度
- 数据一致性修复能力
- 性能优化
- 可靠性和降级
- 工程化测试
- 可观测性
- 安全风控

---

## 2. 总体设计差距

### 2.1 Feed 还不是完整三路混排

当前实现：

```text
recommend pool + cold_start pool
```

成熟产品通常还会加入：

```text
recommend pool + friend timeline + cold_start pool
```

当前缺口：

- 好友时间线没有完整实现。
- 发帖后没有写扩散到好友 timeline。
- Feed 里没有固定位置强插好友动态。

后续优化：

1. 增加 `PostFanoutService`。
2. 发帖后调用 `UserClient.getFriendUserIds`。
3. 给好友写入：

```text
hanlian:user:timeline:{userId}
```

4. Feed 混排时加入好友动态，比如第 3 位优先好友帖。

优先级：P1。

---

### 2.2 已看过滤用 Redis Set，暂未使用 BloomFilter

当前实现：

```text
hanlian:feed:seen:{userId}
```

类型：Set。

优点：

- 容易理解。
- 能精确判断用户是否看过。
- 适合学习阶段。

不足：

- 用户量大、刷得多时，Set 内存会变大。
- 每个用户一个 Set，长期增长需要清理策略。

成熟产品常见做法：

- BloomFilter 降低内存。
- 或者 Set + TTL + 容量裁剪。
- 或者按天拆 key：

```text
hanlian:feed:seen:{userId}:{yyyyMMdd}
```

后续优化：

- 轻量方案：继续用 Set，但限制容量和 TTL。
- 进阶方案：接 Redisson BloomFilter。

优先级：P1。

---

### 2.3 Feed 分页 cursor 还偏简化

当前实现：

- 返回 `nextCursorPostId`。
- 实际翻页主要依赖 `seen` 过滤。
- 没有真正基于 ZSet score 做稳定游标分页。

不足：

- 推荐池重建后，顺序可能变化。
- 用户连续翻页时，分页稳定性一般。
- `nextCursorPostId` 对 Feed 的意义弱于用户帖子列表。

成熟产品做法：

- 基于 score + postId 做复合 cursor。
- 或者服务端维护一次 feed session。
- 或者用召回结果快照，客户端按 cursor 翻页。

后续优化：

```text
cursor = score + postId
```

读取 ZSet 时用 score 范围继续向后查，减少重复和跳页。

优先级：P2。

---

### 2.4 推荐算法比较简单

当前实现：

```text
score = (基础分 + 点赞数 + 评论数 * 3) / 时间衰减
```

优点：

- 简单清晰。
- 方便理解热度排序。
- 第一版足够演示。

不足：

- 没有用户兴趣画像。
- 没有内容标签。
- 没有负反馈。
- 没有曝光频控。
- 没有作者质量分。
- 没有新老用户差异策略。

成熟产品会考虑：

- 用户画像
- 内容标签
- 作者关系
- 点击 / 停留 / 点赞 / 评论 / 拉黑等行为
- 多路召回 + 排序模型

后续优化：

先不用上复杂模型，可以加这些简单因子：

- 关注作者加权
- 新帖保护时间
- 评论权重可配置
- 被举报内容降权
- 用户看过作者过多时降权

优先级：P2。

---

### 2.5 UserClient 还是桩实现

当前实现：

- `getFriendUserIds` 可能返回空。
- `isMale` 可能用 userId 奇偶做临时判断。

不足：

- 性别分桶不是真实数据。
- 好友 timeline 无法真正落地。
- user-service 不可用的降级策略还比较粗。

后续优化：

1. 接入真实 user-service gRPC。
2. 实现好友列表查询。
3. 实现用户性别查询。
4. 给 `isMale` 加本地短 TTL 缓存，避免 FeedScoreJob 频繁 RPC。

优先级：P1。

---

### 2.6 权限模型比较简单

当前实现：

- 删除帖子要求 `userId == post.userId`。
- 其他公开帖子都可看、可点赞、可评论。

不足：

- 没有隐私可见范围。
- 没有拉黑过滤。
- 没有审核中 / 违规内容处理链路。
- 删除失败时暂时没有精确区分“不存在”和“无权限”。

成熟产品会考虑：

- 公开 / 好友可见 / 仅自己可见
- 拉黑双方不可见
- 内容审核状态
- 管理员删除
- 申诉恢复

后续优化：

- 给 `posts` 增加可见范围字段。
- 查询详情 / Feed / 用户帖子列表统一做可见性过滤。
- 删除时先查帖子是否存在，再判断 owner，精确返回 404 / 403。

优先级：P2。

---

### 2.7 评论只支持一级评论

当前实现：

- `rootId = 0`
- `parentId = 0`
- `replyToUserId = 0`
- 只查一级评论。

优点：

- 简单稳定。
- 第一版验收范围清晰。

不足：

- 不能回复评论。
- 没有楼中楼展开。
- 没有评论删除。
- 没有评论点赞。

后续优化：

1. 创建评论时支持 `rootId`、`parentId`、`replyToUserId`。
2. 评论列表返回一级评论。
3. 一级评论下额外带前几条子评论。
4. 单独提供“展开更多回复”接口。

优先级：P2。

---

### 2.8 缺少消息通知链路

当前实现：

- 点赞后不通知作者。
- 评论后不通知作者。

成熟产品一般会：

- 点赞通知
- 评论通知
- 回复通知
- 系统消息

后续优化：

- 不建议 post-service 直接处理 IM。
- 应通过 `im-service` 或消息服务异步发送通知。

优先级：P2。

---

### 2.9 缺少内容审核和风控

当前实现：

- 发帖内容只校验长度。
- 评论内容只校验长度。
- 没有敏感词、图片审核、频率限制。

成熟产品会考虑：

- 发帖频控
- 评论频控
- 敏感词过滤
- 图片 NSFW 审核
- 垃圾评论识别
- 账号风险分

后续优化：

先做简单 Redis 限流：

```text
hanlian:rate:post:create:{userId}
hanlian:rate:post:comment:{userId}
```

比如 1 分钟最多发 3 条帖子、10 条评论。

优先级：P1。

---

## 3. 数据一致性与可靠性差距

### 3.1 缺少计数重算 / 修复任务

当前实现：

- 明细表实时写入。
- `post_stats` 通过 Redis 增量刷盘。

不足：

- 如果刷盘异常、Redis 丢数据、人工改库，`post_stats` 可能和明细表不一致。
- 当前没有自动修复任务。

成熟产品会有：

```text
post_likes / post_comments 明细表
  -> 定期重算
  -> 修复 post_stats
```

后续优化：

新增 `PostStatRebuildJob`：

```sql
SELECT count(*) FROM post_likes WHERE post_id = ? AND status = 1;
SELECT count(*) FROM post_comments WHERE post_id = ? AND status = 1 AND deleted = 0;
```

然后回写 `post_stats`。

优先级：P1。

---

### 3.2 Redis 异常降级不完整

当前实现依赖 Redis 的地方：

- 点赞增量
- 评论增量
- updated_set
- Feed 池
- seen 过滤

不足：

- Redis 不可用时，点赞 / 评论可能无法写增量。
- Feed 可能返回空。
- seen 过滤失效。

成熟产品会做：

- Redis 写失败日志告警。
- 核心明细表不依赖 Redis，保证 DB 先成功。
- 计数可降级为直接 DB 更新。
- Feed 可降级为 DB 最近帖子。

后续优化：

- 点赞 / 评论：Redis 写失败时可以降级直接更新 `post_stats`。
- Feed：Redis 池空时，回源查询最近正常帖子。

优先级：P1。

---

### 3.3 多实例定时任务互斥不完整

当前实现：

- 使用 `@Scheduled` 定时刷盘 / 重建推荐池。

不足：

- 如果部署多个 post-service 实例，定时任务可能重复执行。
- 重复刷盘可能导致计数错误或浪费资源。

成熟产品会使用：

- ShedLock
- Redis 分布式锁
- Quartz 集群
- XXL-JOB 等调度平台

后续优化：

给这些 job 加互斥：

- `PostStatFlushJob`
- `FeedScoreJob`

优先级：P1。

---

### 3.4 删除后的关联数据策略不完整

当前实现：

- 删除帖子时逻辑删除 `posts`。
- 清理 Feed 池里的 postId。
- 点赞、评论明细保留。

不足：

- 评论是否隐藏只依赖帖子不可见。
- 点赞记录是否保留没有单独策略。
- 删除后是否允许恢复没有设计。

成熟产品会明确：

- 删除后详情不可见。
- 作者可恢复还是不可恢复。
- 评论是否软删。
- 统计是否保留。
- 审计记录如何保存。

后续优化：

- 增加删除原因 / 删除人。
- 增加后台恢复能力。
- 定期归档删除较久的数据。

优先级：P2。

---

## 4. 性能与扩展性差距

### 4.1 Feed 详情组装存在多次 DB 查询

当前实现：

```text
FeedService
  -> 对每个 postId 调一次 PostReadService.getPostDetail
```

这会导致：

- 查 posts 多次
- 查 images 多次
- 查 stats 多次
- 查 likedByMe 多次

成熟产品会批量查：

```text
select posts where post_id in (...)
select images where post_id in (...)
select stats where post_id in (...)
select likes where user_id = ? and post_id in (...)
```

后续优化：

新增：

```text
PostReadService.listPostDetails(viewerUserId, postIds)
```

批量组装详情。

优先级：P1。

---

### 4.2 PostManager 职责偏大

当前实现：

`PostManager` 同时管理：

- posts
- post_images
- post_stats
- post_likes
- post_comments

不足：

- 类越来越大。
- 方法归属不够清晰。
- 后续协作时容易冲突。

成熟产品会拆成：

```text
PostManager
PostImageManager
PostStatManager
PostLikeManager
PostCommentManager
```

后续优化：

等功能稳定后再拆，不建议现在边做功能边大拆。

优先级：P2。

---

### 4.3 分页逻辑重复

当前重复逻辑：

- `pageSize` 默认值
- 最大 `pageSize`
- `queryLimit = size + 1`
- `hasMore`
- `nextCursor`

出现场景：

- 评论列表
- 用户帖子列表
- Feed 列表

成熟产品会抽：

```text
CursorPageResult<T>
PageSizeLimiter
```

后续优化：

先不要过早抽象。等分页接口更多、更稳定时再抽。

优先级：P3。

---

### 4.4 Redis key 缺少统一常量类

当前实现：

- key 前缀从配置读取。
- 具体 key pattern 分散在 Redis service 方法里。

不足：

- key 名拼错不容易发现。
- 后续多个类都用同一 key 时不够统一。

成熟产品可增加：

```text
PostRedisKeys
FeedRedisKeys
```

后续优化：

- 把 key pattern 集中成方法。
- 禁止业务类直接拼完整 Redis key。

优先级：P2。

---

### 4.5 配置参数硬编码较多

当前硬编码示例：

- Feed 池 TTL：7 天
- 推荐池候选数：3000
- 推荐窗口：3 天
- 推荐 / 冷启动比例：70% / 30%
- 定时任务间隔
- 内容长度限制

成熟产品会配置化：

```yaml
post:
  feed:
    pool-ttl-days: 7
    candidate-limit: 3000
    recent-window-days: 3
    recommend-ratio: 0.7
  content:
    post-max-length: 1024
    comment-max-length: 512
```

后续优化：

新建配置类，比如：

```text
PostFeatureProperties
```

优先级：P2。

---

## 5. 工程化与测试差距

### 5.1 缺少自动化集成测试

当前测试方式：

- Postman 手动测试。
- IDEA 手动 clean package。

不足：

- 容易漏测。
- 重构时不容易发现回归。
- Redis / PG 相关逻辑靠人工检查。

成熟产品会有：

- JUnit 单元测试
- SpringBootTest
- Testcontainers 启动 PostgreSQL + Redis
- CI 自动跑测试

后续优化：

先补 3 类测试：

1. 点赞幂等测试。
2. 评论创建 + 计数测试。
3. Feed 已看过滤测试。

优先级：P1。

---

### 5.2 Postman 还不是完整自动验收

当前已经有：

- 主接口请求
- 异常场景测试

不足：

- 主链路没有完整自动串联。
- `Create Post` 后没有自动把 `postId` 写入变量。
- 刷盘等待需要人工观察。

后续优化：

- 在 `Create Post` 的 Tests 里写：

```javascript
const json = pm.response.json();
pm.collectionVariables.set("postId", json.postId);
```

- 增加一键 Runner 顺序。

优先级：P2。

---

### 5.3 缺少日志规范

当前不足：

- 发帖、点赞、评论、Feed、刷盘、推荐池重建的关键日志不完整。
- 出问题时主要靠断点和数据库看。

成熟产品应有关键日志：

```text
Post created: postId={} userId={}
Like action: userId={} postId={} liked={} delta={}
Comment created: commentId={} postId={} userId={}
Post stat flush completed: processed={}
Feed returned: userId={} size={} recommend={} coldStart={}
Feed pool rebuilt: total={} male={} female={}
```

后续优化：

- 使用 SLF4J。
- ERROR 日志带异常堆栈。
- 关键链路带 `userId`、`postId`。

优先级：P1。

---

### 5.4 缺少指标和告警

当前没有完整指标：

- 接口耗时
- Feed 耗时
- Redis 增量堆积
- 刷盘失败次数
- 推荐池大小

成熟产品会接：

- Actuator
- Micrometer
- Prometheus
- Grafana

后续可观测指标：

```text
post.create.success
post.like.action
post.comment.create
post.stat.flush.processed
post.stat.flush.failed
feed.recommend.duration
feed.pool.rebuild.duration
redis.updated_set.size
```

优先级：P2。

---

## 6. 安全与风控差距

### 6.1 缺少接口限流

当前：

- 发帖不限频。
- 评论不限频。
- 点赞不限频。

风险：

- 恶意刷帖。
- 恶意刷评论。
- 点赞请求打爆 Redis / DB。

后续优化：

Redis 限流：

```text
INCR hanlian:rate:post:create:{userId}
EXPIRE 60s
```

超过阈值返回：

```text
操作太频繁，请稍后再试
```

优先级：P1。

---

### 6.2 发帖和评论缺少 requestId 幂等

当前：

- 点赞靠状态幂等。
- 发帖和评论没有 requestId。

风险：

- 客户端重试可能创建重复帖子。
- 网络抖动时用户可能重复评论。

成熟产品做法：

客户端传：

```text
requestId
```

服务端记录：

```text
hanlian:idempotent:{userId}:{requestId}
```

后续优化：

- 发帖接口支持 requestId。
- 评论接口支持 requestId。
- 短 TTL 防重复。

优先级：P2。

---

### 6.3 缺少内容安全审核

当前：

- 只做长度校验。

成熟产品会接：

- 敏感词
- 图片审核
- 垃圾内容识别
- 人工审核后台

后续优化：

- 第一阶段先做敏感词。
- 第二阶段接图片审核。
- 第三阶段接审核状态流转。

优先级：P2。

---

## 7. 代码细节差距

### 7.1 异常类型还可以更细

当前：

```text
BusinessException(HttpStatus, message)
```

不足：

- 没有业务错误码。
- 前端只能依赖 message。
- 多语言扩展不方便。

成熟产品会有：

```java
POST_NOT_FOUND
NO_PERMISSION
CONTENT_TOO_LONG
RATE_LIMITED
```

后续优化：

新增：

```text
ErrorCode enum
BusinessException(ErrorCode)
```

优先级：P2。

---

### 7.2 gRPC 错误返回还不够丰富

当前：

- 能映射 gRPC status。
- description 里放错误信息。

不足：

- 没有业务错误码 metadata。
- 客户端无法稳定按错误码处理。

成熟产品做法：

- gRPC status + error code metadata。
- 或统一错误响应 message。

后续优化：

在 `GrpcExceptionHandler` 中附加 metadata：

```text
error-code: POST_NOT_FOUND
```

优先级：P2。

---

### 7.3 FeedService 捕获异常范围偏宽

当前：

```java
catch (Exception e) {
    return null;
}
```

用途：

- Feed 中遇到已删除帖子时跳过。

不足：

- 可能把真实 bug 也吞掉。
- 不利于排查问题。

成熟产品做法：

- 只捕获 `BusinessException` 且状态为 `NOT_FOUND`。
- 其他异常打 ERROR 日志并抛出或告警。

后续优化：

```java
catch (BusinessException e) {
    if (e.getStatus() == HttpStatus.NOT_FOUND) {
        return null;
    }
    throw e;
}
```

优先级：P1。

---

### 7.4 代码注释和编码需要统一

当前曾出现：

- 终端里中文注释乱码。

原因可能是：

- PowerShell 编码显示问题。
- IDEA 文件编码未统一。

后续优化：

统一：

```text
File Encoding = UTF-8
Project Encoding = UTF-8
Properties Encoding = UTF-8
```

优先级：P2。

---

## 8. 后续优化优先级建议

### 8.1 优先做 P1

这些对项目质量提升明显，且不会太离谱：

1. 补关键日志。
2. Feed 详情批量查询优化。
3. Redis 异常降级。
4. 计数重算 / 修复任务。
5. ShedLock 或 Redis 锁防止多实例 Job 重复。
6. 接真实 UserClient。
7. 发帖 / 评论 Redis 限流。
8. FeedService 收窄异常捕获范围。

### 8.2 再做 P2

这些更像产品增强：

1. 好友时间线写扩散。
2. BloomFilter 替换 seen Set。
3. Feed score + postId 稳定 cursor。
4. 楼中楼评论。
5. gRPC 错误码 metadata。
6. ErrorCode 枚举。
7. 配置参数集中化。
8. 敏感词和图片审核。

### 8.3 最后做 P3

这些可以等代码更稳定后做：

1. Manager 拆分。
2. 分页工具抽象。
3. Redis key 常量类。
4. 历史数据归档。
5. 更复杂推荐策略。

---

## 9. 推荐下一轮迭代路线

如果你后续继续优化，我建议按这个顺序：

```text
第 1 步：补关键日志
第 2 步：收窄 FeedService 的 catch Exception
第 3 步：加 Redis 降级和错误日志
第 4 步：做 Feed 批量详情查询
第 5 步：做 post_stats 重算任务
第 6 步：接真实 UserClient
第 7 步：做好友时间线
第 8 步：加限流
第 9 步：补集成测试
第 10 步：接 ShedLock / 指标 / 告警
```

这个顺序的好处是：

- 前几步偏工程质量，不会破坏主链路。
- 中间几步补性能和可靠性。
- 后面再做推荐和产品能力增强。

---

## 10. 总结

当前 post-service 已经不是“玩具 demo”，而是一个具备主链路、分层、Redis 计数、Feed 池、异常处理和 Postman 验收的第一版服务。

它与成熟产品的主要差距不在“能不能跑”，而在：

- 大流量下是否稳定
- 多实例下是否安全
- 数据不一致时能否修复
- Redis / user-service 故障时能否降级
- Feed 是否足够个性化
- 是否有日志、指标、测试支撑长期维护

后续优化时，不建议一口气重构全部。应该按优先级逐步补：

```text
先可靠性，再性能，再产品体验，最后做结构重构。
```

