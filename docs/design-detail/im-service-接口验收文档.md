# im-service 接口验收文档

> 当前文档对应 im-service P0 骨架版本。  
> 已完成：gRPC 契约、服务端实现、注册用户本地表、会话本地表、系统消息本地表、在线状态 Redis + PG、OpenIM raw callback 的 online/offline 基础解析。  
> 未完成：真实 OpenIM REST、真实 LiveKit JWT、AI 自动回复、聊天扣金币、反导流拦截。当前 token / 系统消息 / 通话 token 都是 mock 结果，不能当成生产可用。

---

## 1. 当前完成进度

### P0 已完成

- `proto/im/src/main/proto/im.proto` 已定义 IM P0 RPC。
- `dating-server/im-service` 已注册 gRPC server，端口默认 `19082`。
- `Ping` 连通性接口可用。
- `RegisterImUser` 本地幂等注册可用，写入 `im_registered_user`。
- `GetImToken` 可用，但返回 mock token，并会懒注册本地用户。
- `EnsureConversation` 本地幂等建会话可用，写入 `im_conversation`。
- `SendSystemMessage` 可用，但只落本地 `im_system_message`，没有真实推 OpenIM。
- `GenerateCallToken` 可用，但返回 mock call token，没有真实签 LiveKit JWT。
- `OnRawCallback` 支持 OpenIM online/offline 类回调，维护在线状态。
- `ListOnlineUserIds` 读取 Redis ZSet `hanlian:im:presence:online`。
- `ListRecentOfflineUsers` 读取 PG `user_online_session`。
- Flyway migration 已增加 `V1__im_core_tables.sql`。

### 后续 P1 / P2 待做

- 接真实 OpenIM REST：注册、发系统消息、获取 token。
- 接真实 LiveKit：按服务端密钥签 JWT。
- 完整 provider adaptor：解析 before-send / after-send / message payload。
- before-send：反导流 + payment-service 扣金币。
- after-send：落 `chat_messages` + 判断 BH/DH + 调 ai-chat 回复。
- typing 业务通知、分段发送、重试和监控。
- Postman / grpcurl 集合。

---

## 2. 涉及文件

```text
proto/im/src/main/proto/im.proto
proto/im/im.proto
dating-server/im-service/pom.xml
dating-server/im-service/src/main/resources/application.yml
dating-server/im-service/src/main/resources/db/migration/V1__im_core_tables.sql
dating-server/im-service/src/main/java/com/aurora/dating/im/grpc/ImGrpcService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/CallbackService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/PresenceService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/ImUserService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/ConversationService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/SystemMessageService.java
dating-server/im-service/src/main/java/com/aurora/dating/im/service/CallTokenService.java
```

---

## 3. 编译验收

### 3.1 安装 im-proto

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\im
mvn clean install -DskipTests
```

预期：

```text
BUILD SUCCESS
```

### 3.2 编译 im-service

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl im-service -am -DskipTests package
```

预期：

```text
BUILD SUCCESS
```

---

## 4. HTTP debug 接口

当前没有业务 HTTP 接口，HTTP 只做 debug 连通性检查。

### 4.1 Ping

```text
GET http://localhost:18082/ping
```

作用：确认 Spring Boot HTTP 服务已启动。

### 4.2 DB

```text
GET http://localhost:18082/internal/check/db
```

作用：确认 im-service 能连 PostgreSQL。

预期：

```json
{"database":"ok","result":"1"}
```

### 4.3 Redis

```text
GET http://localhost:18082/internal/check/redis
```

作用：确认 im-service 能读写 Redis。

预期：

```json
{"redis":"ok","key":"hanlian:im-service:check:redis","value":"ok","ttlSeconds":"..."}
```

---

## 5. gRPC 接口

以下命令默认服务已启动，gRPC 端口为 `19082`。

### 5.1 Ping

解决问题：验证 gRPC 服务端可用。

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/Ping
```

预期：

```json
{"message":"im-service pong: hello"}
```

代码链路：

```text
ImGrpcService.ping
```

DB / Redis：当前链路不依赖 DB / Redis。

### 5.2 RegisterImUser

解决问题：为业务用户创建本地 IM 账号映射。

```powershell
'{"userId":10001,"nickname":"Alice","avatar":"avatar/a.png"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/RegisterImUser
```

预期：

```json
{"success":true,"imUserId":"u_10001","message":"registered"}
```

代码链路：

```text
ImGrpcService.registerImUser
  -> ImUserService.register
     -> im_registered_user
```

数据库验收：

```sql
SELECT user_id, im_user_id, nickname
FROM im_registered_user
WHERE user_id = 10001;
```

Redis：当前链路不依赖 Redis。

### 5.3 GetImToken

解决问题：给客户端返回 IM 登录票据。

```powershell
'{"userId":10001,"platform":"ios"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/GetImToken
```

预期：

```json
{"imUserId":"u_10001","token":"mock-im-token-...","expireAtMs":"...","mock":true}
```

代码链路：

```text
ImGrpcService.getImToken
  -> ImUserService.getToken
     -> ImUserService.register
        -> im_registered_user
```

关键记忆点：当前 token 是 mock，后续要替换为 OpenIM `/auth/get_user_token`。

### 5.4 EnsureConversation

解决问题：给两个人幂等创建单聊会话。

```powershell
'{"userIdA":10001,"userIdB":10002}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/EnsureConversation
```

预期：

```json
{"success":true,"conversationId":"single_10001_10002","message":"ok"}
```

代码链路：

```text
ImGrpcService.ensureConversation
  -> ConversationService.ensureConversation
     -> im_conversation
```

数据库验收：

```sql
SELECT conversation_id, user_id_a, user_id_b
FROM im_conversation
WHERE conversation_id = 'single_10001_10002';
```

### 5.5 SendSystemMessage

解决问题：给用户发送系统消息。

```powershell
'{"toUserId":10001,"bizType":"match_success","title":"匹配成功","content":"你们配对了","payloadJson":"{\"matchId\":1}"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/SendSystemMessage
```

预期：

```json
{"success":true,"messageId":"sys_...","message":"mock system message saved","mock":true}
```

代码链路：

```text
ImGrpcService.sendSystemMessage
  -> SystemMessageService.sendSystemMessage
     -> im_system_message
```

关键记忆点：当前只落库，不真实调用 OpenIM business notification。

### 5.6 GenerateCallToken

解决问题：给 1v1 音视频通话返回房间和 token。

```powershell
'{"userId":10001,"peerUserId":10002}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/GenerateCallToken
```

预期：

```json
{"roomId":"call_10001_10002_...","token":"mock-call-token-...","expireAtMs":"...","mock":true}
```

关键记忆点：当前 token 是 mock，后续要替换为 LiveKit JWT。

### 5.7 OnRawCallback

解决问题：收口 OpenIM 原始回调，当前先支持 online/offline 维护在线状态。

上线示例：

```powershell
'{"provider":"openim","payload":"eyJjYWxsYmFja0NvbW1hbmQiOiJjYWxsYmFja1VzZXJPbmxpbmVDb21tYW5kIiwidXNlcklEIjoiMTAwMDEiLCJwbGF0Zm9ybSI6ImlvcyJ9"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/OnRawCallback
```

预期：

```json
{"code":0,"message":"online recorded","allow":true}
```

Redis 验收：

```text
ZRANGE hanlian:im:presence:online 0 -1 WITHSCORES
```

数据库验收：

```sql
SELECT user_id, platform, online_at, offline_at
FROM user_online_session
WHERE user_id = 10001
ORDER BY id DESC
LIMIT 1;
```

下线示例：

```powershell
'{"provider":"openim","payload":"eyJjYWxsYmFja0NvbW1hbmQiOiJjYWxsYmFja1VzZXJPZmZsaW5lQ29tbWFuZCIsInVzZXJJRCI6IjEwMDAxIiwicGxhdGZvcm0iOiJpb3MifQ=="}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/OnRawCallback
```

预期：

```json
{"code":0,"message":"offline recorded","allow":true}
```

Redis 验收：`10001` 应从 `hanlian:im:presence:online` 移除。

数据库验收：最近一条 `user_online_session` 应回填 `offline_at` 和 `duration_seconds`。

### 5.8 ListOnlineUserIds

解决问题：给 match-service 查询某个时间窗口内新上线的用户。

```powershell
'{"sinceMs":0,"untilMs":4102444800000,"limit":100}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/ListOnlineUserIds
```

预期：返回当前 Redis 在线集合里的数字 userId。

代码链路：

```text
ImGrpcService.listOnlineUserIds
  -> PresenceService.listOnlineUserIds
     -> Redis ZSet hanlian:im:presence:online
```

### 5.9 ListRecentOfflineUsers

解决问题：给 match-service 查询某个时间窗口内刚下线的用户。

```powershell
'{"sinceMs":0,"untilMs":4102444800000,"limit":100}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/ListRecentOfflineUsers
```

预期：返回 `user_online_session.offline_at` 落在窗口内的用户。

代码链路：

```text
ImGrpcService.listRecentOfflineUsers
  -> PresenceService.listRecentOfflineUsers
     -> user_online_session
```

---

## 6. 异常验收

### 6.1 userId 非法

```powershell
'{"userId":0,"platform":"ios"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/GetImToken
```

预期：gRPC `INVALID_ARGUMENT`，描述里包含 `user_id must be positive`。

### 6.2 会话双方相同

```powershell
'{"userIdA":10001,"userIdB":10001}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/EnsureConversation
```

预期：gRPC `INVALID_ARGUMENT`，描述里包含 `conversation requires two different users`。

### 6.3 callback payload 非 JSON

```powershell
'{"provider":"openim","payload":"bm90LWpzb24="}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/im/src/main/proto/im.proto -d '@' localhost:19082 dating.im.v1.ImService/OnRawCallback
```

预期：

```json
{"code":4003,"message":"payload is not valid json","allow":false}
```

---

## 7. 当前实现与技术方案差异

- 技术方案里的 provider adaptor / sender 抽象还没有完整拆包，当前先集中在 `CallbackService` 和本地 mock 服务里。
- `chat_messages` 还没建，after-send 落消息和 AI 回复未实现。
- before-send 的反导流、扣金币未实现。
- OpenIM 用户注册 / token / 系统消息未真实调用外部 REST。
- LiveKit token 未真实签发。
- 没有定时孤儿在线会话清扫。
- 没有单元测试和 Postman / grpcurl collection。
