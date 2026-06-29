# user-service 接口验收文档

> 用途：给你自己测试 user-service 用，也帮助你记住每个接口解决什么问题、代码链路怎么走。  
> 范围：HTTP debug 接口、gRPC 身份接口、gRPC Profile 接口、数据库 / Redis 验收点、异常验收点、关键设计记忆点。  
> 当前版本：P1 身份 + Profile 链路，包含手机号、设备、第三方登录、封禁检查、资料查询、资料更新、onboarding 完成、头像 presign / confirm、兴趣标签查询与全量替换。

---

## 1. 验收前准备

### 1.1 服务信息

| 项 | 值 |
|---|---|
| 服务名 | `user-service` |
| HTTP 端口 | `18081` |
| gRPC 端口 | `19081` |
| proto 文件 | `proto/user/src/main/proto/user.proto` |
| gRPC 服务 | `dating.user.v1.UserIdentityService` |
| 数据库 | PostgreSQL `dating_dev_hanlian` |
| Redis | database `1` |

### 1.2 启动前检查

1. 确认 Nacos `user-service.yaml` 里有 PostgreSQL、Redis、Flyway 配置。
2. 如果刚改过 `common` 模块，先在 `dating-server` 目录执行：

```powershell
mvn -pl common install -DskipTests
```

3. 编译 user-service：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl user-service -am -DskipTests package
```

4. 启动 user-service：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server\user-service
mvn spring-boot:run
```

5. 启动成功要看到：

```text
Tomcat started on port 18081
user-service gRPC server started on port 19081
```

### 1.3 常用表

| 表 | 作用 |
|---|---|
| `user_info` | 用户主表，`id` 是数据库内部主键，`user_id` 是对外业务用户 ID |
| `user_login_phone` | 手机号绑定表 |
| `user_device_registration` | 设备绑定表 |
| `user_third_party_registration` | 第三方账号绑定表 |
| `user_interest` | 用户兴趣标签表 |
| `flyway_history_user` | user-service Flyway 迁移历史表 |

### 1.4 当前 Redis key

| Key | 作用 |
|---|---|
| `hanlian:user-service:check:redis` | `/internal/check/redis` 写入的连通性测试 key，TTL 60 秒 |
| `hanlian:user:ban:status:{userId}` | `CheckBan` 封禁状态短缓存，TTL 5 分钟 |
| `hanlian:user:profile:{userId}` | `GetProfile` 用户资料缓存，TTL 24 小时 |
| `hanlian:user:interest:{userId}` | 用户兴趣标签缓存，TTL 24 小时 |
| `hanlian:lock:user:register:phone:{phoneE164}:{appName}` | 手机号注册解析锁 |
| `hanlian:lock:user:register:device:{platform}:{deviceId}:{appName}` | 设备注册解析锁 |
| `hanlian:lock:user:register:third-party:{platform}:{thirdPartyUserId}:{appName}` | 第三方注册解析锁 |

业务缓存 key 的项目前缀来自 `REDIS_KEY_PREFIX`，默认是 `hanlian`。

---

## 2. 总体代码分层

当前 user-service 的主要调用方向：

```text
HTTP debug:
PingController / DbCheckController / RedisCheckController
  -> PostgreSQL / Redis

gRPC:
UserIdentityGrpcService
  -> UserIdentityServiceImpl
     -> UserInfoManager / UserLoginPhoneManager / UserDeviceRegistrationManager / UserThirdPartyRegistrationManager
        -> UserInfoMapper / UserLoginPhoneMapper / UserDeviceRegistrationMapper / UserThirdPartyRegistrationMapper
           -> PostgreSQL / Redis / Redisson

UserProfileGrpcService
  -> UserProfileServiceImpl
     -> UserInfoManager
        -> UserInfoMapper
           -> PostgreSQL / Redis
```

关键原则：

- Controller / gRPC 负责接请求、组装响应、转换异常。
- Service 负责业务逻辑和事务边界。
- Manager 负责单表查询和写入。
- Mapper 只做 MyBatis-Plus 数据访问。
- `user_info.id` 是内部数据库主键。
- `user_info.user_id` 是业务用户 ID，对外接口、post-service、gateway 都应该用它。
- 业务 `user_id` 由 `common` 模块里的 `SnowflakeIdGenerator` 生成。

---

## 2.1 gRPC Metadata 上下文

gateway 调 user-service 时，会通过 gRPC Metadata 透传调用上下文：

| Metadata | 来源 | user-service 用途 |
|---|---|---|
| `x-user-id` | gateway 当前 JWT 解析出的业务 `userId` | 注入 `UserContext.callerUserId()` 和日志 MDC |
| `x-device-id` | HTTP 请求头 `x-device-id` | 注入 `UserContext.deviceId()` 和日志 MDC |
| `x-trace-id` | HTTP 请求头 `x-trace-id`；缺失时 gateway 自动生成 UUID | 注入 `UserContext.traceId()` 和日志 MDC |

代码链路：

```text
mobile-gateway HTTP request
  -> JwtAuthFilter
     -> JwtUserContext.setUserId
  -> UserGrpcClientMetadataInterceptor
     -> 写入 x-user-id / x-device-id / x-trace-id
  -> user-service UserContextServerInterceptor
     -> 读取 Metadata
     -> 写入 UserContext
     -> 写入 MDC
  -> UserIdentityGrpcService / UserProfileGrpcService
```

设计记忆点：

- user-service 不签发 JWT，也不验证 JWT。
- user-service 只信任 gateway 通过 gRPC Metadata 传来的调用上下文。
- `x-user-id` 表示调用者，不一定等于 proto 请求体里的目标 `user_id`。
- `x-trace-id` 缺失时会兜底生成，方便排查链路日志。

---

## 3. HTTP debug 接口

### 3.1 Ping

#### 接口解决什么问题

验证 user-service 的 HTTP 服务是否正常启动。

#### HTTP 请求

```http
GET /internal/ping
```

示例：

```powershell
curl http://localhost:18081/internal/ping
```

#### HTTP 返回示例

```json
{
  "service": "user-service",
  "status": "ok"
}
```

#### 对应 gRPC 方法

无。它只是 HTTP debug 接口。

#### 完整代码链路

```text
PingController.ping
  -> 直接返回 Map
```

#### 验收项

- HTTP 状态码是 `200`。
- 返回 `service = user-service`。
- 返回 `status = ok`。

---

### 3.2 DB 连通性检查

#### 接口解决什么问题

验证 user-service 能否连接 PostgreSQL。

#### HTTP 请求

```http
GET /internal/check/db
```

示例：

```powershell
curl http://localhost:18081/internal/check/db
```

#### HTTP 返回示例

```json
{
  "database": "ok",
  "result": "1"
}
```

#### 对应 gRPC 方法

无。它只是 HTTP debug 接口。

#### 完整代码链路

```text
DbCheckController.checkDb
  -> JdbcTemplate.queryForObject("select 1")
  -> PostgreSQL
```

#### 数据库验收项

- 接口返回 `database = ok`。
- 接口返回 `result = 1`。
- 如果失败，优先检查 Nacos datasource 配置、数据库网络、账号密码。

---

### 3.3 Redis 连通性检查

#### 接口解决什么问题

验证 user-service 能否连接 Redis，并能写入 / 读取短 TTL key。

#### HTTP 请求

```http
GET /internal/check/redis
```

示例：

```powershell
curl http://localhost:18081/internal/check/redis
```

#### HTTP 返回示例

```json
{
  "redis": "ok",
  "key": "hanlian:user-service:check:redis",
  "value": "ok",
  "ttlSeconds": "60"
}
```

#### 对应 gRPC 方法

无。它只是 HTTP debug 接口。

#### 完整代码链路

```text
RedisCheckController.checkRedis
  -> StringRedisTemplate.opsForValue().set
  -> StringRedisTemplate.opsForValue().get
  -> Redis
```

#### Redis 验收项

在 Redis 客户端中检查：

```redis
GET hanlian:user-service:check:redis
TTL hanlian:user-service:check:redis
```

预期：

- value 是 `ok`。
- TTL 大于 0，并且会逐渐减少。

---

## 4. ResolveOrCreateByPhone

### 4.1 接口解决什么问题

手机号登录后，mobile-gateway 已经完成短信校验，user-service 根据手机号找到已有用户，或者创建一个 pending 用户。

### 4.2 HTTP 请求

当前没有业务 HTTP 接口。HTTP 只做 debug 连通性检查。

### 4.3 gRPC 方法

```text
ResolveOrCreateByPhone(ResolveOrCreateByPhoneRequest) returns (ResolveOrCreateResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `phone_e164` | `string` | 手机号，建议 E.164 格式，如 `+8613800138001` |
| `app_name` | `string` | App 名，如 `hanlian` |

#### proto 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID，雪花 ID |
| `pending` | `bool` | 是否资料未补全 |
| `created` | `bool` | 本次是否创建了新用户，`true` 表示新建，`false` 表示命中旧绑定 |

### 4.4 grpcurl 测试

第一次请求：

```powershell
'{"phoneE164":"+8613800138001","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByPhone
```

预期：

```json
{
  "userId": "一个很大的雪花ID",
  "pending": true,
  "created": true
}
```

重复请求同一个手机号：

```powershell
'{"phoneE164":"+8613800138001","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByPhone
```

预期：返回同一个 `userId`，`created = false`。proto3 默认值不显示，所以响应里可能看不到 `created` 字段。

### 4.5 完整代码链路

```text
UserIdentityGrpcService.resolveOrCreateByPhone
  -> UserIdentityServiceImpl.resolveOrCreateByPhone
     -> validateText(phoneE164, "phoneE164")
     -> validateText(appName, "appName")
     -> registerPhoneLockKey
     -> Redisson lock hanlian:lock:user:register:phone:{phoneE164}:{appName}
     -> doResolveOrCreateByPhone
        -> UserLoginPhoneManager.findByPhoneAndApp
           -> UserLoginPhoneMapper.selectOne
        -> 如果已有绑定:
           -> UserInfoManager.touchLastOpenAt
           -> UserInfoManager.findByUserId
              -> UserInfoMapper.selectOne
           -> 返回 created=false
        -> 如果没有绑定:
           -> UserInfoManager.insertPlaceholder
              -> SnowflakeIdGenerator.nextId
              -> UserInfoMapper.insert
           -> UserLoginPhoneManager.insertBinding
              -> UserLoginPhoneMapper.insert
           -> 返回 created=true
```

### 4.6 数据库验收项

查询用户主表：

```sql
select id, user_id, app_name, pending, nickname, regulation_status, last_open_at, created_at
from user_info
order by id desc
limit 5;
```

查询手机号绑定：

```sql
select id, user_id, phone_e164, app_name, verified_at, created_at
from user_login_phone
order by id desc
limit 5;
```

验收点：

- `user_info.id` 是自增主键。
- `user_info.user_id` 是雪花 ID。
- `user_login_phone.user_id = user_info.user_id`。
- 同一个 `(phone_e164, app_name)` 重复请求不会创建新用户。
- 命中已有绑定时会更新 `user_info.last_open_at`。
- 第一次请求 `created = true`。
- 重复请求 `created = false`。

### 4.7 异常验收项

手机号为空：

```powershell
'{"phoneE164":"","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByPhone
```

预期：

```text
Code: InvalidArgument
Message: phoneE164 is required
```

`appName` 为空时，预期：

```text
Code: InvalidArgument
Message: appName is required
```

### 4.8 关键设计记忆点

- user-service 不负责短信验证码校验，短信校验应该在 mobile-gateway。
- 这个接口只负责“手机号 -> userId”的身份解析。
- pending 用户表示资料还没补全，后续应该进入 onboarding。
- 对外永远返回业务 `user_id`，不要返回数据库内部 `id`。

---

## 5. ResolveOrCreateByDevice

### 5.1 接口解决什么问题

用户没有手机号登录时，先根据设备创建或找到一个 pending 用户。它适合快速登录 / 游客态 MVP。

### 5.2 HTTP 请求

当前没有业务 HTTP 接口。HTTP 只做 debug 连通性检查。

### 5.3 gRPC 方法

```text
ResolveOrCreateByDevice(ResolveOrCreateByDeviceRequest) returns (ResolveOrCreateResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `device_id` | `string` | 客户端设备标识 |
| `platform` | `Platform` | `PLATFORM_IOS` / `PLATFORM_ANDROID` / `PLATFORM_WEB` |
| `app_name` | `string` | App 名，如 `hanlian` |

#### proto 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID，雪花 ID |
| `pending` | `bool` | 是否资料未补全 |
| `created` | `bool` | 本次是否创建了新用户 |

### 5.4 grpcurl 测试

第一次请求：

```powershell
'{"deviceId":"device-test-001","platform":"PLATFORM_IOS","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByDevice
```

预期：

```json
{
  "userId": "一个很大的雪花ID",
  "pending": true,
  "created": true
}
```

重复请求同一个设备：

```powershell
'{"deviceId":"device-test-001","platform":"PLATFORM_IOS","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByDevice
```

预期：返回同一个 `userId`，`created = false`。proto3 默认值不显示，所以响应里可能看不到 `created` 字段。

### 5.5 完整代码链路

```text
UserIdentityGrpcService.resolveOrCreateByDevice
  -> UserIdentityServiceImpl.resolveOrCreateByDevice
     -> validateText(deviceId, "deviceId")
     -> validatePositive(platform, "platform")
     -> validateText(appName, "appName")
     -> registerDeviceLockKey
     -> Redisson lock hanlian:lock:user:register:device:{platform}:{deviceId}:{appName}
     -> doResolveOrCreateByDevice
        -> UserDeviceRegistrationManager.findByDeviceAndApp
           -> UserDeviceRegistrationMapper.selectOne
        -> 如果已有绑定:
           -> UserInfoManager.touchLastOpenAt
           -> UserInfoManager.findByUserId
              -> UserInfoMapper.selectOne
           -> 返回 created=false
        -> 如果没有绑定:
           -> UserInfoManager.insertPlaceholder
              -> SnowflakeIdGenerator.nextId
              -> UserInfoMapper.insert
           -> UserDeviceRegistrationManager.insertBinding
              -> UserDeviceRegistrationMapper.insert
           -> 返回 created=true
```

### 5.6 数据库验收项

查询设备绑定：

```sql
select id, user_id, device_id, platform, app_name, created_at
from user_device_registration
order by id desc
limit 5;
```

验收点：

- `user_device_registration.user_id = user_info.user_id`。
- 同一个 `(device_id, platform, app_name)` 重复请求不会创建新用户。
- `platform = 1` 表示 `PLATFORM_IOS`，`platform = 2` 表示 `PLATFORM_ANDROID`，`platform = 3` 表示 `PLATFORM_WEB`。
- 第一次请求 `created = true`。
- 重复请求 `created = false`。

### 5.7 异常验收项

`deviceId` 为空：

```powershell
'{"deviceId":"","platform":"PLATFORM_IOS","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByDevice
```

预期：

```text
Code: InvalidArgument
Message: deviceId is required
```

`platform` 为 `PLATFORM_UNSPECIFIED` 时，预期：

```text
Code: InvalidArgument
Message: platform must be positive
```

### 5.8 关键设计记忆点

- 设备 ID 不是绝对稳定身份，只适合快速登录 / 游客态。
- 设备用户后续绑定手机号时，应该复用同一个业务 `user_id`，不要再创建另一个用户。
- 当前 MVP 还没有实现“设备用户升级绑定手机号”的专门接口。

---

## 6. CheckBan

### 6.1 接口解决什么问题

其他服务拿到业务 `userId` 后，调用 user-service 判断用户是否封禁。

### 6.2 HTTP 请求

当前没有业务 HTTP 接口。HTTP 只做 debug 连通性检查。

### 6.3 gRPC 方法

```text
CheckBan(CheckBanRequest) returns (CheckBanResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |

#### proto 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `banned` | `bool` | 是否封禁 |
| `reason` | `string` | 机器可读原因，如 `USER_BANNED` |
| `banned_at_ms` | `int64` | 封禁时间，当前 P0 固定为 0 |
| `message` | `string` | 展示文案 |

### 6.4 grpcurl 测试

正常用户：

```powershell
'{"userId":你的雪花userId}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/CheckBan
```

预期：

```json
{}
```

说明：proto3 默认值不显示，`{}` 表示 `banned = false`。

手动封禁：

```sql
update user_info
set regulation_status = 2
where user_id = 你的雪花userId;
```

再次请求 `CheckBan`，预期：

```json
{
  "banned": true,
  "reason": "USER_BANNED",
  "message": "用户已被封禁"
}
```

恢复：

```sql
update user_info
set regulation_status = 0
where user_id = 你的雪花userId;
```

### 6.5 完整代码链路

```text
UserIdentityGrpcService.checkBan
  -> UserIdentityServiceImpl.isBanned
     -> validatePositive(userId, "userId")
     -> Redis GET hanlian:user:ban:status:{userId}
     -> 如果命中:
        -> 直接返回缓存里的 banned
     -> 如果未命中:
        -> UserInfoManager.findByUserId
           -> UserInfoMapper.selectOne
        -> 判断 regulation_status
        -> Redis SET hanlian:user:ban:status:{userId} TTL 5 分钟
```

### 6.6 数据库验收项

```sql
select id, user_id, regulation_status, deleted
from user_info
where user_id = 你的雪花userId;
```

验收点：

- `regulation_status = 0`：未封禁。
- `regulation_status = 2`：封禁。
- `regulation_status = 5`：也按封禁处理。
- 查询条件使用业务 `user_id`。

### 6.7 Redis 验收项

第一次调用 `CheckBan` 后，在 Redis 客户端中检查：

```redis
GET hanlian:user:ban:status:你的雪花userId
TTL hanlian:user:ban:status:你的雪花userId
```

预期：

- 正常用户 value 是 `false`。
- 封禁用户 value 是 `true`。
- TTL 大于 0，并且最大约 300 秒。
- 5 分钟内再次调用 `CheckBan` 会优先使用缓存。
- 如果手动修改了数据库里的 `regulation_status`，需要删除这个 Redis key 或等待 TTL 过期后，`CheckBan` 才会读到新的 DB 状态。

### 6.8 异常验收项

`userId` 为 0：

```powershell
'{"userId":0}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/CheckBan
```

预期：

```text
Code: InvalidArgument
Message: userId must be positive
```

### 6.9 关键设计记忆点

- CheckBan 是后续 gateway、post-service、match-service 都可能调用的基础能力。
- 当前已加 Redis 短缓存，缓存 key 是 `hanlian:user:ban:status:{userId}`，TTL 5 分钟。
- 封禁状态应该由 user-service 统一解释，其他服务不要自己猜 `regulation_status` 的含义。

---

## 7. ResolveOrCreateByThirdParty

### 7.1 接口解决什么问题

第三方登录后，根据平台和第三方用户 ID 找到或创建用户。

### 7.2 HTTP 请求

当前没有业务 HTTP 接口。HTTP 只做 debug 连通性检查。

### 7.3 gRPC 方法

```text
ResolveOrCreateByThirdParty(ResolveOrCreateByThirdPartyRequest) returns (ResolveOrCreateResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `platform` | `ThirdPartyPlatform` | `GOOGLE` / `APPLE` / `WECHAT` |
| `third_party_user_id` | `string` | 第三方平台返回的唯一 ID |
| `app_name` | `string` | App 名 |
| `email` | `string` | 可选邮箱 |

#### proto 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID，雪花 ID |
| `pending` | `bool` | 是否资料未补全 |
| `created` | `bool` | 本次是否创建了新用户 |

### 7.4 grpcurl 测试

第一次请求：

```powershell
'{"platform":"THIRD_PARTY_PLATFORM_GOOGLE","thirdPartyUserId":"google-user-001","appName":"hanlian","email":"google-user-001@example.com"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByThirdParty
```

预期：

```json
{
  "userId": "一个很大的雪花ID",
  "pending": true,
  "created": true
}
```

重复请求同一个第三方账号：

```powershell
'{"platform":"THIRD_PARTY_PLATFORM_GOOGLE","thirdPartyUserId":"google-user-001","appName":"hanlian","email":"google-user-001@example.com"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByThirdParty
```

预期：返回同一个 `userId`，`created = false`。proto3 默认值不显示，所以响应里可能看不到 `created` 字段。

### 7.5 完整代码链路

```text
UserIdentityGrpcService.resolveOrCreateByThirdParty
  -> UserIdentityServiceImpl.resolveOrCreateByThirdParty
     -> validatePositive(platform, "platform")
     -> validateText(thirdPartyUserId, "thirdPartyUserId")
     -> validateText(appName, "appName")
     -> registerThirdPartyLockKey
     -> Redisson lock hanlian:lock:user:register:third-party:{platform}:{thirdPartyUserId}:{appName}
     -> doResolveOrCreateByThirdParty
        -> UserThirdPartyRegistrationManager.findByPlatformAndThirdPartyUserId
           -> UserThirdPartyRegistrationMapper.selectOne
        -> 如果已有绑定:
           -> UserInfoManager.touchLastOpenAt
           -> UserInfoManager.findByUserId
              -> UserInfoMapper.selectOne
           -> 返回 created=false
        -> 如果没有绑定:
           -> UserInfoManager.insertPlaceholder
              -> SnowflakeIdGenerator.nextId
              -> UserInfoMapper.insert
           -> UserThirdPartyRegistrationManager.insertBinding
              -> UserThirdPartyRegistrationMapper.insert
           -> 返回 created=true
```

### 7.6 数据库验收项

```sql
select id, user_id, platform, third_party_user_id, app_name, email, created_at
from user_third_party_registration
where third_party_user_id = 'google-user-001'
  and app_name = 'hanlian';
```

验收点：

- `user_third_party_registration.user_id = user_info.user_id`。
- 同一个 `(platform, third_party_user_id, app_name)` 重复请求不会创建新绑定。
- 第一次请求 `created = true`。
- 重复请求 `created = false`。

### 7.7 异常验收项

`thirdPartyUserId` 为空：

```powershell
'{"platform":"THIRD_PARTY_PLATFORM_GOOGLE","thirdPartyUserId":"","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByThirdParty
```

预期：

```text
Code: InvalidArgument
Message: thirdPartyUserId is required
```

### 7.8 关键设计记忆点

- user-service 不负责第三方 OAuth token 校验，token 校验应该在 mobile-gateway。
- 第三方登录也应该返回同一套业务 `user_id`。
- 第三方登录和手机号 / 设备登录一样，也有注册锁和 `created` 字段。

---

## 7.9 账号升级绑定 BindPhone / BindThirdParty

### 接口解决什么问题

设备快速登录创建的是 pending 游客账号。用户后续补绑手机号或第三方账号时，不能再走 `ResolveOrCreate` 创建新用户，而是要把手机号 / 第三方账号绑定到当前 JWT 对应的 `userId`。

MVP 策略：

- 如果手机号 / 第三方账号没有绑定过，绑定到当前用户。
- 如果已经绑定到当前用户，幂等成功。
- 如果已经绑定到其他用户，直接拒绝，不做合账。

### gRPC 方法

```text
BindPhone(BindPhoneRequest) returns (BindAccountResponse)
BindThirdParty(BindThirdPartyRequest) returns (BindAccountResponse)
```

### grpcurl 绑定手机号

```powershell
$json = '{"userId":你的雪花userId,"phoneE164":"+8613800999001","appName":"hanlian"}'
[System.IO.File]::WriteAllText("C:\tmp\bind-phone.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserIdentityService/BindPhone < C:\tmp\bind-phone.json"
```

预期：

```json
{
  "userId": "你的雪花userId"
}
```

### grpcurl 绑定第三方账号

```powershell
$json = '{"userId":你的雪花userId,"platform":"THIRD_PARTY_PLATFORM_GOOGLE","thirdPartyUserId":"google-bind-user-001","appName":"hanlian","email":"google-bind-user-001@example.com"}'
[System.IO.File]::WriteAllText("C:\tmp\bind-third-party.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserIdentityService/BindThirdParty < C:\tmp\bind-third-party.json"
```

### gateway 验收

先设备登录拿 token：

```powershell
$loginBody = @{
  deviceId = "gateway-bind-device-001"
  platform = 1
  appName = "hanlian"
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/login-device" `
  -ContentType "application/json" `
  -Body $loginBody

$token = $login.data.accessToken
```

绑定手机号：

```powershell
$body = @{
  phoneE164 = "+8613800999002"
  smsCode = "123456"
  appName = "hanlian"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/bind-phone" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
```

绑定第三方：

```powershell
$body = @{
  platform = 1
  thirdPartyUserId = "google-bind-user-002"
  thirdPartyToken = "mock-token"
  appName = "hanlian"
  email = "google-bind-user-002@example.com"
} | ConvertTo-Json

Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/bind-third-party" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
```

### 完整代码链路

```text
AuthController.bindPhone
  -> AuthServiceImpl.bindPhone
     -> JwtUserContext.getUserId
     -> UserIdentityClient.bindPhone
        -> gRPC UserIdentityService/BindPhone
           -> UserIdentityGrpcService.bindPhone
              -> UserIdentityServiceImpl.bindPhone
                 -> Redisson lock hanlian:lock:user:register:phone:{phoneE164}:{appName}
                 -> UserInfoManager.findByUserId
                 -> UserLoginPhoneManager.findByPhoneAndApp
                 -> UserLoginPhoneManager.insertBinding
```

```text
AuthController.bindThirdParty
  -> AuthServiceImpl.bindThirdParty
     -> JwtUserContext.getUserId
     -> UserIdentityClient.bindThirdParty
        -> gRPC UserIdentityService/BindThirdParty
           -> UserIdentityGrpcService.bindThirdParty
              -> UserIdentityServiceImpl.bindThirdParty
                 -> Redisson lock hanlian:lock:user:register:third-party:{platform}:{thirdPartyUserId}:{appName}
                 -> UserInfoManager.findByUserId
                 -> UserThirdPartyRegistrationManager.findByPlatformAndThirdPartyUserId
                 -> UserThirdPartyRegistrationManager.insertBinding
```

---

## 8. UserProfileService

### 8.1 GetProfile

#### 接口解决什么问题

根据业务 `userId` 查询用户基础资料。

#### gRPC 方法

```text
GetProfile(GetProfileRequest) returns (GetProfileResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |

#### proto 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `profile` | `UserProfile` | 用户资料 |

#### grpcurl 测试

```powershell
'{"userId":你的雪花userId}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserProfileService/GetProfile
```

预期：

```json
{
  "profile": {
    "userId": "你的雪花userId",
    "appName": "hanlian",
    "nickname": "Alice",
    "profileCompletion": 75,
    "missingFields": [
      "avatar",
      "birthday"
    ]
  }
}
```

#### 资料完整度规则

`profile_completion` 是响应层派生字段，不落库。当前规则：

| 字段 | 分值 | 缺失时 missing field |
|---|---:|---|
| `nickname` | 15 | `nickname` |
| `gender > 0` | 10 | `gender` |
| `age > 0` 或 `birthday` 有值 | 10 | `birthday` |
| `bio` | 10 | `bio` |
| `location` | 10 | `location` |
| `occupation` | 10 | `occupation` |
| `education` | 10 | `education` |
| `height > 0` | 10 | `height` |
| 非默认头像 | 15 | `avatar` |

合计 100 分。默认头像 `status = DEFAULT` 时，不计入头像分。

#### 完整代码链路

```text
UserProfileGrpcService.getProfile
  -> UserProfileServiceImpl.getProfile
     -> UserProfileServiceImpl.getCachedProfile
        -> Redis GET hanlian:user:profile:{userId}
     -> 如果缓存命中:
        -> 返回 UserInfoEntity
     -> 如果缓存未命中:
        -> UserInfoManager.findByUserId
           -> UserInfoMapper.selectOne
        -> UserProfileServiceImpl.cacheProfile
           -> Redis SET hanlian:user:profile:{userId} TTL 24h
  -> UserProfileGrpcService.toUserProfile
     -> calculateProfileCompletion
     -> 返回 profile_completion / missing_fields
```

#### Redis 验收项

第一次调用 `GetProfile` 后，在 Redis 客户端检查：

```redis
GET hanlian:user:profile:你的雪花userId
TTL hanlian:user:profile:你的雪花userId
```

预期：

- GET 能看到用户资料 JSON。
- TTL 大于 0。
- 重复调用 `GetProfile` 会优先命中 Redis。

### 8.2 UpdateProfile

#### 接口解决什么问题

编辑用户资料页保存基础资料。当前支持昵称、年龄、简介、城市、职业、学历、身高。

#### gRPC 方法

```text
UpdateProfile(UpdateProfileRequest) returns (UpdateProfileResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |
| 
ickname` | `string` | 昵称 |
| `age` | `int32` | 年龄 |
| `bio` | `string` | 个人简介 |
| `location` | `string` | 城市，对应 `user_info.preferred_location` |
| `occupation` | `string` | 职业，对应 `user_info.profession` |
| `education` | `string` | 学历 |
| `height` | `int32` | 身高，单位 cm |

#### grpcurl 测试

有中文字段时建议使用 UTF-8 无 BOM JSON 文件：

```powershell
$json = '{"userId":你的雪花userId,"nickname":"Alice","age":25,"bio":"hello","location":"北京","occupation":"Engineer","education":"本科","height":168}'
[System.IO.File]::WriteAllText("C:\tmp\update-profile.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/UpdateProfile < C:\tmp\update-profile.json"
```

预期：

```json
{
  "profile": {
    "nickname": "Alice",
    "age": 25,
    "location": "北京",
    "occupation": "Engineer",
    "education": "本科",
    "height": 168
  }
}
```

#### 完整代码链路

```text
UserProfileGrpcService.updateProfile
  -> UserProfileServiceImpl.updateProfile
     -> validatePositive / validateNickname / validateAge / validateTextLength / validateHeight
     -> UserInfoManager.findByUserId
     -> UserInfoManager.updateProfile
        -> UserInfoMapper.update
     -> UserProfileServiceImpl.evictProfileCache
        -> Redis DEL hanlian:user:profile:{userId}
     -> UserInfoManager.findByUserId
```

#### 数据库验收项

```sql
select user_id, nickname, age, bio, preferred_location, profession, education, height
from user_info
where user_id = 你的雪花userId;
```

#### Redis 验收项

- `UpdateProfile` 更新 DB 后会删除 `hanlian:user:profile:{userId}`。
- 再调用 `GetProfile` 会重新回源 DB 并写入缓存。

### 8.3 UpsertOnboarding

#### 接口解决什么问题

新用户首次补全资料，并把 `pending` 从 `true` 改成 `false`。

#### gRPC 方法

```text
UpsertOnboarding(UpsertOnboardingRequest) returns (UpsertOnboardingResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |
| 
ickname` | `string` | 昵称 |
| `gender` | `int32` | 性别，0 未知，1 男，2 女 |
| `age` | `int32` | 年龄 |
| `birthday` | `string` | 生日，格式 `yyyy-MM-dd`，不能是未来日期 |
| `bio` | `string` | 个人简介 |
| `location` | `string` | 城市 |
| `occupation` | `string` | 职业 |
| `education` | `string` | 学历 |
| `height` | `int32` | 身高，单位 cm |

#### grpcurl 测试

```powershell
$json = '{"userId":你的雪花userId,"nickname":"Alice","gender":2,"age":25,"birthday":"1998-05-20","bio":"hello","location":"北京","occupation":"Engineer","education":"本科","height":168}'
[System.IO.File]::WriteAllText("C:\tmp\onboarding.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/UpsertOnboarding < C:\tmp\onboarding.json"
```

预期：

```json
{
  "profile": {
    "nickname": "Alice",
    "gender": 2,
    "age": 25,
    "birthday": "1998-05-20",
    "location": "北京",
    "education": "本科"
  }
}
```

说明：`pending = false` 是 proto3 默认值，grpcurl 默认不显示。

#### 完整代码链路

```text
UserProfileGrpcService.upsertOnboarding
  -> UserProfileServiceImpl.upsertOnboarding
     -> validatePositive / validateNickname / validateGender / validateAge / parseBirthday / validateTextLength / validateHeight
     -> UserInfoManager.findByUserId
     -> UserInfoManager.upsertOnboarding
        -> UserInfoMapper.update
        -> set pending=false
     -> UserProfileServiceImpl.evictProfileCache
        -> Redis DEL hanlian:user:profile:{userId}
     -> UserInfoManager.findByUserId
```

#### 数据库验收项

```sql
select user_id, pending, nickname, gender, age, birthday, preferred_location, education
from user_info
where user_id = 你的雪花userId;
```

预期：

- `pending = false`。
- `gender / nickname / age / birthday / preferred_location / education` 已更新。

### 8.4 PresignAvatarUpload

#### 接口解决什么问题

给客户端生成头像上传凭证。当前版本使用 MinIO / S3 兼容协议生成真实 PUT 预签名 URL，客户端拿到 `upload_url` 后直接上传文件到对象存储。

#### Nacos 配置要求

`user-service` 需要在配置中心补充对象存储配置：

```yaml
storage:
  endpoint: https://minio-api.jianjiange.site
  region: us-east-1
  access-key: hanlian-user-service
  secret-key: 你的MinIO Service Account SecretKey
  bucket: dating-hanlian
  public-base-url: https://minio-api.jianjiange.site/dating-hanlian
  default-avatar-url: https://minio-api.jianjiange.site/dating-hanlian/default/avatar.png
  path-style-access: true
```

可先验证 user-service 到 MinIO 的连通性：

```powershell
Invoke-RestMethod -Method Get -Uri "http://localhost:18081/internal/check/minio"
```

预期：

```json
{
  "minio": "ok",
  "endpoint": "https://minio-api.jianjiange.site",
  "bucket": "dating-hanlian"
}
```

#### gRPC 方法

```text
PresignAvatarUpload(PresignAvatarUploadRequest) returns (PresignAvatarUploadResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |
| `file_ext` | `string` | 文件扩展名，支持 `jpg / jpeg / png / webp` |
| `content_type` | `string` | 上传文件 Content-Type |
| `content_length` | `int64` | 文件大小，当前限制不超过 10MB |

#### grpcurl 测试

```powershell
$json = '{"userId":你的雪花userId,"fileExt":"jpg","contentType":"image/jpeg","contentLength":102400}'
[System.IO.File]::WriteAllText("C:\tmp\presign-avatar.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/PresignAvatarUpload < C:\tmp\presign-avatar.json"
```

预期：

```json
{
  "uploadUrl": "https://minio-api.jianjiange.site/dating-hanlian/avatar/你的雪花userId/xxx.jpg?...",
  "objectKey": "avatar/你的雪花userId/xxx.jpg",
  "expireSeconds": "300"
}
```

#### 上传文件到 MinIO

拿到 `uploadUrl` 后，客户端需要使用相同的 `Content-Type` 发起 PUT：

```powershell
[System.IO.File]::WriteAllBytes("C:\tmp\avatar-test.jpg", [byte[]](0xFF,0xD8,0xFF,0xD9))
curl.exe -sS -w "HTTP_STATUS:%{http_code}" -X PUT "上一步返回的uploadUrl" -H "Content-Type: image/jpeg" --data-binary "@C:\tmp\avatar-test.jpg"
```

必须看到 `HTTP_STATUS:200` 后，再调用 `ConfirmAvatarUpload`。如果没有确认 PUT 成功就调用 confirm，服务端 `headObject` 可能查不到对象，confirm 会失败。

#### 完整代码链路

```text
UserProfileGrpcService.presignAvatarUpload
  -> UserProfileServiceImpl.presignAvatarUpload
     -> validatePositive / validateAvatarFileExt / validateAvatarContentLength
     -> UserInfoManager.findByUserId
     -> 生成 avatar/{userId}/{uuid}.{ext}
     -> S3Presigner.presignPutObject 生成 MinIO PUT 预签名 URL
```

### 8.5 ConfirmAvatarUpload

#### 接口解决什么问题

客户端上传头像后，确认 objectKey 属于当前用户，并确认 MinIO 中对象真实存在，然后把头像信息写入 `user_info.custom_avatar`。

#### gRPC 方法

```text
ConfirmAvatarUpload(ConfirmAvatarUploadRequest) returns (ConfirmAvatarUploadResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |
| `object_key` | `string` | 头像 object key，必须以 `avatar/{userId}/` 开头 |

#### grpcurl 测试

```powershell
$json = '{"userId":你的雪花userId,"objectKey":"avatar/你的雪花userId/test.jpg"}'
[System.IO.File]::WriteAllText("C:\tmp\confirm-avatar.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/ConfirmAvatarUpload < C:\tmp\confirm-avatar.json"
```

预期：

```json
{
  "profile": {
    "userId": "你的雪花userId",
    "avatar": {
      "originalKey": "avatar/你的雪花userId/test.jpg",
      "originalUrl": "https://minio-api.jianjiange.site/dating-hanlian/avatar/你的雪花userId/test.jpg",
      "minKey": "avatar/你的雪花userId/test.jpg",
      "minUrl": "https://minio-api.jianjiange.site/dating-hanlian/avatar/你的雪花userId/test.jpg",
      "midKey": "avatar/你的雪花userId/test.jpg",
      "midUrl": "https://minio-api.jianjiange.site/dating-hanlian/avatar/你的雪花userId/test.jpg",
      "status": "READY"
    }
  }
}
```

#### 数据库验收项

```sql
select user_id, custom_avatar
from user_info
where user_id = 你的雪花userId;
```

预期：`custom_avatar` 中有 `originalKey / minKey / midKey`。

#### Redis 验收项

- `ConfirmAvatarUpload` 会先调用 MinIO `headObject` 确认对象存在，写 DB 后会删除 `hanlian:user:profile:{userId}`。
- 再调用 `GetProfile` 会重新回源 DB，并在返回里带 `avatar`。`avatar` 会同时包含 object key 和由 `storage.public-base-url` 拼出的可访问 URL。
- 如果用户还没有上传头像，并且配置了 `storage.default-avatar-url`，`GetProfile` / gateway `/api/v1/user/me` 会返回默认头像 URL。
- 当前版本 confirm 后 `avatar.status = READY`。默认头像返回 `status = DEFAULT`。后续接入真实缩略图异步处理时，可以扩展为 `PROCESSING / READY / FAILED`。

### 8.6 GetUserInterests

#### 接口解决什么问题

查询某个用户的兴趣标签列表。

#### gRPC 方法

```text
GetUserInterests(GetUserInterestsRequest) returns (GetUserInterestsResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |

#### proto 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `interests` | `repeated UserInterest` | 兴趣标签列表 |

#### grpcurl 测试

```powershell
$json = '{"userId":你的雪花userId}'
[System.IO.File]::WriteAllText("C:\tmp\get-interests.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/GetUserInterests < C:\tmp\get-interests.json"
```

预期：

```json
{
  "interests": [
    {
      "interestCode": "music",
      "displayName": "音乐",
      "type": "INTEREST_TYPE_TEXT",
      "sortOrder": 0
    }
  ]
}
```

#### 完整代码链路

```text
UserProfileGrpcService.getUserInterests
  -> UserProfileServiceImpl.getUserInterests
     -> validatePositive
     -> UserProfileServiceImpl.getCachedInterests
        -> Redis GET hanlian:user:interest:{userId}
     -> 如果缓存未命中:
        -> UserInfoManager.findByUserId
        -> UserInterestManager.findByUserId
           -> UserInterestMapper.selectList
        -> UserProfileServiceImpl.cacheInterests
           -> Redis SET hanlian:user:interest:{userId} TTL 24h
```

### 8.7 ReplaceUserInterests

#### 接口解决什么问题

全量替换用户兴趣标签。适合 onboarding 或资料编辑页一次性保存兴趣列表。

#### gRPC 方法

```text
ReplaceUserInterests(ReplaceUserInterestsRequest) returns (ReplaceUserInterestsResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |
| `interests` | `repeated UserInterest` | 新的兴趣标签列表 |

#### UserInterest 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `interest_code` | `string` | 兴趣编码，例如 `music` |
| `display_name` | `string` | 展示名称，例如 `音乐` |
| `type` | `InterestType` | `INTEREST_TYPE_TEXT` 或 `INTEREST_TYPE_PICTURE` |
| `pic_key` | `string` | 图片兴趣 object key，不存完整 URL |
| `sort_order` | `int32` | 排序 |

#### grpcurl 测试

```powershell
$json = '{
  "userId": 你的雪花userId,
  "interests": [
    {"interestCode":"music","displayName":"音乐","type":"INTEREST_TYPE_TEXT","sortOrder":0},
    {"interestCode":"travel","displayName":"旅行","type":"INTEREST_TYPE_TEXT","sortOrder":1}
  ]
}'
[System.IO.File]::WriteAllText("C:\tmp\replace-interests.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/ReplaceUserInterests < C:\tmp\replace-interests.json"
```

预期：

```json
{
  "interests": [
    {
      "interestCode": "music",
      "displayName": "音乐",
      "type": "INTEREST_TYPE_TEXT",
      "sortOrder": 0
    },
    {
      "interestCode": "travel",
      "displayName": "旅行",
      "type": "INTEREST_TYPE_TEXT",
      "sortOrder": 1
    }
  ]
}
```

#### 完整代码链路

```text
UserProfileGrpcService.replaceUserInterests
  -> UserProfileServiceImpl.replaceUserInterests
     -> validatePositive / normalizeInterests / validateInterest
     -> UserInfoManager.findByUserId
     -> UserInterestManager.replaceByUserId
        -> DELETE FROM user_interest WHERE user_id = ?
        -> INSERT user_interest
     -> UserProfileServiceImpl.evictInterestCache
        -> Redis DEL hanlian:user:interest:{userId}
     -> UserInterestManager.findByUserId
     -> UserProfileServiceImpl.cacheInterests
```

#### 数据库验收项

```sql
select user_id, interest_code, display_name, type, pic_key, sort_order
from user_interest
where user_id = 你的雪花userId
order by sort_order, id;
```

#### Redis 验收项

- `ReplaceUserInterests` 写 DB 后会删除 `hanlian:user:interest:{userId}`。
- 再调用 `GetUserInterests` 会重新回源 DB 并写入缓存。

### 8.8 UpdateRegulationStatus

#### 接口解决什么问题

内部更新用户资料审核 / 监管状态。用于本地验收、运营后台或后续风控服务接入，不是普通 App 用户自助接口。

#### gRPC 方法

```text
UpdateRegulationStatus(UpdateRegulationStatusRequest) returns (UpdateRegulationStatusResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |
| `regulation_status` | `int32` | 目标审核 / 监管状态 |
| `reason` | `string` | 更新原因，当前用于调用记录，后续可落审核日志表 |

#### 状态值约定

| 值 | 含义 | CheckBan 行为 |
|---:|---|---|
| `0` | 正常 / 未审核 | 不封禁 |
| `1` | 待审核 | 不封禁 |
| `2` | 封禁 | 返回 `USER_BANNED` |
| `3` | 审核通过 | 不封禁 |
| `4` | 审核拒绝 | 不封禁 |
| `5` | 暂停 | 返回 `USER_SUSPENDED` |

#### grpcurl 设置待审核

```powershell
$json = '{"userId":你的雪花userId,"regulationStatus":1,"reason":"profile pending review"}'
[System.IO.File]::WriteAllText("C:\tmp\update-regulation.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/UpdateRegulationStatus < C:\tmp\update-regulation.json"
```

预期：

```json
{
  "profile": {
    "userId": "你的雪花userId",
    "regulationStatus": 1
  }
}
```

#### grpcurl 设置封禁并验证 CheckBan

```powershell
$json = '{"userId":你的雪花userId,"regulationStatus":2,"reason":"manual ban test"}'
[System.IO.File]::WriteAllText("C:\tmp\update-regulation.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/UpdateRegulationStatus < C:\tmp\update-regulation.json"

$json = '{"userId":你的雪花userId}'
[System.IO.File]::WriteAllText("C:\tmp\check-ban.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserIdentityService/CheckBan < C:\tmp\check-ban.json"
```

预期：

```json
{
  "banned": true,
  "reason": "USER_BANNED"
}
```

#### grpcurl 设置暂停并验证 CheckBan

```powershell
$json = '{"userId":你的雪花userId,"regulationStatus":5,"reason":"manual suspend test"}'
[System.IO.File]::WriteAllText("C:\tmp\update-regulation.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/UpdateRegulationStatus < C:\tmp\update-regulation.json"

cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserIdentityService/CheckBan < C:\tmp\check-ban.json"
```

预期：

```json
{
  "banned": true,
  "reason": "USER_SUSPENDED"
}
```

#### grpcurl 恢复正常

```powershell
$json = '{"userId":你的雪花userId,"regulationStatus":0,"reason":"restore normal"}'
[System.IO.File]::WriteAllText("C:\tmp\update-regulation.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/UpdateRegulationStatus < C:\tmp\update-regulation.json"

cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserIdentityService/CheckBan < C:\tmp\check-ban.json"
```

预期：`CheckBan` 返回 `{}` 或 `banned = false`。proto3 默认不显示 false，所以 `{}` 是正常结果。

#### 完整代码链路

```text
UserProfileGrpcService.updateRegulationStatus
  -> UserProfileServiceImpl.updateRegulationStatus
     -> validatePositive / validateRegulationStatus
     -> UserInfoManager.findByUserId
     -> UserInfoManager.updateRegulationStatus
        -> UserInfoMapper.update
     -> UserProfileServiceImpl.evictProfileCache
        -> Redis DEL hanlian:user:profile:{userId}
     -> UserProfileServiceImpl.evictBanStatusCache
        -> Redis DEL hanlian:user:ban:status:{userId}
     -> UserInfoManager.findByUserId
```

#### 数据库验收项

```sql
select user_id, regulation_status, updated_at
from user_info
where user_id = 你的雪花userId;
```

#### Redis 验收项

- `UpdateRegulationStatus` 更新 DB 后会删除 `hanlian:user:profile:{userId}`。
- 同时会删除 `hanlian:user:ban:status:{userId}`。
- 设置 `regulation_status = 2 / 5` 后，下一次 `CheckBan` 会重新读 DB 并写入封禁短缓存。

### 8.9 ListRegulationLogs

#### 接口解决什么问题

查询某个用户最近的审核 / 监管状态变更日志。用于运营后台、风控排查和本地验收。

#### gRPC 方法

```text
ListRegulationLogs(ListRegulationLogsRequest) returns (ListRegulationLogsResponse)
```

#### proto 请求字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` | `int64` | 业务用户 ID |
| `limit` | `int32` | 最多返回多少条，当前业务层默认 20，最大 100 |

#### proto 响应字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `logs` | `repeated RegulationLog` | 审核 / 监管状态变更日志列表 |

#### RegulationLog 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `int64` | 日志 ID |
| `user_id` | `int64` | 被修改状态的业务用户 ID |
| `before_status` | `int32` | 修改前状态 |
| `after_status` | `int32` | 修改后状态 |
| `reason` | `string` | 修改原因 |
| `operator_type` | `int32` | 操作者类型，当前默认 0 |
| `operator_id` | `string` | 操作者 ID，当前可为空 |
| `created_at_ms` | `int64` | 创建时间，毫秒时间戳 |

#### grpcurl 测试

```powershell
$json = '{"userId":你的雪花userId,"limit":5}'
[System.IO.File]::WriteAllText("C:\tmp\list-regulation-logs.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/ListRegulationLogs < C:\tmp\list-regulation-logs.json"
```

预期：

```json
{
  "logs": [
    {
      "id": "123",
      "userId": "你的雪花userId",
      "beforeStatus": 2,
      "afterStatus": 0,
      "reason": "restore normal with log test",
      "operatorType": 0,
      "createdAtMs": "1782710000000"
    }
  ]
}
```

#### 完整代码链路

```text
UserProfileGrpcService.listRegulationLogs
  -> UserProfileServiceImpl.listRegulationLogs
     -> validatePositive
     -> ensureUserExists
     -> UserRegulationLogManager.findByUserId
        -> UserRegulationLogMapper.selectList
     -> UserProfileServiceImpl.toRegulationLogItem
     -> UserProfileGrpcService.toRegulationLog
```

#### 数据库验收项

```sql
select id, user_id, before_status, after_status, reason, operator_type, operator_id, created_at
from user_regulation_log
where user_id = 你的雪花userId
order by created_at desc, id desc
limit 5;
```

预期：gRPC 返回顺序与 SQL 查询顺序一致。

### 8.10 mobile-gateway 兴趣接口验收

#### 接口列表

| HTTP 接口 | 作用 |
|---|---|
| `GET /api/v1/user/interests` | 查询当前登录用户兴趣 |
| `PUT /api/v1/user/interests` | 全量替换当前登录用户兴趣 |
| `GET /api/v1/user/me` | 查询当前用户资料，响应中包含 `avatar` 和 `interests` |

#### 登录拿 token

```powershell
$loginBody = @{
  deviceId = "gateway-interest-device-001"
  platform = 1
  appName = "hanlian"
} | ConvertTo-Json

$login = Invoke-RestMethod `
  -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/login-device" `
  -ContentType "application/json" `
  -Body $loginBody

$token = $login.data.accessToken
```

#### 替换兴趣

```powershell
$json = '{
  "interests": [
    {"interestCode":"music","displayName":"音乐","type":1,"sortOrder":0},
    {"interestCode":"travel","displayName":"旅行","type":1,"sortOrder":1}
  ]
}'
[System.IO.File]::WriteAllText("C:\tmp\gateway-interests.json", $json, [System.Text.UTF8Encoding]::new($false))

Invoke-RestMethod `
  -Method Put `
  -Uri "http://localhost:8080/api/v1/user/interests" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json; charset=utf-8" `
  -InFile "C:\tmp\gateway-interests.json"
```

#### 查询兴趣

```powershell
$result = Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/user/interests" `
  -Headers @{ Authorization = "Bearer $token" }

$result | ConvertTo-Json -Depth 6
```

#### 查询资料并确认 interests

```powershell
$me = Invoke-RestMethod `
  -Method Get `
  -Uri "http://localhost:8080/api/v1/user/me" `
  -Headers @{ Authorization = "Bearer $token" }

$me | ConvertTo-Json -Depth 8
```

预期：

- `PUT /api/v1/user/interests` 返回 `code = 0`。
- `GET /api/v1/user/interests` 返回 `data.interests`。
- `GET /api/v1/user/me` 返回 `data.interests`。

### 8.11 Windows PowerShell 中文 grpcurl 注意点

PowerShell 管道直接传中文 JSON 时，可能把中文写成 `??`，甚至出现 BOM 导致：

```text
invalid character 'ï' looking for beginning of value
```

推荐做法：把 JSON 写入 UTF-8 无 BOM 文件，再让 grpcurl 从 stdin 读取：

```powershell
$json = '{"userId":你的雪花userId,"location":"北京","education":"本科"}'
[System.IO.File]::WriteAllText("C:\tmp\request.json", $json, [System.Text.UTF8Encoding]::new($false))
cmd /c "C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d @ 127.0.0.1:19081 dating.user.v1.UserProfileService/UpdateProfile < C:\tmp\request.json"
```

如果数据库里已经写入了 `??`，重新用无 BOM JSON 文件调用 `UpdateProfile` 或 `UpsertOnboarding` 覆盖即可。

---

## 9. gRPC 异常返回验收

当前统一入口：

```text
UserIdentityGrpcService.handleException
UserProfileGrpcService.handleException
```

| Java 异常 | gRPC 状态 |
|---|---|
| `IllegalArgumentException` | `INVALID_ARGUMENT` |
| 其他异常 | `INTERNAL` |

已验收异常：

| 场景 | 预期 |
|---|---|
| `phoneE164` 为空 | `INVALID_ARGUMENT: phoneE164 is required` |
| `deviceId` 为空 | `INVALID_ARGUMENT: deviceId is required` |
| `userId = 0` | `INVALID_ARGUMENT: userId must be positive` |
| `thirdPartyUserId` 为空 | `INVALID_ARGUMENT: thirdPartyUserId is required` |
| Profile 用户不存在 | `INVALID_ARGUMENT: user not found` |

记忆点：

- 参数校验在 Service 层做。
- gRPC 状态转换在 gRPC 层做。
- 这样 HTTP / gRPC 以后可以复用同一套 service 逻辑。

---

## 10. 数据库迁移验收

当前关键迁移：

| 版本 | 文件 | 作用 |
|---|---|---|
| `20260625.01` | `V20260625_01__init_user_identity_schema.sql` | 创建身份相关表 |
| `20260626.01` | `V20260626_01__add_user_business_id.sql` | 给 `user_info` 增加业务 `user_id` |
| `20260627.01` | `V20260627_01__add_user_third_party_registration.sql` | 创建第三方账号绑定表 |
| `20260629.01` | `V20260629_01__create_user_interest.sql` | 创建用户兴趣标签表 |

查询 Flyway 历史：

```sql
select installed_rank, version, description, success
from flyway_history_user
order by installed_rank;
```

预期：

- `20260625.01` 成功。
- `20260626.01` 成功。
- `20260627.01` 成功。
- `20260629.01` 成功。

查询 `user_info` 字段：

```sql
select id, user_id, app_name, pending, nickname, regulation_status, created_at
from user_info
order by id desc
limit 10;
```

关键记忆点：

- 已执行过的 Flyway 脚本不要直接改。
- 已上线 / 已执行的结构变化用新的 migration 文件追加。
- PostgreSQL 不建议为了字段显示顺序重建表，查询时显式 select 字段顺序即可。

---

## 11. 雪花 ID 验收

公共 ID 生成器位置：

```text
dating-server/common/src/main/java/com/aurora/dating/common/id/SnowflakeIdGenerator.java
```

配置位置：

```yaml
aurora:
  id:
    datacenter-id: 1
    worker-id: 1
```

当前约定：

| 服务 | worker-id |
|---|---:|
| user-service | `1` |
| post-service | `2` |

验收点：

- 新建用户返回的 `userId` 是很大的雪花 ID。
- `user_info.id` 和 `user_info.user_id` 不相同。
- user-service 对外只使用 `user_id`。

记忆点：

- `common` 只放通用算法，不放具体业务逻辑。
- 每个服务要配置不同 `worker-id`。
- 如果改了 `common` 后单独启动某个服务，要先执行 `mvn -pl common install -DskipTests`。

---

## 12. 推荐验收顺序

建议每次大改 user-service 后按这个顺序跑：

1. 编译：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl user-service -am -DskipTests package
```

2. 启动 user-service。
3. HTTP `GET /internal/ping`。
4. HTTP `GET /internal/check/db`。
5. HTTP `GET /internal/check/redis`。
6. gRPC `ResolveOrCreateByPhone` 第一次请求。
7. gRPC `ResolveOrCreateByPhone` 重复请求。
8. gRPC `ResolveOrCreateByDevice` 第一次请求。
9. gRPC `ResolveOrCreateByDevice` 重复请求。
10. gRPC `CheckBan` 正常用户。
11. 数据库手动改 `regulation_status = 2`。
12. gRPC `CheckBan` 封禁用户。
13. 恢复 `regulation_status = 0`。
14. 跑三个异常场景。
15. gRPC `ResolveOrCreateByThirdParty` 第一次请求。
16. gRPC `ResolveOrCreateByThirdParty` 重复请求。
17. gRPC `GetProfile`。
18. gRPC `UpdateProfile`。
19. gRPC `UpsertOnboarding`。
20. Profile Redis 缓存验收。
21. gRPC `PresignAvatarUpload`。
22. gRPC `ConfirmAvatarUpload`。
23. gRPC `ReplaceUserInterests`。
24. gRPC `GetUserInterests`。
25. gRPC `UpdateRegulationStatus` 设置待审核。
26. gRPC `UpdateRegulationStatus` 设置封禁，并用 `CheckBan` 验证。
27. gRPC `UpdateRegulationStatus` 恢复正常。
28. gRPC `ListRegulationLogs` 查询审核日志。
29. gateway `PUT /api/v1/user/interests`。
30. gateway `GET /api/v1/user/interests`。
31. gateway `GET /api/v1/user/me`，确认返回 `avatar` 和 `interests`。
32. 查 `flyway_history_user`、`user_info`、`user_interest`、`user_regulation_log`、`user_login_phone`、`user_device_registration`、`user_third_party_registration`。

---

## 13. IDEA 复习入口

你复习每个接口时，可以按下面顺序看。

HTTP debug：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/PingController.java
dating-server/user-service/src/main/java/com/aurora/dating/user/DbCheckController.java
dating-server/user-service/src/main/java/com/aurora/dating/user/RedisCheckController.java
```

gRPC 入口：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/grpc/UserIdentityGrpcService.java
dating-server/user-service/src/main/java/com/aurora/dating/user/grpc/UserProfileGrpcService.java
dating-server/user-service/src/main/java/com/aurora/dating/user/grpc/UserGrpcServerLifecycle.java
```

业务层：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/service/UserIdentityService.java
dating-server/user-service/src/main/java/com/aurora/dating/user/service/impl/UserIdentityServiceImpl.java
dating-server/user-service/src/main/java/com/aurora/dating/user/service/UserProfileService.java
dating-server/user-service/src/main/java/com/aurora/dating/user/service/impl/UserProfileServiceImpl.java
```

Manager：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserInfoManager.java
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserLoginPhoneManager.java
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserDeviceRegistrationManager.java
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserThirdPartyRegistrationManager.java
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserInterestManager.java
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserRegulationLogManager.java
```

Mapper：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserInfoMapper.java
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserLoginPhoneMapper.java
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserDeviceRegistrationMapper.java
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserThirdPartyRegistrationMapper.java
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserInterestMapper.java
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserRegulationLogMapper.java
```

Entity：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserInfoEntity.java
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserLoginPhoneEntity.java
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserDeviceRegistrationEntity.java
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserThirdPartyRegistrationEntity.java
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserInterestEntity.java
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserRegulationLogEntity.java
```

数据库迁移：

```text
dating-server/user-service/src/main/resources/db/migration/V20260625_01__init_user_identity_schema.sql
dating-server/user-service/src/main/resources/db/migration/V20260626_01__add_user_business_id.sql
dating-server/user-service/src/main/resources/db/migration/V20260627_01__add_user_third_party_registration.sql
dating-server/user-service/src/main/resources/db/migration/V20260629_01__create_user_interest.sql
dating-server/user-service/src/main/resources/db/migration/V20260629_02__create_user_regulation_log.sql
```

公共雪花 ID：

```text
dating-server/common/src/main/java/com/aurora/dating/common/id/SnowflakeIdGenerator.java
dating-server/common/src/main/java/com/aurora/dating/common/id/SnowflakeIdAutoConfiguration.java
dating-server/common/src/main/java/com/aurora/dating/common/id/SnowflakeIdProperties.java
```

---

## 14. 当前实现与技术方案差异

| 技术方案项 | 当前状态 |
|---|---|
| `ResolveOrCreateByPhone` | 已完成 |
| `ResolveOrCreateByDevice` | 已完成 |
| `ResolveOrCreateByThirdParty` | 已完成 |
| `CheckBan` | 已完成，已接 Redis 短缓存 |
| 业务 `user_id` | 已完成，使用 common 雪花 ID |
| HTTP debug DB / Redis 检查 | 已完成 |
| gRPC 参数校验和异常转换 | 已完成 |
| Redisson 注册锁 | 已完成，覆盖 phone / device / third-party |
| 封禁 Redis 短缓存 | 已完成 |
| 用户资料 Profile | 已完成 GetProfile / UpdateProfile / UpsertOnboarding |
| Profile Redis 缓存 | 已完成，GetProfile cache-aside，写后删除缓存 |
| 头像 presign / confirm | 已完成，支持 presign、confirm、Profile 返回 avatar |
| 兴趣标签 | 已完成 GetUserInterests / ReplaceUserInterests，已接 Redis 缓存和 gateway 接口 |
| 资料审核 / 监管状态 | 已完成 UpdateRegulationStatus 最小闭环，支持更新 regulation_status、写入审核日志、查询审核日志，并联动 CheckBan 缓存 |

当前文档验收的是 user-service P1 身份 + Profile + 头像 + 兴趣标签 + 监管状态与审核日志最小闭环版本。缩略图异步生成、对象存储真实 PUT 校验等成熟产品能力后续继续补。


