# user-service 接口验收文档

> 用途：给你自己测试 user-service 用，也帮助你记住每个接口解决什么问题、代码链路怎么走。  
> 范围：HTTP debug 接口、gRPC 身份接口、数据库 / Redis 验收点、异常验收点、关键设计记忆点。  
> 当前版本：P0 身份解析链路，包含手机号、设备、封禁检查。资料页、头像、兴趣、第三方登录暂未完整实现。

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
| `flyway_history_user` | user-service Flyway 迁移历史表 |

### 1.4 当前 Redis key

| Key | 作用 |
|---|---|
| `hanlian:user-service:check:redis` | `/internal/check/redis` 写入的连通性测试 key，TTL 60 秒 |

当前 P0 身份解析主链路暂时不依赖 Redis。Redis 主要用于连通性检查，后续封禁缓存、资料缓存、注册锁可以继续扩展。

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
     -> UserInfoManager / UserLoginPhoneManager / UserDeviceRegistrationManager
        -> UserInfoMapper / UserLoginPhoneMapper / UserDeviceRegistrationMapper
           -> PostgreSQL
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
| `created` | `bool` | proto 已预留，当前代码暂未设置 |

### 4.4 grpcurl 测试

第一次请求：

```powershell
'{"phoneE164":"+8613800138001","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByPhone
```

预期：

```json
{
  "userId": "一个很大的雪花ID",
  "pending": true
}
```

重复请求同一个手机号：

```powershell
'{"phoneE164":"+8613800138001","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByPhone
```

预期：返回同一个 `userId`。

### 4.5 完整代码链路

```text
UserIdentityGrpcService.resolveOrCreateByPhone
  -> UserIdentityServiceImpl.resolveOrCreateByPhone
     -> validateText(phoneE164, "phoneE164")
     -> validateText(appName, "appName")
     -> UserLoginPhoneManager.findByPhoneAndApp
        -> UserLoginPhoneMapper.selectOne
     -> 如果已有绑定:
        -> UserInfoManager.touchLastOpenAt
        -> UserInfoManager.findByUserId
           -> UserInfoMapper.selectOne
     -> 如果没有绑定:
        -> UserInfoManager.insertPlaceholder
           -> SnowflakeIdGenerator.nextId
           -> UserInfoMapper.insert
        -> UserLoginPhoneManager.insertBinding
           -> UserLoginPhoneMapper.insert
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
| `created` | `bool` | proto 已预留，当前代码暂未设置 |

### 5.4 grpcurl 测试

第一次请求：

```powershell
'{"deviceId":"device-test-001","platform":"PLATFORM_IOS","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByDevice
```

预期：

```json
{
  "userId": "一个很大的雪花ID",
  "pending": true
}
```

重复请求同一个设备：

```powershell
'{"deviceId":"device-test-001","platform":"PLATFORM_IOS","appName":"hanlian"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/ResolveOrCreateByDevice
```

预期：返回同一个 `userId`。

### 5.5 完整代码链路

```text
UserIdentityGrpcService.resolveOrCreateByDevice
  -> UserIdentityServiceImpl.resolveOrCreateByDevice
     -> validateText(deviceId, "deviceId")
     -> validatePositive(platform, "platform")
     -> validateText(appName, "appName")
     -> UserDeviceRegistrationManager.findByDeviceAndApp
        -> UserDeviceRegistrationMapper.selectOne
     -> 如果已有绑定:
        -> UserInfoManager.touchLastOpenAt
        -> UserInfoManager.findByUserId
           -> UserInfoMapper.selectOne
     -> 如果没有绑定:
        -> UserInfoManager.insertPlaceholder
           -> SnowflakeIdGenerator.nextId
           -> UserInfoMapper.insert
        -> UserDeviceRegistrationManager.insertBinding
           -> UserDeviceRegistrationMapper.insert
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
     -> UserInfoManager.findByUserId
        -> UserInfoMapper.selectOne
     -> 判断 regulation_status
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

### 6.7 异常验收项

`userId` 为 0：

```powershell
'{"userId":0}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -import-path E:\heart-dev\workspace\hanlian-workspace\proto\user\src\main\proto -proto user.proto -d '@' 127.0.0.1:19081 dating.user.v1.UserIdentityService/CheckBan
```

预期：

```text
Code: InvalidArgument
Message: userId must be positive
```

### 6.8 关键设计记忆点

- CheckBan 是后续 gateway、post-service、match-service 都可能调用的基础能力。
- 当前 P0 直接查 PostgreSQL，后续可以加 Redis 短缓存。
- 封禁状态应该由 user-service 统一解释，其他服务不要自己猜 `regulation_status` 的含义。

---

## 7. ResolveOrCreateByThirdParty

### 7.1 接口解决什么问题

第三方登录后，根据平台和第三方用户 ID 找到或创建用户。

### 7.2 当前状态

proto 已定义，服务端暂未实现。

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

### 7.4 当前验收项

调用后预期返回：

```text
Code: Unimplemented
Message: ResolveOrCreateByThirdParty is not implemented yet
```

### 7.5 关键设计记忆点

- 这部分需要新增第三方绑定表，目前不是 P0。
- 第三方登录也应该返回同一套业务 `user_id`。

---

## 8. gRPC 异常返回验收

当前统一入口：

```text
UserIdentityGrpcService.handleException
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

记忆点：

- 参数校验在 Service 层做。
- gRPC 状态转换在 gRPC 层做。
- 这样 HTTP / gRPC 以后可以复用同一套 service 逻辑。

---

## 9. 数据库迁移验收

当前关键迁移：

| 版本 | 文件 | 作用 |
|---|---|---|
| `20260625.01` | `V20260625_01__init_user_identity_schema.sql` | 创建身份相关表 |
| `20260626.01` | `V20260626_01__add_user_business_id.sql` | 给 `user_info` 增加业务 `user_id` |

查询 Flyway 历史：

```sql
select installed_rank, version, description, success
from flyway_history_user
order by installed_rank;
```

预期：

- `20260625.01` 成功。
- `20260626.01` 成功。

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

## 10. 雪花 ID 验收

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

## 11. 推荐验收顺序

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
15. 查 `flyway_history_user`、`user_info`、`user_login_phone`、`user_device_registration`。

---

## 12. IDEA 复习入口

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
dating-server/user-service/src/main/java/com/aurora/dating/user/grpc/UserGrpcServerLifecycle.java
```

业务层：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/service/UserIdentityService.java
dating-server/user-service/src/main/java/com/aurora/dating/user/service/impl/UserIdentityServiceImpl.java
```

Manager：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserInfoManager.java
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserLoginPhoneManager.java
dating-server/user-service/src/main/java/com/aurora/dating/user/manager/UserDeviceRegistrationManager.java
```

Mapper：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserInfoMapper.java
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserLoginPhoneMapper.java
dating-server/user-service/src/main/java/com/aurora/dating/user/mapper/UserDeviceRegistrationMapper.java
```

Entity：

```text
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserInfoEntity.java
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserLoginPhoneEntity.java
dating-server/user-service/src/main/java/com/aurora/dating/user/entity/UserDeviceRegistrationEntity.java
```

数据库迁移：

```text
dating-server/user-service/src/main/resources/db/migration/V20260625_01__init_user_identity_schema.sql
dating-server/user-service/src/main/resources/db/migration/V20260626_01__add_user_business_id.sql
```

公共雪花 ID：

```text
dating-server/common/src/main/java/com/aurora/dating/common/id/SnowflakeIdGenerator.java
dating-server/common/src/main/java/com/aurora/dating/common/id/SnowflakeIdAutoConfiguration.java
dating-server/common/src/main/java/com/aurora/dating/common/id/SnowflakeIdProperties.java
```

---

## 13. 当前实现与技术方案差异

| 技术方案项 | 当前状态 |
|---|---|
| `ResolveOrCreateByPhone` | 已完成 |
| `ResolveOrCreateByDevice` | 已完成 |
| `ResolveOrCreateByThirdParty` | proto 已有，服务端暂未实现 |
| `CheckBan` | 已完成，当前直查 DB |
| 业务 `user_id` | 已完成，使用 common 雪花 ID |
| HTTP debug DB / Redis 检查 | 已完成 |
| gRPC 参数校验和异常转换 | 已完成 |
| Redisson 注册锁 | 暂未实现 |
| 封禁 Redis 短缓存 | 暂未实现 |
| 用户资料 Profile | 暂未实现 |
| 头像 presign / confirm | 暂未实现 |
| 兴趣、标签、资料审核 | 暂未实现 |

当前文档验收的是 user-service P0 身份解析版本，不要求一次性完成完整用户域。
