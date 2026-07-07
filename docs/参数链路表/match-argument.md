# match-service 参数链路表

> 服务：`match-service`  
> proto：`proto/match/src/main/proto/match.proto`  
> Java 包：`com.aurora.dating.match`  
> 当前状态：proto 参数、P0 数据表、实体类、Mapper/Manager 骨架已定义；业务 RPC 还在开发中。本文只记录已经定义的接口参数和实体字段，不把未实现链路写成已完成。

---

## 1. 接口总览

| RPC | 作用 | Request | Response | 当前说明 |
|---|---|---|---|---|
| `Ping` | gRPC 连通性检查 | `PingRequest` | `PingResponse` | 已实现 |
| `GetTodayFeed` | 拉取今日卡片 | `GetTodayFeedRequest` | `GetTodayFeedResponse` | proto 已定义 |
| `SwipeCard` | 左划 / 右划 / Super Hi 划卡 | `SwipeCardRequest` | `SwipeCardResponse` | proto 已定义 |
| `SuperHi` | 单独处理 Super Hi | `SuperHiRequest` | `SuperHiResponse` | proto 已定义 |
| `ListLikesOfMe` | 查询喜欢我的人 | `ListLikesOfMeRequest` | `ListLikesOfMeResponse` | proto 已定义 |
| `ListVisitsOfMe` | 查询访问过我的人 | `ListVisitsOfMeRequest` | `ListVisitsOfMeResponse` | proto 已定义 |
| `RecordVisit` | 记录一次访问 | `RecordVisitRequest` | `RecordVisitResponse` | proto 已定义 |

---

## 2. 枚举参数

### 2.1 UserType

| proto 值 | 数字值 | 含义 |
|---|---:|---|
| `USER_TYPE_UNSPECIFIED` | 0 | 未指定。业务入参不应使用，服务端后续要校验拒绝 |
| `USER_TYPE_BH` | 1 | 真人用户，Biological Human |
| `USER_TYPE_DH` | 2 | 数字人，Digital Human |

对应实体字段：

| 实体类 | 字段 | DB 字段 |
|---|---|---|
| `UserSwipeHistoryEntity` | `targetUserType` | `user_swipe_history.target_user_type` |

### 2.2 SwipeAction

| proto 值 | 数字值 | 含义 |
|---|---:|---|
| `SWIPE_ACTION_UNSPECIFIED` | 0 | 未指定。业务入参不应使用 |
| `SWIPE_ACTION_LEFT` | 1 | 左划，不喜欢 |
| `SWIPE_ACTION_RIGHT` | 2 | 右划，喜欢 |
| `SWIPE_ACTION_SUPER_HI` | 3 | Super Hi |

对应实体字段：

| 实体类 | 字段 | DB 字段 |
|---|---|---|
| `UserSwipeHistoryEntity` | `action` | `user_swipe_history.action` |

### 2.3 MatchSource

| proto 值 | 数字值 | 含义 |
|---|---:|---|
| `MATCH_SOURCE_UNSPECIFIED` | 0 | 未指定。业务写入时不应使用 |
| `MATCH_SOURCE_SWIPE_MATCH` | 1 | 普通右划互相喜欢产生配对 |
| `MATCH_SOURCE_SWIPE_SUPER_HI` | 2 | Super Hi 直接产生配对 |

对应实体字段：

| 实体类 | 字段 | DB 字段 |
|---|---|---|
| `MatchPairEntity` | `source` | `match_pairs.source` |

---

## 3. 通用消息

### 3.1 PingRequest

| 字段 | 类型 | 必填 | 含义 |
|---|---|---|---|
| `message` | `string` | 否 | 调用方传入的测试文本 |

### 3.2 PingResponse

| 字段 | 类型 | 含义 |
|---|---|---|
| `message` | `string` | 服务端返回的 pong 文本 |

### 3.3 FeedCard

| 字段 | 类型 | 含义 |
|---|---|---|
| `target_user_id` | `int64` | 卡片上的目标用户 ID |
| `target_user_type` | `UserType` | 目标用户类型：BH / DH |
| `nickname` | `string` | 目标用户昵称 |
| `age` | `int32` | 目标用户年龄 |
| `photo_keys` | `repeated string` | 目标用户照片 key 列表 |
| `distance_km` | `double` | 距离，单位 km。DH 或未知距离时可返回 0 |

当前边界：

- P0 本服务闭环阶段，卡片数据可以先来自本服务简化数据。
- 后续进入真实召回时，需要补 `user-service.ListDhCandidates` / `user-service.NearbyUsers`，并通过 `user-service.BatchGetProfile` 补展示字段。

### 3.4 MatchInfo

| 字段 | 类型 | 含义 |
|---|---|---|
| `match_id` | `int64` | 配对 ID，对应 `match_pairs.match_id` |
| `user_id_a` | `int64` | 配对用户 A |
| `user_id_b` | `int64` | 配对用户 B |
| `source` | `MatchSource` | 配对来源 |
| `matched_at_unix_ms` | `int64` | 配对成功时间，Unix 毫秒 |

对应实体：

| proto 字段 | 实体类 | 实体字段 | DB 字段 |
|---|---|---|---|
| `match_id` | `MatchPairEntity` | `matchId` | `match_pairs.match_id` |
| `source` | `MatchPairEntity` | `source` | `match_pairs.source` |
| `matched_at_unix_ms` | `MatchPairEntity` | `matchedAt` | `match_pairs.matched_at` |

`user_id_a / user_id_b` 是接口展示字段；DB 内部为了唯一约束，拆成 `user_id_low / user_id_high`。

---

## 4. GetTodayFeed

### 4.1 解决什么问题

用户进入首页时，拉取一批今日可展示的滑卡卡片。

### 4.2 Request：GetTodayFeedRequest

| 字段 | 类型 | 必填 | 含义 | 建议校验 |
|---|---|---|---|---|
| `user_id` | `int64` | 是 | 当前拉 feed 的用户 ID | 必须大于 0 |
| `count` | `int32` | 否 | 本次希望拉几张卡片 | 建议默认 5，上限后续可限制为 20 |

### 4.3 Response：GetTodayFeedResponse

| 字段 | 类型 | 含义 |
|---|---|---|
| `cards` | `repeated FeedCard` | 返回的卡片列表 |
| `exhausted` | `bool` | 是否已经没有可划卡片或今日配额耗尽 |
| `cards_remaining_today` | `int32` | 今日剩余可划卡片数 |

### 4.4 当前实体关联

`GetTodayFeed` 当前没有单独的 feed 持久化实体。P0 阶段可以从 Redis LIST 或简化候选来源返回；用户真正划卡后才写入 `UserSwipeHistoryEntity`。

---

## 5. SwipeCard

### 5.1 解决什么问题

记录用户对某张卡片的划卡动作，并在右划 / Super Hi 时判断是否产生配对。

### 5.2 Request：SwipeCardRequest

| 字段 | 类型 | 必填 | 含义 | 建议校验 |
|---|---|---|---|---|
| `user_id` | `int64` | 是 | 发起划卡的用户 ID | 必须大于 0 |
| `target_user_id` | `int64` | 是 | 被划卡的目标用户 ID | 必须大于 0，不能等于 `user_id` |
| `target_user_type` | `UserType` | 是 | 目标用户类型 | 只能是 `USER_TYPE_BH` 或 `USER_TYPE_DH` |
| `action` | `SwipeAction` | 是 | 划卡动作 | 只能是 LEFT / RIGHT / SUPER_HI |

### 5.3 Response：SwipeCardResponse

| 字段 | 类型 | 含义 |
|---|---|---|
| `matched` | `bool` | 本次划卡是否产生配对 |
| `match_info` | `MatchInfo` | 如果产生配对，返回配对信息 |
| `idempotent` | `bool` | 是否命中重复请求幂等返回 |
| `cards_remaining_today` | `int32` | 今日剩余可划卡片数 |
| `right_swipes_remaining_today` | `int32` | 今日剩余右划次数 |

### 5.4 写入实体：UserSwipeHistoryEntity

| proto 入参 | 实体字段 | DB 字段 | 含义 |
|---|---|---|---|
| `user_id` | `userId` | `user_swipe_history.user_id` | 发起划卡的人 |
| `target_user_id` | `targetUserId` | `user_swipe_history.target_user_id` | 被划卡的人 |
| `target_user_type` | `targetUserType` | `user_swipe_history.target_user_type` | 被划卡的人类型 |
| `action` | `action` | `user_swipe_history.action` | 划卡动作 |
| 服务端生成 | `matchId` | `user_swipe_history.match_id` | 若本次产生配对，记录配对 ID |
| 服务端生成 | `swipedAt` | `user_swipe_history.swiped_at` | 划卡时间 |
| 服务端生成 | `status` | `user_swipe_history.status` | 状态，当前 1 表示有效 |

幂等依赖：

| DB 约束 | 含义 |
|---|---|
| `uk_user_swipe_history_user_target (user_id, target_user_id)` | 同一个用户对同一个目标只允许有一条划卡记录 |

### 5.5 可能写入实体：LikeRecordEntity

右划但没有立即配对时，可以写入单向喜欢记录。

| 来源 | 实体字段 | DB 字段 | 含义 |
|---|---|---|---|
| `user_id` | `fromUserId` | `like_record.from_user_id` | 喜欢发起人 |
| `target_user_id` | `toUserId` | `like_record.to_user_id` | 被喜欢的人 |
| 服务端生成 | `source` | `like_record.source` | 当前可用 1 表示右划 |
| 服务端生成 | `likedAt` | `like_record.liked_at` | 喜欢时间 |

### 5.6 可能写入实体：MatchPairEntity

互相右划或 Super Hi 产生配对时写入。

| 来源 | 实体字段 | DB 字段 | 含义 |
|---|---|---|---|
| `user_id / target_user_id` | `userIdLow` | `match_pairs.user_id_low` | 两个用户 ID 中较小的一个 |
| `user_id / target_user_id` | `userIdHigh` | `match_pairs.user_id_high` | 两个用户 ID 中较大的一个 |
| 服务端生成 | `source` | `match_pairs.source` | `1=普通互划`，`2=Super Hi` |
| 服务端生成 | `matchedAt` | `match_pairs.matched_at` | 配对成功时间 |

唯一约束：

| DB 约束 | 含义 |
|---|---|
| `uk_match_pairs_user_pair (user_id_low, user_id_high)` | 同两个人只能配对一次 |

---

## 6. SuperHi

### 6.1 解决什么问题

单独处理 Super Hi。Super Hi 语义比普通右划更强，后续会接订阅免费额度和金币扣减。

### 6.2 Request：SuperHiRequest

| 字段 | 类型 | 必填 | 含义 | 建议校验 |
|---|---|---|---|---|
| `user_id` | `int64` | 是 | 发起 Super Hi 的用户 ID | 必须大于 0 |
| `target_user_id` | `int64` | 是 | 被 Super Hi 的目标用户 ID | 必须大于 0，不能等于 `user_id` |
| `target_user_type` | `UserType` | 是 | 目标用户类型 | 只能是 BH / DH |
| `use_coin` | `bool` | 否 | 免费 Super Hi 不足时，是否允许使用金币 | `true` 表示允许扣金币 |

### 6.3 Response：SuperHiResponse

| 字段 | 类型 | 含义 |
|---|---|---|
| `matched` | `bool` | 是否配对成功 |
| `match_info` | `MatchInfo` | 配对信息 |
| `used_free_quota` | `bool` | 是否使用了订阅赠送的免费 Super Hi |
| `used_coin` | `bool` | 是否使用金币购买 Super Hi |
| `super_hi_remaining_today` | `int32` | 今日剩余免费 Super Hi 次数 |
| `right_swipes_remaining_today` | `int32` | 今日剩余右划次数。Super Hi 也会消耗右划次数 |

### 6.4 实体关联

`SuperHi` 会复用：

| 实体类 | 作用 |
|---|---|
| `UserSwipeHistoryEntity` | 记录一次 `SWIPE_ACTION_SUPER_HI` 划卡历史 |
| `MatchPairEntity` | 记录 Super Hi 产生的配对 |

当前 P0 本服务闭环阶段，可先完成本服务记录和配对；后续接 payment-service 时再处理 `use_coin` 对应的真实扣金币逻辑。

---

## 7. ListLikesOfMe

### 7.1 解决什么问题

查询“喜欢我的人”列表。

### 7.2 Request：ListLikesOfMeRequest

| 字段 | 类型 | 必填 | 含义 | 建议校验 |
|---|---|---|---|---|
| `user_id` | `int64` | 是 | 被查询的人，也就是“我” | 必须大于 0 |
| `page_size` | `int32` | 否 | 每页数量 | 建议默认 20，上限 50 |
| `cursor_liked_at_unix_ms` | `int64` | 否 | 翻页游标，上一页最后一条的喜欢时间 | 0 表示第一页 |

### 7.3 Response：ListLikesOfMeResponse

| 字段 | 类型 | 含义 |
|---|---|---|
| `likes` | `repeated LikeUserVO` | 喜欢我的用户列表 |
| `next_cursor_liked_at_unix_ms` | `int64` | 下一页游标 |
| `has_more` | `bool` | 是否还有下一页 |

### 7.4 LikeUserVO

| 字段 | 类型 | 含义 |
|---|---|---|
| `from_user_id` | `int64` | 喜欢我的用户 ID |
| `nickname` | `string` | 喜欢我的用户昵称 |
| `age` | `int32` | 喜欢我的用户年龄 |
| `photo_keys` | `repeated string` | 喜欢我的用户照片 key |
| `liked_at_unix_ms` | `int64` | 喜欢发生时间，Unix 毫秒 |

### 7.5 实体关联：LikeRecordEntity

| 实体字段 | DB 字段 | 含义 |
|---|---|---|
| `id` | `like_record.id` | 内部自增主键 |
| `fromUserId` | `like_record.from_user_id` | 喜欢发起人 |
| `toUserId` | `like_record.to_user_id` | 被喜欢的人，也就是接口入参 `user_id` |
| `source` | `like_record.source` | 来源，当前 1 表示右划，2 预留 DH 计划 |
| `status` | `like_record.status` | 状态，1 表示有效 |
| `likedAt` | `like_record.liked_at` | 喜欢发生时间 |
| `createdAt` | `like_record.created_at` | 创建时间 |
| `updatedAt` | `like_record.updated_at` | 更新时间 |

当前边界：

- `LikeUserVO` 的昵称、年龄、照片需要用户资料能力补充。
- P0 本服务闭环可以先返回 ID 和时间；后续真实展示时需要调用 user-service。

---

## 8. ListVisitsOfMe

### 8.1 解决什么问题

查询“访问过我的人”列表。

### 8.2 Request：ListVisitsOfMeRequest

| 字段 | 类型 | 必填 | 含义 | 建议校验 |
|---|---|---|---|---|
| `user_id` | `int64` | 是 | 被查询的人，也就是“我” | 必须大于 0 |
| `page_size` | `int32` | 否 | 每页数量 | 建议默认 20，上限 50 |
| `cursor_visited_at_unix_ms` | `int64` | 否 | 翻页游标，上一页最后一条访问时间 | 0 表示第一页 |

### 8.3 Response：ListVisitsOfMeResponse

| 字段 | 类型 | 含义 |
|---|---|---|
| `visits` | `repeated VisitUserVO` | 访问过我的用户列表 |
| `next_cursor_visited_at_unix_ms` | `int64` | 下一页游标 |
| `has_more` | `bool` | 是否还有下一页 |

### 8.4 VisitUserVO

| 字段 | 类型 | 含义 |
|---|---|---|
| `from_user_id` | `int64` | 访问我的用户 ID |
| `nickname` | `string` | 访问我的用户昵称 |
| `age` | `int32` | 访问我的用户年龄 |
| `photo_keys` | `repeated string` | 访问我的用户照片 key |
| `visited_at_unix_ms` | `int64` | 最近访问时间，Unix 毫秒 |
| `visit_count` | `int32` | 同一访问人对我的累计访问次数 |

### 8.5 实体关联：VisitRecordEntity

| 实体字段 | DB 字段 | 含义 |
|---|---|---|
| `id` | `visit_record.id` | 内部自增主键 |
| `fromUserId` | `visit_record.from_user_id` | 访问发起人 |
| `toUserId` | `visit_record.to_user_id` | 被访问的人，也就是接口入参 `user_id` |
| `visitCount` | `visit_record.visit_count` | 同一访问关系累计访问次数 |
| `status` | `visit_record.status` | 状态，1 表示有效 |
| `visitedAt` | `visit_record.visited_at` | 最近访问时间 |
| `createdAt` | `visit_record.created_at` | 创建时间 |
| `updatedAt` | `visit_record.updated_at` | 更新时间 |

当前边界：

- `VisitUserVO` 的昵称、年龄、照片需要用户资料能力补充。
- P0 本服务闭环可以先返回 ID、时间、次数。

---

## 9. RecordVisit

### 9.1 解决什么问题

记录某个用户访问了另一个用户。重复访问同一目标时累计 `visit_count`，并刷新最近访问时间。

### 9.2 Request：RecordVisitRequest

| 字段 | 类型 | 必填 | 含义 | 建议校验 |
|---|---|---|---|---|
| `viewer_user_id` | `int64` | 是 | 访问发起人 | 必须大于 0 |
| `target_user_id` | `int64` | 是 | 被访问的人 | 必须大于 0；等于 `viewer_user_id` 时建议直接返回 `ok=true`，不落库 |

### 9.3 Response：RecordVisitResponse

| 字段 | 类型 | 含义 |
|---|---|---|
| `ok` | `bool` | 是否已接收记录请求。P0 同步写入时表示写入成功；后续异步写入时只表示已接收 |

### 9.4 实体关联：VisitRecordEntity

| proto 入参 | 实体字段 | DB 字段 | 写入逻辑 |
|---|---|---|---|
| `viewer_user_id` | `fromUserId` | `visit_record.from_user_id` | 访问发起人 |
| `target_user_id` | `toUserId` | `visit_record.to_user_id` | 被访问的人 |
| 服务端生成 | `visitCount` | `visit_record.visit_count` | 首次访问为 1，重复访问累加 |
| 服务端生成 | `visitedAt` | `visit_record.visited_at` | 每次访问刷新 |
| 服务端生成 | `status` | `visit_record.status` | 1 表示有效 |

幂等 / 并发依赖：

| DB 约束 / SQL | 含义 |
|---|---|
| `uk_visit_record_from_to (from_user_id, to_user_id)` | 同一访问关系只保留一条记录 |
| `INSERT ... ON CONFLICT ... DO UPDATE` | 重复访问时原子累加 `visit_count` 并刷新 `visited_at` |

---

## 10. 服务实体类总表

### 10.1 UserSwipeHistoryEntity

Java 文件：`dating-server/match-service/src/main/java/com/aurora/dating/match/entity/UserSwipeHistoryEntity.java`  
DB 表：`user_swipe_history`

| 实体字段 | Java 类型 | DB 字段 | DB 类型 | 含义 |
|---|---|---|---|---|
| `id` | `Long` | `id` | `BIGSERIAL` | 内部自增主键 |
| `userId` | `Long` | `user_id` | `BIGINT` | 发起划卡的用户 ID |
| `targetUserId` | `Long` | `target_user_id` | `BIGINT` | 被划卡的目标用户 ID |
| `targetUserType` | `Integer` | `target_user_type` | `SMALLINT` | 目标用户类型：1 BH，2 DH |
| `action` | `Integer` | `action` | `SMALLINT` | 划卡动作：1 左划，2 右划，3 Super Hi |
| `status` | `Integer` | `status` | `SMALLINT` | 状态：1 有效 |
| `matchId` | `Long` | `match_id` | `BIGINT` | 本次划卡产生的配对 ID |
| `swipedAt` | `OffsetDateTime` | `swiped_at` | `TIMESTAMPTZ` | 划卡发生时间 |
| `createdAt` | `OffsetDateTime` | `created_at` | `TIMESTAMPTZ` | 创建时间 |
| `updatedAt` | `OffsetDateTime` | `updated_at` | `TIMESTAMPTZ` | 更新时间 |

### 10.2 MatchPairEntity

Java 文件：`dating-server/match-service/src/main/java/com/aurora/dating/match/entity/MatchPairEntity.java`  
DB 表：`match_pairs`

| 实体字段 | Java 类型 | DB 字段 | DB 类型 | 含义 |
|---|---|---|---|---|
| `matchId` | `Long` | `match_id` | `BIGSERIAL` | 配对 ID |
| `userIdLow` | `Long` | `user_id_low` | `BIGINT` | 两个用户 ID 中较小的一个 |
| `userIdHigh` | `Long` | `user_id_high` | `BIGINT` | 两个用户 ID 中较大的一个 |
| `source` | `Integer` | `source` | `SMALLINT` | 配对来源：1 普通互划，2 Super Hi |
| `status` | `Integer` | `status` | `SMALLINT` | 状态：1 有效 |
| `matchedAt` | `OffsetDateTime` | `matched_at` | `TIMESTAMPTZ` | 配对成功时间 |
| `createdAt` | `OffsetDateTime` | `created_at` | `TIMESTAMPTZ` | 创建时间 |
| `updatedAt` | `OffsetDateTime` | `updated_at` | `TIMESTAMPTZ` | 更新时间 |

### 10.3 LikeRecordEntity

Java 文件：`dating-server/match-service/src/main/java/com/aurora/dating/match/entity/LikeRecordEntity.java`  
DB 表：`like_record`

| 实体字段 | Java 类型 | DB 字段 | DB 类型 | 含义 |
|---|---|---|---|---|
| `id` | `Long` | `id` | `BIGSERIAL` | 内部自增主键 |
| `fromUserId` | `Long` | `from_user_id` | `BIGINT` | 喜欢发起人 |
| `toUserId` | `Long` | `to_user_id` | `BIGINT` | 被喜欢的人 |
| `source` | `Integer` | `source` | `SMALLINT` | 来源：1 右划，2 DH 计划 |
| `status` | `Integer` | `status` | `SMALLINT` | 状态：1 有效 |
| `likedAt` | `OffsetDateTime` | `liked_at` | `TIMESTAMPTZ` | 喜欢发生时间 |
| `createdAt` | `OffsetDateTime` | `created_at` | `TIMESTAMPTZ` | 创建时间 |
| `updatedAt` | `OffsetDateTime` | `updated_at` | `TIMESTAMPTZ` | 更新时间 |

### 10.4 VisitRecordEntity

Java 文件：`dating-server/match-service/src/main/java/com/aurora/dating/match/entity/VisitRecordEntity.java`  
DB 表：`visit_record`

| 实体字段 | Java 类型 | DB 字段 | DB 类型 | 含义 |
|---|---|---|---|---|
| `id` | `Long` | `id` | `BIGSERIAL` | 内部自增主键 |
| `fromUserId` | `Long` | `from_user_id` | `BIGINT` | 访问发起人 |
| `toUserId` | `Long` | `to_user_id` | `BIGINT` | 被访问的人 |
| `visitCount` | `Integer` | `visit_count` | `INT` | 同一访问关系累计访问次数 |
| `status` | `Integer` | `status` | `SMALLINT` | 状态：1 有效 |
| `visitedAt` | `OffsetDateTime` | `visited_at` | `TIMESTAMPTZ` | 最近访问时间 |
| `createdAt` | `OffsetDateTime` | `created_at` | `TIMESTAMPTZ` | 创建时间 |
| `updatedAt` | `OffsetDateTime` | `updated_at` | `TIMESTAMPTZ` | 更新时间 |

---

## 11. 当前边界

1. 本文是“参数链路表”，不是完整接口链路设计；完整链路后续仍应维护到 `docs/接口链路设计/match-design.md`。
2. 当前 match-service 仍采用“本服务闭环”开发策略，暂不依赖 user-service 真实召回。
3. 后续一旦进入真实候选召回，必须补 user-service 的 `ListDhCandidates` 和 `NearbyUsers`，match-service 不能直连 user 表。
4. `FeedCard / LikeUserVO / VisitUserVO` 中的昵称、年龄、照片等展示字段，最终应来自 user-service。
5. 当前实体类只覆盖 P0 四张表：划卡历史、配对、喜欢记录、访问记录；D1 推荐、DH 任务、订阅缓存等 P1/P2 能力还没有实体。
