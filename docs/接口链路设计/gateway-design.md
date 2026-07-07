# mobile-gateway 接口链路设计

> 目的：把 mobile-gateway 当前对移动端暴露的 REST 接口、内部 gRPC debug 接口、JWT 过滤链路、refresh token 持久化和 user-service 调用链路串起来。  
> 当前范围：`/api/v1/auth/*`、`/api/v1/user/*`、`/internal/check/*`、`/internal/debug/user-identity/*`。  
> 当前状态：gateway 已完成基础登录发 token、refresh token 轮换、账号绑定、资料 / 头像 / 兴趣代理；短信码、第三方 token、Redis 黑名单、IM UserSig、auth_device 等方案项尚未完整实现。

---

## 1. 总体入口和分层

### 1.1 HTTP 入口

| Controller | 路径前缀 | 作用 |
| --- | --- | --- |
| `AuthController` | `/api/v1/auth` | 登录、绑定、refresh、me |
| `UserProfileController` | `/api/v1/user` | 我的资料、批量资料、onboarding、头像、兴趣 |
| `PingController` | `/internal/ping` | 网关存活检查 |
| `UserGrpcCheckController` | `/internal/check/user-grpc` | user-service gRPC 连通性 |
| `PostGrpcCheckController` | `/internal/check/post-grpc` | post-service gRPC 连通性 |
| `PaymentGrpcCheckController` | `/internal/check/payment-grpc` | payment-service gRPC 连通性 |
| `MatchGrpcCheckController` | `/internal/check/match-grpc` | match-service gRPC 连通性 |
| `ImGrpcCheckController` | `/internal/check/im-grpc` | im-service gRPC 连通性 |
| `UserIdentityDebugController` | `/internal/debug/user-identity` | 身份 RPC debug |

### 1.2 核心分层

```text
HTTP Controller / Filter
  -> Service
     -> Manager / Client
        -> Mapper / gRPC Stub
           -> PostgreSQL / user-service
```

主要类：

| 层级 | 文件 | 作用 |
| --- | --- | --- |
| Filter | `JwtAuthFilter` | 验 access token，注入 `JwtUserContext` |
| Controller | `AuthController` | 鉴权相关 REST |
| Controller | `UserProfileController` | 用户资料 REST BFF |
| Service | `AuthServiceImpl` | 登录、绑定、refresh 业务编排 |
| Client | `UserIdentityClient` | 调 user-service 身份 RPC |
| Client | `UserProfileClient` | 调 user-service Profile RPC |
| Security | `JwtIssuer` / `JwtVerifier` | JWT 签发和校验 |
| Manager | `AuthRefreshTokenManager` | refresh token 表读写 |
| Mapper | `AuthRefreshTokenMapper` | `auth_refresh_token` 单表访问 |

### 1.3 鉴权过滤规则

`JwtAuthFilter.shouldNotFilter` 当前放行：

```text
/api/v1/auth/login-*
/api/v1/auth/refresh
/actuator/**
/internal/debug/**
```

其他 `/api/v1/**` 请求都需要：

```http
Authorization: Bearer {accessToken}
```

过滤链路：

```text
HTTP Request
  -> JwtAuthFilter
     -> 从 Authorization 取 Bearer token
     -> JwtVerifier.verifyAccessToken(token)
     -> JwtUserContext.setUserId(userId)
     -> Controller 读取当前 userId
  -> finally JwtUserContext.clear()
```

当前未查询 Redis 黑名单。

---

## 2. POST /api/v1/auth/login-device 快速登录

### 2.1 request / response

request DTO：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `deviceId` | `string` | 设备标识，必填 |
| `platform` | `integer` | 平台，必须 > 0，对应 user proto `Platform` |
| `appName` | `string` | 可选，默认 `hanlian` |

response `LoginResponse`：

| 字段 | 说明 |
| --- | --- |
| `userId` | 登录用户 ID |
| `pending` | 是否需要 onboarding |
| `created` | 是否本次新建用户 |
| `banned` | 当前代码固定返回 false，封禁时直接抛错 |
| `banReason` / `banMessage` | 当前成功时为空 |
| `accessToken` | access JWT |
| `refreshToken` | refresh JWT / opaque token，按 `JwtIssuer` 实现 |
| `expiresIn` | access token 过期秒数 |

### 2.2 链路

```text
AuthController.loginDevice
  -> AuthServiceImpl.loginDevice(request)
     -> 校验 deviceId 非空、platform > 0
     -> appName 默认 hanlian
     -> UserIdentityClient.resolveOrCreateByDevice(deviceId, platform, appName)
        -> gRPC dating.user.v1.UserIdentityService/ResolveOrCreateByDevice
     -> buildLoginResponse(resolveResponse, deviceId)
        -> UserIdentityClient.checkBan(userId)
        -> banned=true 则抛 USER_BANNED
        -> JwtIssuer.issue(userId)
        -> saveRefreshToken(userId, tokenPair, deviceId)
           -> TokenHashUtil.sha256(refreshToken)
           -> AuthRefreshTokenManager.saveToken
        -> 返回 LoginResponse
```

### 2.3 DB / Redis / gRPC 影响

gateway DB：写入 `auth_refresh_token`。  
user-service DB：可能创建 `user_info` 和 `user_device_registration`。  
Redis：gateway 当前不写 Redis；user-service 使用注册锁和封禁缓存。  
gRPC：调用 `ResolveOrCreateByDevice`、`CheckBan`。

---

## 3. POST /api/v1/auth/login-phone 手机登录

### 3.1 request

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `phoneE164` | `string` | 手机号，必填 |
| `smsCode` | `string` | 短信验证码，当前只校验非空 |
| `appName` | `string` | 可选，默认 `hanlian` |

### 3.2 链路

```text
AuthController.loginPhone
  -> AuthServiceImpl.loginPhone
     -> 校验 phoneE164 / smsCode 非空
     -> UserIdentityClient.resolveOrCreateByPhone(phoneE164, appName)
     -> buildLoginResponse(resolveResponse, deviceId=null)
        -> CheckBan
        -> JwtIssuer.issue
        -> 保存 refresh token hash，device_id=null
```

### 3.3 当前边界

短信验证码没有真实 Redis 校验；`smsCode` 只是必填参数。手机号格式也没有 libphonenumber 规范化。

---

## 4. POST /api/v1/auth/login-third-party 三方登录

### 4.1 request

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `platform` | `integer` | 三方平台，必须 > 0 |
| `thirdPartyUserId` | `string` | 三方用户 ID，必填 |
| `thirdPartyToken` | `string` | 三方 token，当前只校验非空 |
| `email` | `string` | 可选 |
| `appName` | `string` | 可选，默认 `hanlian` |

### 4.2 链路

```text
AuthController.loginThirdParty
  -> AuthServiceImpl.loginThirdParty
     -> 校验 platform / thirdPartyUserId / thirdPartyToken
     -> UserIdentityClient.resolveOrCreateByThirdParty(platform, thirdPartyUserId, appName, email)
     -> buildLoginResponse
```

### 4.3 当前边界

没有真实 Google / Apple / WeChat token 验签；gateway 当前信任请求里的 `thirdPartyUserId`。

---

## 5. POST /api/v1/auth/bind-phone 绑定手机号

### 5.1 鉴权

需要 access token。`JwtAuthFilter` 解析 userId 后，Controller 不接收 body 里的 userId。

### 5.2 request / response

| request 字段 | 说明 |
| --- | --- |
| `phoneE164` | 要绑定的手机号 |
| `smsCode` | 短信验证码，当前只校验非空 |
| `appName` | 可选，默认 `hanlian` |

response：`BindAccountResponse.userId`。

### 5.3 链路

```text
JwtAuthFilter -> JwtUserContext.userId
AuthController.bindPhone
  -> AuthServiceImpl.bindPhone(userId, request)
     -> 校验 userId / phoneE164 / smsCode
     -> UserIdentityClient.bindPhone(userId, phoneE164, appName)
        -> user-service BindPhone
```

---

## 6. POST /api/v1/auth/bind-third-party 绑定三方账号

### 6.1 request

| 字段 | 说明 |
| --- | --- |
| `platform` | 三方平台 |
| `thirdPartyUserId` | 三方用户 ID |
| `thirdPartyToken` | 三方 token，当前只校验非空 |
| `appName` | 可选，默认 `hanlian` |
| `email` | 可选 |

### 6.2 链路

```text
AuthController.bindThirdParty
  -> AuthServiceImpl.bindThirdParty(currentUserId, request)
     -> 校验参数
     -> UserIdentityClient.bindThirdParty(currentUserId, platform, thirdPartyUserId, appName, email)
```

当前没有真实第三方 token 校验。

---

## 7. POST /api/v1/auth/me 当前登录用户

### 7.1 链路

```text
JwtAuthFilter.verifyAccessToken
  -> JwtUserContext.setUserId
  -> AuthController.me
     -> 返回 MeResponse(userId)
```

只返回 token 中的 userId，不访问 DB / Redis / 下游 gRPC。

---

## 8. POST /api/v1/auth/refresh 刷新 token

### 8.1 request / response

request：

| 字段 | 说明 |
| --- | --- |
| `refreshToken` | refresh token，必填 |

response 同 `LoginResponse`，但 `pending=false`、`created=false`。

### 8.2 链路

```text
AuthController.refresh
  -> AuthServiceImpl.refresh(request)
     -> 校验 refreshToken 非空
     -> JwtVerifier.verifyRefreshToken(refreshToken) 得到 userId
     -> TokenHashUtil.sha256(refreshToken)
     -> AuthRefreshTokenManager.findByTokenHash
        -> 不存在或 revoked=true：TOKEN_INVALID
        -> expired_at < now：TOKEN_INVALID
     -> AuthRefreshTokenManager.revokeByTokenHash(oldHash)
     -> JwtIssuer.issue(userId) 生成新 access + refresh
     -> saveRefreshToken(userId, newTokenPair, oldToken.deviceId)
     -> 返回 LoginResponse
```

### 8.3 DB 影响

表：`auth_refresh_token`。

```text
旧 refresh token: revoked = true, revoked_at = now
新 refresh token: insert token_hash, user_id, device_id, issued_at, expired_at
```

### 8.4 幂等与安全

refresh 是轮换语义。旧 token 用过后立即撤销，再次使用会返回 token invalid。当前没有 refresh token reuse 告警和全设备踢下线。

---

## 9. GET /api/v1/user/me 查询我的资料

### 9.1 链路

```text
JwtAuthFilter -> current userId
UserProfileController.me
  -> UserProfileClient.getProfile(userId)
     -> gRPC dating.user.v1.UserProfileService/GetProfile
  -> UserProfileResponse.from(proto UserProfile)
```

gateway 不读 user-service 数据库，也不缓存资料。

---

## 10. POST /api/v1/user/profiles/batch 批量查询资料

### 10.1 request / response

request：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `userIds` | `array<long>` | 要查询的用户 ID 列表 |

response：`BatchGetProfilesResponse.profiles`。

### 10.2 链路

```text
UserProfileController.batchGetProfiles
  -> UserProfileClient.batchGetProfile(request.userIds)
     -> user-service BatchGetProfile
  -> 每个 proto UserProfile 转 UserProfileResponse
```

当前接口需要 JWT，因为不在 filter 放行列表。

---

## 11. POST /api/v1/user/onboarding 补全资料

### 11.1 request

| 字段 | 说明 |
| --- | --- |
| `nickname` | 昵称 |
| `gender` | 性别 |
| `age` | 年龄 |
| `birthday` | 生日，`yyyy-MM-dd` |
| `bio` | 简介 |
| `location` | 所在地 |
| `occupation` | 职业，映射 user-service `profession` |
| `education` | 学历 |
| `height` | 身高 cm |

### 11.2 链路

```text
UserProfileController.onboarding
  -> 当前 userId 来自 JWT
  -> UserProfileClient.upsertOnboarding(userId, ...)
     -> user-service UpsertOnboarding
  -> 返回 UserProfileResponse
```

---

## 12. PUT /api/v1/user/me 编辑我的资料

### 12.1 request

| 字段 | 说明 |
| --- | --- |
| `nickname` | 昵称 |
| `age` | 年龄 |
| `bio` | 简介 |
| `location` | 所在地 |
| `occupation` | 职业 |
| `education` | 学历 |
| `height` | 身高 cm |

### 12.2 链路

```text
UserProfileController.updateMe
  -> UserProfileClient.updateProfile(currentUserId, request fields)
     -> user-service UpdateProfile
  -> UserProfileResponse.from(profile)
```

当前接口不支持修改 gender / birthday / avatar / interests，这些走 onboarding、头像、兴趣专用接口。

---

## 13. POST /api/v1/user/avatar/presign 头像上传签名

### 13.1 request / response

request：

| 字段 | 说明 |
| --- | --- |
| `fileExt` | 扩展名，user-service 允许 jpg/jpeg/png/webp |
| `contentType` | MIME 类型 |
| `contentLength` | 文件大小，user-service 限制 <= 10MB |

response：`uploadUrl`、`objectKey`、`expireSeconds`。

### 13.2 链路

```text
UserProfileController.presignAvatarUpload
  -> current userId
  -> UserProfileClient.presignAvatarUpload(userId, fileExt, contentType, contentLength)
     -> user-service PresignAvatarUpload
  -> 返回 uploadUrl / objectKey / expireSeconds
```

gateway 不接收文件流，只转发签名请求。

---

## 14. POST /api/v1/user/avatar/confirm 确认头像上传

### 14.1 request

| 字段 | 说明 |
| --- | --- |
| `objectKey` | presign 返回的头像对象 key |

### 14.2 链路

```text
UserProfileController.confirmAvatarUpload
  -> current userId
  -> UserProfileClient.confirmAvatarUpload(userId, objectKey)
     -> user-service ConfirmAvatarUpload
        -> 校验对象存在并更新 user_info.custom_avatar
  -> 返回 UserProfileResponse
```

---

## 15. GET /api/v1/user/interests 查询兴趣

### 15.1 链路

```text
UserProfileController.getInterests
  -> current userId
  -> UserProfileClient.getUserInterests(userId)
     -> user-service GetUserInterests
  -> UserInterestsResponse
```

---

## 16. PUT /api/v1/user/interests 替换兴趣

### 16.1 request

`ReplaceUserInterestsRequest.interests` 是数组，每个元素：

| 字段 | 说明 |
| --- | --- |
| `interestCode` | 兴趣编码 |
| `displayName` | 展示名 |
| `type` | `1=TEXT`，`2=PICTURE` |
| `picKey` | 图片兴趣对象 key |
| `sortOrder` | 排序 |

### 16.2 链路

```text
UserProfileController.replaceInterests
  -> current userId
  -> UserProfileClient.replaceUserInterests(userId, request.interests)
     -> user-service ReplaceUserInterests
  -> UserInterestsResponse
```

这是全量替换语义，不是增量添加。

---

## 17. 内部 gRPC 检查接口

### 17.1 check 接口

| 路径 | 下游 | 说明 |
| --- | --- | --- |
| `/internal/check/user-grpc` | user-service | 调 user `Ping` 或身份 stub 检查 |
| `/internal/check/post-grpc` | post-service | 调 post `Ping` |
| `/internal/check/payment-grpc` | payment-service | 调 payment `Ping` |
| `/internal/check/match-grpc` | match-service | 调 match `Ping` |
| `/internal/check/im-grpc` | im-service | 调 im `Ping` |

这些接口用于本地排查服务发现 / gRPC 端口 / proto 依赖是否正常，不是移动端业务接口。

### 17.2 user identity debug

| 路径 | 对应 user-service RPC |
| --- | --- |
| `/internal/debug/user-identity/phone` | `ResolveOrCreateByPhone` |
| `/internal/debug/user-identity/device` | `ResolveOrCreateByDevice` |
| `/internal/debug/user-identity/third-party` | `ResolveOrCreateByThirdParty` |
| `/internal/debug/user-identity/check-ban` | `CheckBan` |

`/internal/debug/**` 当前不走 JWT filter，注意只用于本地或内网排查。

---

## 18. 当前功能完成状态

| 功能 | HTTP | 下游 gRPC | gateway DB | 当前状态 |
| --- | --- | --- | --- | --- |
| login-device | 有 | `ResolveOrCreateByDevice` + `CheckBan` | 写 refresh token | 已完成基础版 |
| login-phone | 有 | `ResolveOrCreateByPhone` + `CheckBan` | 写 refresh token | 已完成基础版，短信未真校验 |
| login-third-party | 有 | `ResolveOrCreateByThirdParty` + `CheckBan` | 写 refresh token | 已完成基础版，三方 token 未真校验 |
| bind-phone | 有 | `BindPhone` | 无 | 已完成基础版，短信未真校验 |
| bind-third-party | 有 | `BindThirdParty` | 无 | 已完成基础版，三方 token 未真校验 |
| auth/me | 有 | 无 | 无 | 已完成 |
| refresh | 有 | 无 | 撤销旧 token + 写新 token | 已完成轮换 |
| user/me | 有 | `GetProfile` | 无 | 已完成 |
| profiles/batch | 有 | `BatchGetProfile` | 无 | 已完成 |
| onboarding | 有 | `UpsertOnboarding` | 无 | 已完成 |
| update profile | 有 | `UpdateProfile` | 无 | 已完成 |
| avatar presign | 有 | `PresignAvatarUpload` | 无 | 已完成 |
| avatar confirm | 有 | `ConfirmAvatarUpload` | 无 | 已完成 |
| interests get/replace | 有 | `GetUserInterests` / `ReplaceUserInterests` | 无 | 已完成 |
| internal check | 有 | 多服务 Ping | 无 | 已完成排查入口 |

---

## 19. 技术方案中未完成 / 待补齐

| 设计项 | 当前代码状态 | 后续建议 |
| --- | --- | --- |
| 短信验证码下发 / 校验 | `smsCode` 只校验非空 | 增加发送接口、Redis code、过期和次数限制 |
| 第三方 token 验证 | `thirdPartyToken` 只校验非空 | 接 Google / Apple / WeChat 服务端验签 |
| JWT 黑名单 | `JwtAuthFilter` 未查 Redis 黑名单 | 增加登出 / 强制登出和黑名单 TTL |
| Logout 接口 | 当前没有 | 增加撤销 refresh + access jti 黑名单 |
| auth_device 表 | 当前只有 `auth_refresh_token` migration | 增加设备表并在登录时 upsert |
| refresh token 复用检测 | 旧 token revoked 后只返回 invalid | 增加 reuse 告警、撤销该用户全部 token |
| deviceId 匹配校验 | refresh 没校验请求设备 | refresh request 增加 deviceId 或从请求头拿 deviceId |
| IM UserSig | `LoginResponse` 里相关字段当前为空 | 登录成功后调 im-service 获取 UserSig |
| BFF 聚合首页 | 当前只代理 user profile | 接 post / match / im 等聚合接口 |
| 下游超时 / 熔断 | client 当前直接 blocking stub 调用 | 增加 deadline、Resilience4j、降级策略 |
| gRPC metadata 透传 | 有 `UserGrpcClientMetadataInterceptor`，覆盖范围需继续核查 | 所有下游 client 统一透传 userId / traceId / deviceId |
| OpenAPI / 参数校验注解 | Controller 当前主要手写 service 校验 | 补 `@Valid`、错误码、OpenAPI 描述 |
| CORS / RateLimit | 方案有，当前需按 filter/config 核查完整度 | 移动端联调前补齐白名单和限流 |

---

## 20. 推荐学习顺序

1. `JwtAuthFilter`：先理解哪些接口需要 token。
2. `AuthController` + `AuthServiceImpl`：理解登录、绑定、refresh 主链路。
3. `UserIdentityClient`：看 gateway 如何调用 user-service 身份 RPC。
4. `UserProfileController` + `UserProfileClient`：看移动端资料接口如何转 gRPC。
5. `AuthRefreshTokenManager` / `AuthRefreshTokenMapper`：理解 refresh token 轮换落库。
6. 各 `*GrpcCheckController`：用于排查服务发现和 gRPC 连通性。
