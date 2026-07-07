# user-service 接口链路设计

> 目的：把 user-service 当前所有 gRPC 功能，从 proto request / response 参数，到 gRPC 入口、service 业务逻辑、manager / mapper / DB / Redis / 对象存储影响串起来。  
> 当前范围：`Ping`、身份解析与绑定、封禁检查、资料读写、onboarding、头像 presign / confirm、兴趣读写、监管状态与日志。  
> 当前状态：代码已覆盖身份 P0、Profile P0/P1、头像本地 S3 直传链路、兴趣和监管状态基础链路；短信校验、第三方 token 校验、JWT 签发不属于 user-service，在 mobile-gateway。

---

## 1. 总体入口和分层

### 1.1 proto 文件

```text
proto/user/src/main/proto/user.proto
proto/user/user.proto
```

修改 proto 后需要在 proto 模块安装：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\user
mvn clean install -DskipTests
```

### 1.2 gRPC 服务入口

| proto service | Java 入口 | 说明 |
| --- | --- | --- |
| `dating.user.v1.UserService` | `UserGrpcService` | 旧 `Ping` 兼容入口 |
| `dating.user.v1.UserIdentityService` | `UserIdentityGrpcService` | 身份解析、账号绑定、封禁检查 |
| `dating.user.v1.UserProfileService` | `UserProfileGrpcService` | 资料、头像、兴趣、监管状态 |

### 1.3 HTTP debug 入口

当前 user-service 没有业务 HTTP 接口，HTTP 只做本地连通性检查：

| 路径 | 入口 | 作用 |
| --- | --- | --- |
| `GET /internal/ping` | `PingController` | HTTP 存活检查 |
| `GET /internal/check/db` | `DbCheckController` | 数据库连通性检查 |
| `GET /internal/check/redis` | `RedisCheckController` | Redis 连通性检查 |
| `GET /internal/check/minio` | `MinioCheckController` | 对象存储连通性检查 |
| `GET /internal/check/minio/object` | `MinioCheckController` | 对象存在性检查 |

业务 HTTP 入口在 mobile-gateway，gateway 再通过 gRPC 调 user-service。

### 1.4 核心业务分层

```text
Grpc
  -> Service
     -> Manager
        -> Mapper
           -> PostgreSQL / Redis / S3
```

主要类：

| 层级 | 文件 | 作用 |
| --- | --- | --- |
| gRPC | `UserIdentityGrpcService` | 身份 RPC 入参出参编排 |
| gRPC | `UserProfileGrpcService` | Profile RPC 入参出参编排，组装 `UserProfile` proto |
| Service | `UserIdentityServiceImpl` | 注册锁、绑定查找、封禁缓存 |
| Service | `UserProfileServiceImpl` | 资料缓存、头像签名、兴趣替换、监管日志 |
| Manager | `UserInfoManager` | 用户主表读写 |
| Manager | `UserLoginPhoneManager` | 手机绑定表读写 |
| Manager | `UserDeviceRegistrationManager` | 设备绑定表读写 |
| Manager | `UserThirdPartyRegistrationManager` | 三方绑定表读写 |
| Manager | `UserInterestManager` | 兴趣表读写 |
| Manager | `UserRegulationLogManager` | 监管日志读写 |

---

## 2. Ping 连通性检查

### 2.1 proto 定义

```proto
service UserService {
  rpc Ping(PingRequest) returns (PingResponse);
}

message PingRequest { string message = 1; }
message PingResponse { string message = 1; }
```

### 2.2 参数

| request 字段 | 类型 | 说明 |
| --- | --- | --- |
| `message` | `string` | 测试文本 |

| response 字段 | 类型 | 说明 |
| --- | --- | --- |
| `message` | `string` | 返回 pong 文本 |

### 2.3 gRPC 接口

```text
dating.user.v1.UserService/Ping
```

grpcurl 示例：

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/user/src/main/proto/user.proto -d '@' localhost:19083 dating.user.v1.UserService/Ping
```

### 2.4 数据影响

不读写 DB / Redis / S3。

---

## 3. ResolveOrCreateByPhone 手机身份解析

### 3.1 proto 定义

```proto
rpc ResolveOrCreateByPhone(ResolveOrCreateByPhoneRequest) returns (ResolveOrCreateResponse);

message ResolveOrCreateByPhoneRequest {
  string phone_e164 = 1;
  string app_name = 2;
}

message ResolveOrCreateResponse {
  int64 user_id = 1;
  bool pending = 2;
  bool created = 3;
}
```

### 3.2 参数

| request 字段 | 类型 | 说明 |
| --- | --- | --- |
| `phone_e164` | `string` | 已由 gateway 验证过的手机号，建议 E.164 格式 |
| `app_name` | `string` | App 名称，当前常用 `hanlian` |

| response 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | `int64` | 业务用户 ID，对应 `user_info.user_id` |
| `pending` | `bool` | 是否资料未补全 |
| `created` | `bool` | 本次是否创建了新用户 |

### 3.3 gRPC 接口

```text
dating.user.v1.UserIdentityService/ResolveOrCreateByPhone
```

### 3.4 服务逻辑全流程

```text
UserIdentityGrpcService.resolveOrCreateByPhone
  -> UserIdentityServiceImpl.resolveOrCreateByPhone(phoneE164, appName)
     -> 校验 phoneE164 / appName 非空
     -> Redisson lock: {prefix}:lock:user:register:phone:{phoneE164}:{appName}
     -> UserLoginPhoneManager.findByPhoneAndApp
        -> 命中：UserInfoManager.touchLastOpenAt(userId) -> findByUserId -> created=false
        -> 未命中：UserInfoManager.insertPlaceholder(appName)
                  UserLoginPhoneManager.insertBinding(userId, phoneE164, appName)
                  created=true
     -> 释放注册锁
  -> ResolveOrCreateResponse(user_id, pending, created)
```

### 3.5 DB / Redis 影响

DB：

```text
读取 user_login_phone。
命中时更新 user_info.last_open_at。
未命中时插入 user_info placeholder，并插入 user_login_phone 绑定。
```

Redis：

```text
使用 Redisson 注册锁，key = {prefix}:lock:user:register:phone:{phoneE164}:{appName}。
当前不缓存手机号绑定结果。
```

### 3.6 并发与幂等

手机号 + appName 维度加注册锁，DB 还有唯一索引 `uk_user_login_phone_phone_app`。重复请求命中同一绑定，不重复创建用户。

---

## 4. ResolveOrCreateByDevice 设备身份解析

### 4.1 proto 定义

```proto
rpc ResolveOrCreateByDevice(ResolveOrCreateByDeviceRequest) returns (ResolveOrCreateResponse);

message ResolveOrCreateByDeviceRequest {
  string device_id = 1;
  Platform platform = 2;
  string app_name = 3;
}
```

### 4.2 参数

| request 字段 | 类型 | 说明 |
| --- | --- | --- |
| `device_id` | `string` | 客户端设备标识 |
| `platform` | `enum` | `1=iOS`、`2=Android`、`3=Web` |
| `app_name` | `string` | App 名称 |

response 同 `ResolveOrCreateResponse`。

### 4.3 链路

```text
UserIdentityGrpcService.resolveOrCreateByDevice
  -> UserIdentityServiceImpl.resolveOrCreateByDevice(deviceId, platform, appName)
     -> 校验 deviceId / platform / appName
     -> Redisson lock: {prefix}:lock:user:register:device:{platform}:{deviceId}:{appName}
     -> UserDeviceRegistrationManager.findByDeviceAndApp
        -> 命中：touchLastOpenAt -> 返回已有用户
        -> 未命中：insertPlaceholder -> insert device binding
```

### 4.4 DB / Redis 影响

读取 / 写入 `user_device_registration`，可能创建 `user_info` placeholder。Redis 只用于注册锁。

### 4.5 当前边界

deviceId 不是强身份；代码没有接 App Attest / Play Integrity，也没有客户端持久化兜底 token。快速登录刷号和卸载重装找回问题属于后续风控/产品决策。

---

## 5. ResolveOrCreateByThirdParty 三方身份解析

### 5.1 proto 定义

```proto
rpc ResolveOrCreateByThirdParty(ResolveOrCreateByThirdPartyRequest) returns (ResolveOrCreateResponse);

message ResolveOrCreateByThirdPartyRequest {
  ThirdPartyPlatform platform = 1;
  string third_party_user_id = 2;
  string app_name = 3;
  string email = 4;
}
```

### 5.2 参数

| request 字段 | 类型 | 说明 |
| --- | --- | --- |
| `platform` | `enum` | `1=Google`、`2=Apple`、`3=Wechat` |
| `third_party_user_id` | `string` | 三方平台用户唯一 ID |
| `app_name` | `string` | App 名称 |
| `email` | `string` | 可选邮箱，例如 Google email |

### 5.3 链路

```text
UserIdentityGrpcService.resolveOrCreateByThirdParty
  -> UserIdentityServiceImpl.resolveOrCreateByThirdParty(platform, thirdPartyUserId, appName, email)
     -> 校验 platform / thirdPartyUserId / appName
     -> Redisson lock: {prefix}:lock:user:register:third-party:{platform}:{thirdPartyUserId}:{appName}
     -> UserThirdPartyRegistrationManager.findByPlatformAndThirdPartyUserId
        -> 命中：touchLastOpenAt -> 返回已有用户
        -> 未命中：insertPlaceholder -> insert third_party binding
```

### 5.4 DB / Redis 影响

读取 / 写入 `user_third_party_registration`，可能创建 `user_info` placeholder。Redis 只用于注册锁。

### 5.5 当前边界

user-service 默认信任 gateway 已验证第三方 token；真实 OAuth token 校验不在 user-service。

---

## 6. BindPhone 绑定手机号

### 6.1 proto 定义

```proto
rpc BindPhone(BindPhoneRequest) returns (BindAccountResponse);

message BindPhoneRequest {
  int64 user_id = 1;
  string phone_e164 = 2;
  string app_name = 3;
}

message BindAccountResponse { int64 user_id = 1; }
```

### 6.2 链路

```text
UserIdentityGrpcService.bindPhone
  -> UserIdentityServiceImpl.bindPhone(userId, phoneE164, appName)
     -> 校验 userId / phoneE164 / appName
     -> 手机号注册锁
     -> UserInfoManager.findByUserId，不存在则报错
     -> UserLoginPhoneManager.findByPhoneAndApp
        -> 绑定到其他 userId：报 phone already bound
        -> 未绑定：insertBinding
        -> 已绑定当前 userId：幂等返回
     -> touchLastOpenAt
```

### 6.3 DB / Redis 影响

可能插入 `user_login_phone`，更新 `user_info.last_open_at`。Redis 使用手机号注册锁。

---

## 7. BindThirdParty 绑定三方账号

### 7.1 proto 定义

```proto
rpc BindThirdParty(BindThirdPartyRequest) returns (BindAccountResponse);

message BindThirdPartyRequest {
  int64 user_id = 1;
  ThirdPartyPlatform platform = 2;
  string third_party_user_id = 3;
  string app_name = 4;
  string email = 5;
}
```

### 7.2 链路

```text
UserIdentityGrpcService.bindThirdParty
  -> UserIdentityServiceImpl.bindThirdParty(userId, platform, thirdPartyUserId, appName, email)
     -> 校验参数
     -> 三方注册锁
     -> UserInfoManager.findByUserId
     -> UserThirdPartyRegistrationManager.findByPlatformAndThirdPartyUserId
        -> 绑定到其他 userId：报 third party account already bound
        -> 未绑定：insertBinding
        -> 已绑定当前 userId：幂等返回
     -> touchLastOpenAt
```

---

## 8. CheckBan 封禁检查

### 8.1 proto 定义

```proto
rpc CheckBan(CheckBanRequest) returns (CheckBanResponse);

message CheckBanRequest { int64 user_id = 1; }
message CheckBanResponse {
  bool banned = 1;
  string reason = 2;
  int64 banned_at_ms = 3;
  string message = 4;
}
```

### 8.2 链路

```text
UserIdentityGrpcService.checkBan
  -> UserIdentityServiceImpl.isBanned(userId)
     -> 校验 userId > 0
     -> Redis GET {prefix}:user:ban:status:{userId}
        -> 命中 true / false：直接返回
        -> 未命中：UserInfoManager.findByUserId
             -> regulation_status in (2, 5) 表示封禁
             -> Redis SET true/false，TTL = user.cache.banStatusTtl
  -> CheckBanResponse(banned, reason, message)
```

### 8.3 DB / Redis 影响

读取 `user_info.regulation_status`。Redis key：

```text
{prefix}:user:ban:status:{userId}
```

### 8.4 当前边界

当前返回的 `reason` 只有封禁时固定 `USER_BANNED`，`banned_at_ms` 固定 0；尚未区分 suspended、运营级封禁来源和真实封禁时间。

---

## 9. GetProfile 查询用户资料

### 9.1 proto 定义

```proto
rpc GetProfile(GetProfileRequest) returns (GetProfileResponse);

message GetProfileRequest { int64 user_id = 1; }
message GetProfileResponse { UserProfile profile = 1; }
```

`UserProfile` 关键字段：`user_id`、`app_name`、`pending`、`nickname`、`gender`、`age`、`birthday`、`bio`、`location`、`occupation`、`education`、`height`、`regulation_status`、`avatar`、`interests`、`profile_completion`、`missing_fields`。

### 9.2 链路

```text
UserProfileGrpcService.getProfile
  -> UserProfileServiceImpl.getProfile(userId)
     -> Redis GET {prefix}:user:profile:{userId}
        -> 命中：反序列化 UserInfoEntity
        -> 未命中：UserInfoManager.findByUserId -> cacheProfile
  -> UserProfileGrpcService.toUserProfile
     -> 解析 custom_avatar JSON，补 object URL / 默认头像
     -> UserProfileServiceImpl.getUserInterests(userId)
     -> 计算 profile_completion 和 missing_fields
```

### 9.3 DB / Redis 影响

读取 `user_info`，读取兴趣时可能读取 `user_interest`。Redis：

```text
{prefix}:user:profile:{userId}
{prefix}:user:interest:{userId}
```

### 9.4 当前边界

`toUserProfile` 每次都会补兴趣，因此批量资料如果很多，会逐个调用兴趣读取；当前 batch 限制 100，不是设计文档中的 200。

---

## 10. BatchGetProfile 批量查询资料

### 10.1 proto 定义

```proto
rpc BatchGetProfile(BatchGetProfileRequest) returns (BatchGetProfileResponse);

message BatchGetProfileRequest { repeated int64 user_ids = 1; }
message BatchGetProfileResponse { repeated UserProfile profiles = 1; }
```

### 10.2 链路

```text
UserProfileGrpcService.batchGetProfile
  -> UserProfileServiceImpl.batchGetProfile(userIds)
     -> 校验非空，去重，最多 100 个
     -> 逐个读 Redis profile cache
     -> miss 集合 UserInfoManager.findByUserIds
     -> 回填 Redis
     -> 按请求 userId 顺序返回存在的用户
  -> 每个 UserInfoEntity 转 UserProfile，并补兴趣 / 头像 / 完成度
```

---

## 11. UpdateProfile 编辑资料

### 11.1 proto 定义

```proto
rpc UpdateProfile(UpdateProfileRequest) returns (UpdateProfileResponse);

message UpdateProfileRequest {
  int64 user_id = 1;
  string nickname = 2;
  int32 age = 3;
  string bio = 4;
  string location = 5;
  string occupation = 6;
  string education = 7;
  int32 height = 8;
}
```

### 11.2 链路

```text
UserProfileGrpcService.updateProfile
  -> UserProfileServiceImpl.updateProfile
     -> 校验 nickname <= 64、age 0..120、bio <= 500、location/occupation/education <= 128、height 0..260
     -> UserInfoManager.findByUserId
     -> UserInfoManager.updateProfile
        -> 更新 nickname / age / bio / preferred_location / profession / education / height
     -> 删除 profile cache
     -> findByUserId 返回最新资料
```

### 11.3 当前边界

proto3 标量无法区分“未传”和“传空/0”，gateway 当前会把 null 转成空字符串或 0。动态 SET 是否跳过空值以 `UserInfoManager.updateProfile` 当前实现为准，调用方要谨慎处理清空字段语义。

---

## 12. UpsertOnboarding 补全资料

### 12.1 proto 定义

```proto
rpc UpsertOnboarding(UpsertOnboardingRequest) returns (UpsertOnboardingResponse);

message UpsertOnboardingRequest {
  int64 user_id = 1;
  string nickname = 2;
  int32 gender = 3;
  int32 age = 4;
  string bio = 5;
  string location = 6;
  string occupation = 7;
  string education = 8;
  int32 height = 9;
  string birthday = 10;
}
```

### 12.2 链路

```text
UserProfileGrpcService.upsertOnboarding
  -> UserProfileServiceImpl.upsertOnboarding
     -> 校验 userId、nickname、gender 0..2、age、birthday yyyy-MM-dd 且不能未来
     -> UserInfoManager.findByUserId
     -> UserInfoManager.upsertOnboarding
        -> 更新资料字段，并把 pending 改为 false
     -> 删除 profile cache
```

---

## 13. PresignAvatarUpload 头像上传签名

### 13.1 proto 定义

```proto
rpc PresignAvatarUpload(PresignAvatarUploadRequest) returns (PresignAvatarUploadResponse);

message PresignAvatarUploadRequest {
  int64 user_id = 1;
  string file_ext = 2;
  string content_type = 3;
  int64 content_length = 4;
}

message PresignAvatarUploadResponse {
  string upload_url = 1;
  string object_key = 2;
  int64 expire_seconds = 3;
}
```

### 13.2 链路

```text
UserProfileGrpcService.presignAvatarUpload
  -> UserProfileServiceImpl.presignAvatarUpload
     -> 校验 userId
     -> 校验 ext in jpg/jpeg/png/webp
     -> 校验 contentLength > 0 且 <= 10MB
     -> UserInfoManager.findByUserId
     -> objectKey = avatar/{userId}/{uuid}.{ext}
     -> S3Presigner.presignPutObject(bucket, key, contentType, contentLength, TTL=300s)
  -> 返回 upload_url / object_key / expire_seconds
```

### 13.3 数据影响

不写 DB。依赖 S3 兼容对象存储生成 PUT presigned URL。

---

## 14. ConfirmAvatarUpload 确认头像上传

### 14.1 proto 定义

```proto
rpc ConfirmAvatarUpload(ConfirmAvatarUploadRequest) returns (ConfirmAvatarUploadResponse);

message ConfirmAvatarUploadRequest {
  int64 user_id = 1;
  string object_key = 2;
}
```

### 14.2 链路

```text
UserProfileGrpcService.confirmAvatarUpload
  -> UserProfileServiceImpl.confirmAvatarUpload
     -> 校验 objectKey 前缀必须是 avatar/{userId}/，扩展名合法
     -> UserInfoManager.findByUserId
     -> S3Client.headObject(bucket, objectKey)，404 则报 avatar object not found
     -> buildAvatarJson(originalKey=minKey=midKey=objectKey, status=READY)
     -> UserInfoManager.updateCustomAvatar
     -> 删除 profile cache
  -> 返回最新 UserProfile
```

### 14.3 当前边界

缩略图未生成，`originalKey` / `minKey` / `midKey` 当前都写同一个 objectKey。

---

## 15. GetUserInterests 查询兴趣

### 15.1 proto 定义

```proto
rpc GetUserInterests(GetUserInterestsRequest) returns (GetUserInterestsResponse);

message GetUserInterestsRequest { int64 user_id = 1; }
message GetUserInterestsResponse { repeated UserInterest interests = 1; }
```

`UserInterest` 字段：`interest_code`、`display_name`、`type`、`pic_key`、`sort_order`。

### 15.2 链路

```text
UserProfileGrpcService.getUserInterests
  -> UserProfileServiceImpl.getUserInterests
     -> Redis GET {prefix}:user:interest:{userId}
     -> 未命中：ensureUserExists -> UserInterestManager.findByUserId -> cacheInterests
```

---

## 16. ReplaceUserInterests 替换兴趣

### 16.1 proto 定义

```proto
rpc ReplaceUserInterests(ReplaceUserInterestsRequest) returns (ReplaceUserInterestsResponse);

message ReplaceUserInterestsRequest {
  int64 user_id = 1;
  repeated UserInterest interests = 2;
}
```

### 16.2 链路

```text
UserProfileGrpcService.replaceUserInterests
  -> UserProfileServiceImpl.replaceUserInterests
     -> 校验 userId，用户存在
     -> 校验 interests 非空列表，最多 50 个
     -> interest_code / display_name 必填且 <= 64
     -> type 只能 TEXT(1) / PICTURE(2)
     -> picture 类型必须有 picKey，图片兴趣最多 9 个
     -> UserInterestManager.replaceByUserId
        -> 删除旧兴趣并批量插入新兴趣
     -> 删除 interest cache
     -> 查询 DB 返回最新兴趣并回填 cache
```

### 16.3 数据一致性

替换语义是全量覆盖，不是增量追加。调用方需要一次传完整兴趣列表。

---

## 17. UpdateRegulationStatus 更新监管状态

### 17.1 proto 定义

```proto
rpc UpdateRegulationStatus(UpdateRegulationStatusRequest) returns (UpdateRegulationStatusResponse);

message UpdateRegulationStatusRequest {
  int64 user_id = 1;
  int32 regulation_status = 2;
  string reason = 3;
}
```

### 17.2 链路

```text
UserProfileGrpcService.updateRegulationStatus
  -> UserProfileServiceImpl.updateRegulationStatus
     -> 校验 userId、regulationStatus 0..5、reason <= 512
     -> UserInfoManager.findByUserId，记录 beforeStatus
     -> UserInfoManager.updateRegulationStatus
     -> UserRegulationLogManager.insertLog(beforeStatus, afterStatus, reason, operatorType=0)
     -> 删除 profile cache
     -> 删除 ban status cache
```

### 17.3 DB / Redis 影响

更新 `user_info.regulation_status`，插入 `user_regulation_log`。删除：

```text
{prefix}:user:profile:{userId}
{prefix}:user:ban:status:{userId}
```

---

## 18. ListRegulationLogs 查询监管日志

### 18.1 proto 定义

```proto
rpc ListRegulationLogs(ListRegulationLogsRequest) returns (ListRegulationLogsResponse);

message ListRegulationLogsRequest {
  int64 user_id = 1;
  int32 limit = 2;
}
```

### 18.2 链路

```text
UserProfileGrpcService.listRegulationLogs
  -> UserProfileServiceImpl.listRegulationLogs
     -> 校验 userId，用户存在
     -> UserRegulationLogManager.findByUserId(userId, limit)
     -> 转 RegulationLog proto
```

只读 `user_regulation_log`，不依赖 Redis。

---

## 19. 当前功能完成状态

| 功能 | gRPC | HTTP debug | DB | Redis / S3 | 当前状态 |
| --- | --- | --- | --- | --- | --- |
| Ping | 有 | `/internal/ping` | 无 | 无 | 已完成 |
| ResolveOrCreateByPhone | 有 | gateway debug 有 | `user_info` + `user_login_phone` | 注册锁 | 已完成 |
| ResolveOrCreateByDevice | 有 | gateway debug 有 | `user_info` + `user_device_registration` | 注册锁 | 已完成 |
| ResolveOrCreateByThirdParty | 有 | gateway debug 有 | `user_info` + `user_third_party_registration` | 注册锁 | 已完成 |
| BindPhone | 有 | gateway REST 有 | `user_login_phone` | 注册锁 | 已完成 |
| BindThirdParty | 有 | gateway REST 有 | `user_third_party_registration` | 注册锁 | 已完成 |
| CheckBan | 有 | gateway debug 有 | `user_info` | 封禁短缓存 | 已完成基础版 |
| GetProfile | 有 | gateway REST 有 | `user_info` + `user_interest` | profile / interest cache | 已完成 |
| BatchGetProfile | 有 | gateway REST 有 | `user_info` + `user_interest` | profile / interest cache | 已完成，batch <= 100 |
| UpdateProfile | 有 | gateway REST 有 | `user_info` | 删 profile cache | 已完成基础版 |
| UpsertOnboarding | 有 | gateway REST 有 | `user_info` | 删 profile cache | 已完成 |
| PresignAvatarUpload | 有 | gateway REST 有 | 无 | S3 presign | 已完成 |
| ConfirmAvatarUpload | 有 | gateway REST 有 | `user_info.custom_avatar` | S3 headObject + 删 cache | 已完成 |
| GetUserInterests | 有 | gateway REST 有 | `user_interest` | interest cache | 已完成 |
| ReplaceUserInterests | 有 | gateway REST 有 | `user_interest` | 删 / 写 interest cache | 已完成 |
| UpdateRegulationStatus | 有 | 无正式 REST | `user_info` + `user_regulation_log` | 删封禁缓存 | 内部能力已完成 |
| ListRegulationLogs | 有 | 无正式 REST | `user_regulation_log` | 无 | 内部能力已完成 |

---

## 20. 技术方案中未完成 / 待补齐

| 设计项 | 当前代码状态 | 后续建议 |
| --- | --- | --- |
| 手机号格式化 `libphonenumber` | 当前只校验非空，默认 gateway 已传 E.164 | 在 gateway 或 user-service 增加规范化校验 |
| 第三方 token 校验 | user-service 不负责，gateway 当前也只是校验 token 非空 | gateway 接 Google / Apple / WeChat 真实校验 |
| 运营级封禁 Redis Set | 当前只查 `user_info.regulation_status` | 补 `user:ban:thirdparty-set` 或风控服务写入方 |
| `CheckBan` 原因和时间 | `reason` 固定 `USER_BANNED`，`banned_at_ms=0` | 按 regulation_status / 日志返回结构化原因和时间 |
| Profile 缓存结构 | 当前 profile 整体 JSON String | 如资料很大，可拆 Hash / big key |
| BatchGetProfile 上限 | 当前最大 100 | 如要对齐方案的 200，需要改校验和压测 |
| 头像缩略图 | 当前三档 key 都是原图 | 增加异步缩略图 worker 或对象存储处理链路 |
| 头像 URL 契约 | 当前 proto 同时返回 key 和 URL | 下一版可按设计收敛为 App 自拼 CDN URL |
| 兴趣图片上传 | 当前只存 picKey，没有兴趣图 presign | 复用头像 presign 思路或独立 media-service |
| 账户解绑 / 合账 | 当前只有绑定，冲突直接拒绝 | 后续补解绑、换绑、合账产品规则 |
| gRPC metadata 用户上下文 | 已有 `UserContextServerInterceptor`，业务使用还不充分 | 监管操作 operator 可从 metadata 写入 |

---

## 21. 推荐学习顺序

1. `proto/user/src/main/proto/user.proto`：先看所有 RPC 和字段。
2. `UserIdentityGrpcService`：看身份 RPC 怎么转 service 方法。
3. `UserIdentityServiceImpl`：理解注册锁、绑定幂等、封禁缓存。
4. `UserProfileGrpcService`：看 `UserInfoEntity` 如何组装成 `UserProfile`。
5. `UserProfileServiceImpl`：理解资料缓存、头像 S3、兴趣、监管日志。
6. 各 `Manager`：看每张表的读写边界。
7. `mobile-gateway` 的 `AuthController` / `UserProfileController`：看移动端 HTTP 如何调用这些 gRPC。
