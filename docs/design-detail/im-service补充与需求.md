# im-service 补充与需求

> 记录时间：2026-07-04  
> 目的：暂停 im-service 开发前，把当前完成情况、真实边界、未完成需求和后续推荐顺序记录下来，方便下次继续开发。

---

## 1. 当前背景

`user-service` 和 `post-service` 已经进入较高完成度，当前曾计划继续开发 `im-service`。

根据 `docs/design/im-service-design.md`，`im-service` 的定位是：

```text
平台所有即时通讯能力的唯一收口层。
```

也就是说：

- `mobile-gateway` 不直接调用 OpenIM。
- `match-service` 不直接发 IM 消息。
- `post-service` 后续如果要发点赞 / 评论通知，也不直接碰 OpenIM。
- 所有 IM 相关能力统一经 `im-service` 的 gRPC 接口收口。

当前用户说明：

```text
OpenIM 后台暂时还没有。
```

因此，现阶段不能继续接真实 OpenIM REST API，只能先保留 mock / 本地能力，后续等 OpenIM 后台准备好再接真实 provider。

---

## 2. 当前已完成内容

### 2.1 proto 已补充 IM P0 接口

涉及文件：

```text
proto/im/src/main/proto/im.proto
proto/im/im.proto
```

当前 `ImService` 已包含：

```text
Ping
OnRawCallback
RegisterImUser
GetImToken
EnsureConversation
SendSystemMessage
GenerateCallToken
ListOnlineUserIds
ListRecentOfflineUsers
```

这些接口的含义：

| RPC | 当前作用 |
|---|---|
| `Ping` | gRPC 连通性测试 |
| `OnRawCallback` | 接收 OpenIM / mock provider 原始回调 |
| `RegisterImUser` | 注册 IM 用户映射 |
| `GetImToken` | 获取 IM token，目前是 mock token |
| `EnsureConversation` | 幂等创建单聊会话 |
| `SendSystemMessage` | 保存系统消息，目前不真实推 OpenIM |
| `GenerateCallToken` | 生成通话 token，目前是 mock token |
| `ListOnlineUserIds` | 从 Redis 查询在线用户 |
| `ListRecentOfflineUsers` | 从 PG 查询最近下线用户 |

注意：

```text
proto 是 gateway / match-service 等服务调用 im-service 的契约。
前端不直接使用 proto，前端仍然通过 HTTP/JSON 调 gateway。
```

---

### 2.2 im-service gRPC 服务端已接入新增 RPC

涉及文件：

```text
dating-server/im-service/src/main/java/com/aurora/dating/im/grpc/ImGrpcService.java
```

当前 `ImGrpcService` 已经继承：

```text
ImServiceGrpc.ImServiceImplBase
```

并实现了 proto 中定义的 gRPC 方法。

代码链路大体是：

```text
ImGrpcService
  -> ImUserService
  -> ConversationService
  -> SystemMessageService
  -> CallTokenService
  -> PresenceService
  -> CallbackService
```

---

### 2.3 已新增本地业务 service

涉及目录：

```text
dating-server/im-service/src/main/java/com/aurora/dating/im/service
```

当前已新增：

| 类 | 当前职责 |
|---|---|
| `ImUserService` | 本地 IM 用户注册、生成 mock IM token |
| `ConversationService` | 幂等创建本地会话 |
| `SystemMessageService` | 保存系统消息，当前不真实推送 |
| `CallTokenService` | 生成 mock 通话 token |
| `PresenceService` | 维护 Redis 在线状态和 PG 在线会话 |
| `CallbackService` | 解析基础 raw callback，当前支持 online / offline |

---

### 2.4 已新增数据库 migration

涉及文件：

```text
dating-server/im-service/src/main/resources/db/migration/V1__im_core_tables.sql
```

当前新增表：

| 表 | 作用 |
|---|---|
| `im_registered_user` | 业务 user_id 与 IM user_id 的映射 |
| `im_conversation` | 单聊会话记录 |
| `im_system_message` | 系统消息本地记录 |
| `user_online_session` | 用户上线 / 下线 / 在线时长历史 |

当前新增索引：

```text
idx_user_online_session_open
idx_user_online_session_offline_at
```

用途：

- 支持查询当前打开的在线会话。
- 支持按 `offline_at` 查询最近下线用户。

---

### 2.5 Redis 在线状态已设计并实现

当前 Redis key：

```text
hanlian:im:presence:online
```

类型：

```text
ZSet
```

结构：

```text
member = userId
score  = onlineAtMs
```

当前用途：

- 用户上线时写入 ZSet。
- 用户下线时从 ZSet 移除。
- `ListOnlineUserIds` 从该 ZSet 按时间窗口查询在线用户。

注意：

```text
该 key 后续应该继续作为 im-service 的内部 key。
其他服务不要直接读 Redis，应该通过 im-service gRPC 查询。
```

---

### 2.6 application.yml 已补充配置

涉及文件：

```text
dating-server/im-service/src/main/resources/application.yml
```

已补充：

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    table: flyway_history_im

app:
  cache:
    key-prefix: ${REDIS_KEY_PREFIX:hanlian}

im:
  token:
    ttl-seconds: 604800
  call:
    ttl-seconds: 1800
```

当前还没有真实 OpenIM 配置，因为 OpenIM 后台暂时还没有。

---

### 2.7 已补充接口验收文档

涉及文件：

```text
docs/design-detail/im-service-接口验收文档.md
```

内容包括：

- 当前完成进度。
- gRPC 验收命令。
- HTTP debug 接口。
- DB 验收项。
- Redis 验收项。
- 异常验收项。
- 当前实现与技术方案的差异。

---

### 2.8 已完成编译验证

已执行：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\im
mvn clean install -DskipTests
```

结果：

```text
BUILD SUCCESS
```

已执行：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl im-service -am -DskipTests package
```

结果：

```text
BUILD SUCCESS
```

---

## 3. 当前真实完成度判断

当前 `im-service` 处于：

```text
P0 骨架已建立，但真实 IM 能力未接入。
```

已经能做到：

- gRPC 服务可以编译。
- proto 契约已经扩展。
- 基础 RPC 已有服务端实现。
- 本地注册用户表、会话表、系统消息表、在线会话表已准备。
- 在线状态 Redis + PG 链路已具备。
- callback 可以处理基础 online / offline。

还不能说完成：

- 真实 OpenIM 用户注册。
- 真实 OpenIM token 获取。
- 真实 OpenIM 系统消息推送。
- 真实 OpenIM 消息回调解析。
- LiveKit 真 token。
- AI 自动回复。
- 聊天扣金币。
- 反导流拦截。
- typing 通知。
- 消息流水 `chat_messages`。
- provider adaptor / sender 完整抽象。

---

## 4. 当前 mock 边界

以下能力当前是 mock，不能当生产功能：

| 功能 | 当前状态 | 后续替换方向 |
|---|---|---|
| `GetImToken` | 返回 `mock-im-token-*` | 接 OpenIM `/auth/get_user_token` |
| `SendSystemMessage` | 只写 `im_system_message` | 接 OpenIM business notification |
| `GenerateCallToken` | 返回 `mock-call-token-*` | 接 LiveKit JWT 签发 |
| `OnRawCallback` | 只解析 online / offline 简化字段 | 接 OpenIM 完整 callback payload |

重要提醒：

```text
OpenIM 后台还没有，所以不要继续写真实 OpenIM REST 调用。
否则代码无法真实验收，只会变成配置空转。
```

---

## 5. 后续需求清单

### 5.1 短期优先级：先做不依赖 OpenIM 后台的内部能力

推荐继续顺序：

```text
1. 抽 ImProviderClient 接口
2. 新增 MockImProviderClient
3. 将 ImUserService / SystemMessageService / CallTokenService 中的 mock 逻辑移动到 provider
4. 补 chat_messages 表
5. 补消息记录 service
6. 抽 callback adaptor
7. 实现 before-send / after-send 的内部事件模型
8. 做反导流检测
```

这样做的好处：

- 不依赖 OpenIM 后台。
- 当前代码还能真实编译和本地验收。
- 后续 OpenIM 准备好时，只新增 `OpenImProviderClient`，不用大改业务 service。

---

### 5.2 Provider 抽象需求

目标结构：

```text
ImProviderClient
  -> MockImProviderClient
  -> OpenImProviderClient（后续）
```

建议接口方法：

```text
registerUser(imUserId, nickname, avatar)
getUserToken(imUserId, platform)
sendSystemMessage(toUserId, payload)
ensureConversation(userIdA, userIdB)
```

当前先实现：

```text
MockImProviderClient
```

后续 OpenIM 后台准备好后，再实现：

```text
OpenImProviderClient
```

---

### 5.3 Callback adaptor 需求

技术方案中提到：

```text
ImProviderAdaptor
```

目标结构：

```text
CallbackService
  -> ImProviderAdaptor
     -> MockImAdaptor
     -> OpenImAdaptor（后续）
  -> ImEventDispatcher
```

这样做的原因：

```text
不同 IM 厂商的 callback JSON 格式不同。
业务层不应该直接依赖 OpenIM 字段。
```

当前代码里 `CallbackService` 直接解析：

```text
callbackCommand
userID
platform
```

后续需要拆出去。

---

### 5.4 消息流水需求

后续需要新增：

```text
chat_messages
```

建议字段：

```text
message_id
from_user_id
to_user_id
content
type
conversation_type
provider
route_type
timestamp
created_at
```

用途：

- 保存用户消息。
- 保存 AI 回复消息。
- 给排查和运营留记录。
- 给后续聊天扣金币 / AI 自动回复提供上下文。

---

### 5.5 before-send 需求

`before-send` 是发消息前的检查。

后续要做：

```text
BeforeSendHandler
```

职责：

- 识别发送人是不是 DH。
- DH 消息直接放行。
- 真人发消息时做反导流检测。
- 后续接 payment-service 做扣金币。

当前先建议只做：

```text
反导流检测
```

暂不接 payment。

---

### 5.6 after-send 需求

`after-send` 是消息发出后的处理。

后续要做：

```text
MessageSentHandler
```

职责：

- 消息落库。
- 判断路由类型：BH_BH / BH_DH / DH_BH / DH_DH。
- 如果是 BH -> DH，后续触发 AI 自动回复。

当前可以先做：

```text
消息落库 + route_type 占位
```

暂不接 ai-chat。

---

### 5.7 等 OpenIM 后台准备好后再做

OpenIM 后台准备好后，再继续：

```text
OpenImProviderClient
OpenImAdaptor
真实 RegisterImUser
真实 GetImToken
真实 SendSystemMessage
真实 before-send / after-send payload 解析
```

需要的配置：

```yaml
openim:
  api-url: ${OPENIM_API_URL}
  admin-user-id: ${OPENIM_ADMIN_USER_ID}
  admin-secret: ${OPENIM_ADMIN_SECRET}
```

注意：

```text
admin-secret 不要写进仓库。
放 Nacos 或环境变量。
```

---

## 6. 暂时不建议做的事情

当前不建议继续做：

- 真实 OpenIM REST 调用。
- 真实 LiveKit token。
- payment-service 扣金币。
- ai-chat 自动回复。
- typing 真实推送。

原因：

```text
OpenIM 后台还没有，IM 主链路无法真实闭环。
现在先做内部抽象和本地可验收能力，收益更高。
```

---

## 7. 下次继续开发时建议第一步

建议下次从这里开始：

```text
抽 ImProviderClient + MockImProviderClient
```

涉及文件建议：

```text
dating-server/im-service/src/main/java/com/aurora/dating/im/provider/ImProviderClient.java
dating-server/im-service/src/main/java/com/aurora/dating/im/provider/MockImProviderClient.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/ImUserService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/SystemMessageService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/CallTokenService.java
```

改造目标：

```text
业务 service 不直接生成 mock 结果。
业务 service 调 provider 接口。
当前 provider 实现是 MockImProviderClient。
未来 OpenIM 准备好后，新增 OpenImProviderClient。
```

验证方式：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl im-service -am -DskipTests package
```

预期：

```text
BUILD SUCCESS
```

---

## 8. 给下个 AI / 下次对话的提醒

下次继续时，先读：

```text
docs/AI-开工前必读.md
docs/design/im-service-design.md
docs/design-detail/im-service-接口验收文档.md
docs/design-detail/im-service补充与需求.md
```

然后再看：

```text
proto/im/src/main/proto/im.proto
dating-server/im-service
```

不要跳过当前事实：

```text
OpenIM 后台暂时还没有。
当前真实完成度是 P0 骨架，不是真实 IM 接入完成。
```
