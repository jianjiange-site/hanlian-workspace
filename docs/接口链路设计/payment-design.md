# payment-service 接口链路设计

> 目的：把 payment-service 当前所有 gRPC 功能，从 proto request / response 参数，到 HTTP debug 入口、gRPC 入口、service 业务逻辑、manager / mapper / DB 影响，按功能完整串起来。  
> 当前范围：`Ping`、`GetCoins`、`GrantCoins`、`ConsumeCoins`、`GetSubscription`、`CreateOrder`、`PreparePayment`、`MockPaySuccess`、`GetOrder`、`CloseOrder`、`ListUserOrders`、`ListProducts`、`ListCallbackRecords`。  
> 当前状态：这是 P0 / P0+ 本地可验收链路，不代表真实第三方支付、真实回调验签、退款和对账已经完成。

---

## 1. 总体入口和分层

### 1.1 proto 文件

```text
proto/payment/src/main/proto/payment.proto
proto/payment/payment.proto
```

这两个文件当前内容保持一致。修改 proto 后必须执行：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\payment
mvn clean install -DskipTests
```

否则 `dating-server/payment-service` 里可能识别不到新生成的 request / response Java 类。

### 1.2 gRPC 服务入口

```text
dating-server/payment-service/src/main/java/com/aurora/dating/payment/grpc/PaymentGrpcService.java
```

所有 proto RPC 最终都会进入这个类。它的职责是：

```text
1. 接收 proto request
2. 从 request 里取参数
3. 调用具体 service 方法
4. 把 service result 组装成 proto response
5. responseObserver.onNext(response)
6. responseObserver.onCompleted()
```

### 1.3 HTTP debug 入口

```text
dating-server/payment-service/src/main/java/com/aurora/dating/payment/PaymentCoinDebugController.java
```

HTTP debug 接口用于本地浏览器 / curl / Postman 验收。HTTP 入口和 gRPC 入口最终调用的是同一套 service 逻辑。

### 1.4 核心业务分层

```text
Controller / Grpc
  -> Service
     -> Manager
        -> Mapper
           -> PostgreSQL
```

当前主要类：

| 层级         | 文件                              | 作用                 |
| ---------- | ------------------------------- | ------------------ |
| gRPC       | `PaymentGrpcService`            | 实现 proto RPC       |
| HTTP debug | `PaymentCoinDebugController`    | 提供本地 debug HTTP 接口 |
| Service    | `PaymentCoinService`            | 金币查询、加金币、扣金币       |
| Service    | `PaymentSubscriptionService`    | 查询订阅、设置订阅          |
| Service    | `PaymentOrderService`           | 创建订单、模拟支付成功        |
| Manager    | `PaymentCoinManager`            | 组织金币账户 / 流水 mapper |
| Manager    | `PaymentSubscriptionManager`    | 组织订阅 mapper        |
| Manager    | `PaymentOrderManager`           | 组织订单 mapper        |
| Mapper     | `PaymentCoinAccountMapper`      | 操作金币账户表            |
| Mapper     | `PaymentCoinLedgerMapper`       | 操作金币流水表            |
| Mapper     | `PaymentUserSubscriptionMapper` | 操作订阅表              |
| Mapper     | `PaymentOrderMapper`            | 操作订单表              |

---

## 2. Ping 连通性检查

### 2.1 proto 定义

```proto
rpc Ping (PingRequest) returns (PingResponse);

message PingRequest {
  string message = 1;
}

message PingResponse {
  string message = 1;
}
```

### 2.2 request 参数

| 字段        | 类型       | 说明         |
| --------- | -------- | ---------- |
| `message` | `string` | 客户端传入的测试文本 |

### 2.3 response 参数

| 字段        | 类型       | 说明                                      |
| --------- | -------- | --------------------------------------- |
| `message` | `string` | 服务端返回 `payment-service pong: {message}` |

### 2.4 gRPC 接口

```text
dating.payment.v1.PaymentService/Ping
```

grpcurl 示例：

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/Ping
```

### 2.5 HTTP debug 接口

Ping 的 HTTP debug 不走同一个 proto request / response，而是 Spring Boot 普通 HTTP：

```http
GET /internal/ping
```

### 2.6 服务逻辑全流程

```text
客户端 gRPC Ping
  -> PaymentGrpcService.ping
     -> 从 request.getMessage() 取 message
     -> 拼接 "payment-service pong: " + message
     -> 构建 PingResponse
     -> responseObserver.onNext(response)
     -> responseObserver.onCompleted()
```

### 2.7 数据库 / Redis 影响

```text
无数据库写入。
无 Redis 读写。
只用于确认 gRPC 服务可访问。
```

---

## 3. GetCoins 查询金币余额

### 3.1 proto 定义

```proto
rpc GetCoins (GetCoinsRequest) returns (GetCoinsResponse);

message GetCoinsRequest {
  int64 user_id = 1;
}

message GetCoinsResponse {
  int32 code = 1;
  string message = 2;
  int64 user_id = 3;
  int64 balance = 4;
}
```

### 3.2 request 参数

| 字段        | 类型      | 说明             |
| --------- | ------- | -------------- |
| `user_id` | `int64` | 业务用户 ID，必须大于 0 |

### 3.3 response 参数

| 字段        | 类型       | 说明                 |
| --------- | -------- | ------------------ |
| `code`    | `int32`  | `0` 成功，`4000` 参数错误 |
| `message` | `string` | 成功为 `ok`，失败为错误原因   |
| `user_id` | `int64`  | 用户 ID              |
| `balance` | `int64`  | 当前金币余额             |

### 3.4 gRPC 接口

```text
dating.payment.v1.PaymentService/GetCoins
```

grpcurl 示例：

```powershell
'{"userId":10001}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GetCoins
```

### 3.5 HTTP debug 接口

```http
GET /internal/debug/payment/coins?userId={userId}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/coins?userId=10001"
```

### 3.6 服务逻辑全流程

```text
客户端请求 GetCoins
  -> PaymentGrpcService.getCoins
     -> request.getUserId()
     -> PaymentCoinService.getCoins(userId)
        -> validateUserId(userId)
        -> PaymentCoinManager.createAccountIfAbsent(userId)
           -> PaymentCoinAccountMapper.selectById(userId)
           -> 如果账户不存在，insert 一条 balance = 0 的账户
           -> 返回 PaymentCoinAccountEntity
     -> 组装 GetCoinsResponse
     -> 返回 code / message / userId / balance
```

HTTP debug 链路：

```text
GET /internal/debug/payment/coins
  -> PaymentCoinDebugController.getCoins
     -> PaymentCoinService.getCoins
     -> 返回 Map JSON
```

### 3.7 数据库 / Redis 影响

数据库：

```text
读取 payment_coin_accounts。
如果用户第一次进入金币系统，会自动创建一条余额为 0 的账户记录。
```

Redis：

```text
当前链路不依赖 Redis。
```

### 3.8 异常逻辑

| 场景              | 返回            |
| --------------- | ------------- |
| `user_id <= 0`  | `code = 4000` |
| `user_id` 为空或非法 | `code = 4000` |

---

## 4. GrantCoins 加金币

### 4.1 proto 定义

```proto
rpc GrantCoins (GrantCoinsRequest) returns (GrantCoinsResponse);

message GrantCoinsRequest {
  int64 user_id = 1;
  int64 amount = 2;
  string reason = 3;
  string idempotency_key = 4;
}

message GrantCoinsResponse {
  int32 code = 1;
  string message = 2;
  int64 user_id = 3;
  int64 balance = 4;
  string ledger_no = 5;
}
```

### 4.2 request 参数

| 字段                | 类型       | 说明                                      |
| ----------------- | -------- | --------------------------------------- |
| `user_id`         | `int64`  | 要加金币的业务用户 ID，必须大于 0                     |
| `amount`          | `int64`  | 增加金币数量，必须大于 0                           |
| `reason`          | `string` | 加金币原因，例如 `TEST_GRANT` / `PAYMENT_ORDER` |
| `idempotency_key` | `string` | 幂等键，同一笔业务请求必须使用同一个 key                  |

### 4.3 response 参数

| 字段          | 类型       | 说明                 |
| ----------- | -------- | ------------------ |
| `code`      | `int32`  | `0` 成功，`4000` 参数错误 |
| `message`   | `string` | 成功为 `ok`           |
| `user_id`   | `int64`  | 用户 ID              |
| `balance`   | `int64`  | 加金币后的余额            |
| `ledger_no` | `string` | 金币流水号              |

### 4.4 gRPC 接口

```text
dating.payment.v1.PaymentService/GrantCoins
```

grpcurl 示例：

```powershell
'{"userId":10001,"amount":100,"reason":"TEST_GRANT","idempotencyKey":"grant-10001-001"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GrantCoins
```

### 4.5 HTTP debug 接口

```http
GET /internal/debug/payment/grant?userId={userId}&amount={amount}&reason={reason}&idempotencyKey={idempotencyKey}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/grant?userId=10001&amount=100&reason=TEST_GRANT&idempotencyKey=grant-10001-001"
```

### 4.6 服务逻辑全流程

```text
客户端请求 GrantCoins
  -> PaymentGrpcService.grantCoins
     -> request.getUserId()
     -> request.getAmount()
     -> request.getReason()
     -> request.getIdempotencyKey()
     -> PaymentCoinService.grantCoins(userId, amount, reason, idempotencyKey)
        -> validateConsumeRequest
           -> 校验 userId > 0
           -> 校验 amount > 0
           -> 校验 reason 非空
           -> 校验 idempotencyKey 非空
        -> PaymentCoinManager.createAccountIfAbsent(userId)
        -> PaymentCoinManager.findLedgerByIdempotencyKey(idempotencyKey)
           -> 如果流水已存在，直接返回原流水结果，不重复加金币
        -> PaymentCoinManager.grantCoins(userId, amount)
           -> payment_coin_accounts.balance 增加 amount
           -> payment_coin_accounts.total_recharge 增加 amount
        -> PaymentCoinManager.findAccount(userId)
        -> 生成 ledgerNo
        -> PaymentCoinManager.createLedger(ledger)
           -> 写入 payment_coin_ledger
        -> 返回 GrantCoinsResult
     -> 组装 GrantCoinsResponse
```

### 4.7 数据库 / Redis 影响

数据库：

```text
payment_coin_accounts:
  balance 增加 amount
  total_recharge 增加 amount

payment_coin_ledger:
  新增一条 direction = 1 的入账流水
  change_amount = 正数 amount
  reason = request.reason
  idempotency_key = request.idempotency_key
```

Redis：

```text
当前链路不依赖 Redis。
```

### 4.8 幂等逻辑

核心字段：

```text
idempotency_key
```

同一个 `idempotency_key` 重复请求：

```text
不会重复增加余额。
会返回第一次生成的流水结果。
```

---

## 5. ConsumeCoins 扣金币

### 5.1 proto 定义

```proto
rpc ConsumeCoins (ConsumeCoinsRequest) returns (ConsumeCoinsResponse);

message ConsumeCoinsRequest {
  int64 user_id = 1;
  int64 amount = 2;
  string reason = 3;
  string idempotency_key = 4;
}

message ConsumeCoinsResponse {
  int32 code = 1;
  string message = 2;
  int64 user_id = 3;
  int64 balance = 4;
  string ledger_no = 5;
}
```

### 5.2 request 参数

| 字段                | 类型       | 说明                                   |
| ----------------- | -------- | ------------------------------------ |
| `user_id`         | `int64`  | 要扣金币的业务用户 ID，必须大于 0                  |
| `amount`          | `int64`  | 扣金币数量，必须大于 0                         |
| `reason`          | `string` | 扣金币原因，例如 `CHAT_MESSAGE` / `SUPER_HI` |
| `idempotency_key` | `string` | 幂等键，同一笔扣费业务必须使用同一个 key               |

### 5.3 response 参数

| 字段          | 类型       | 说明                                  |
| ----------- | -------- | ----------------------------------- |
| `code`      | `int32`  | `0` 成功，`3001` 余额不足，`4000` 参数错误      |
| `message`   | `string` | 成功为 `ok`，余额不足为 `insufficient coins` |
| `user_id`   | `int64`  | 用户 ID                               |
| `balance`   | `int64`  | 扣费后的余额，或余额不足时的当前余额                  |
| `ledger_no` | `string` | 扣费流水号，余额不足时为空字符串                    |

### 5.4 gRPC 接口

```text
dating.payment.v1.PaymentService/ConsumeCoins
```

grpcurl 示例：

```powershell
'{"userId":10001,"amount":10,"reason":"CHAT_MESSAGE","idempotencyKey":"im-msg-001"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/ConsumeCoins
```

### 5.5 HTTP debug 接口

```http
GET /internal/debug/payment/consume?userId={userId}&amount={amount}&reason={reason}&idempotencyKey={idempotencyKey}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/consume?userId=10001&amount=10&reason=CHAT_MESSAGE&idempotencyKey=im-msg-001"
```

### 5.6 服务逻辑全流程

```text
客户端请求 ConsumeCoins
  -> PaymentGrpcService.consumeCoins
     -> request.getUserId()
     -> request.getAmount()
     -> request.getReason()
     -> request.getIdempotencyKey()
     -> PaymentCoinService.consumeCoins(userId, amount, reason, idempotencyKey)
        -> validateConsumeRequest
        -> PaymentCoinManager.createAccountIfAbsent(userId)
        -> PaymentCoinManager.findLedgerByIdempotencyKey(idempotencyKey)
           -> 如果已存在流水，直接返回原流水结果，不重复扣金币
        -> PaymentCoinManager.consumeCoins(userId, amount)
           -> SQL 条件扣减：balance >= amount
           -> 如果 updated = 0，表示余额不足
        -> PaymentCoinManager.findAccount(userId)
        -> 生成 ledgerNo
        -> PaymentCoinManager.createLedger(ledger)
           -> 写入 payment_coin_ledger
        -> 返回 ConsumeCoinsResult
     -> 组装 ConsumeCoinsResponse
```

### 5.7 数据库 / Redis 影响

数据库：

```text
payment_coin_accounts:
  balance 减少 amount
  total_consume 增加 amount

payment_coin_ledger:
  新增一条 direction = 2 的扣费流水
  change_amount = 负数 -amount
  reason = request.reason
  idempotency_key = request.idempotency_key
```

余额不足时：

```text
不扣余额。
不写扣费流水。
返回 code = 3001。
```

Redis：

```text
当前链路不依赖 Redis。
```

### 5.8 并发安全点

扣金币不是先查余额再扣，而是 SQL 条件更新：

```text
balance >= amount
```

这样可以避免并发场景下把余额扣成负数。

---

## 6. GetSubscription 查询订阅权益

### 6.1 proto 定义

```proto
rpc GetSubscription (GetSubscriptionRequest) returns (GetSubscriptionResponse);

message GetSubscriptionRequest {
  int64 user_id = 1;
}

message GetSubscriptionResponse {
  int32 code = 1;
  string message = 2;
  int64 user_id = 3;
  string tier = 4;
  bool active = 5;
  int64 expire_at_ms = 6;
  int32 daily_right_swipe_limit = 7;
  int32 daily_card_limit = 8;
  int32 daily_super_hi_limit = 9;
}
```

### 6.2 request 参数

| 字段        | 类型      | 说明              |
| --------- | ------- | --------------- |
| `user_id` | `int64` | 要查询订阅权益的业务用户 ID |

### 6.3 response 参数

| 字段                        | 类型       | 说明                                          |
| ------------------------- | -------- | ------------------------------------------- |
| `code`                    | `int32`  | `0` 成功，`4000` 参数错误                          |
| `message`                 | `string` | 成功为 `ok`                                    |
| `user_id`                 | `int64`  | 用户 ID                                       |
| `tier`                    | `string` | 档位：`FREE` / `WEEKLY` / `MONTHLY` / `YEARLY` |
| `active`                  | `bool`   | 当前订阅是否有效                                    |
| `expire_at_ms`            | `int64`  | 订阅过期时间毫秒时间戳，免费用户为 `0`                       |
| `daily_right_swipe_limit` | `int32`  | 每日右划次数                                      |
| `daily_card_limit`        | `int32`  | 每日可划卡片数                                     |
| `daily_super_hi_limit`    | `int32`  | 每日免费 Super Hi 次数                            |

### 6.4 gRPC 接口

```text
dating.payment.v1.PaymentService/GetSubscription
```

grpcurl 示例：

```powershell
'{"userId":10001}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GetSubscription
```

### 6.5 HTTP debug 接口

查询订阅：

```http
GET /internal/debug/payment/subscription?userId={userId}
```

设置订阅，当前仅用于 debug：

```http
GET /internal/debug/payment/subscription/set?userId={userId}&tier={tier}&expireDays={expireDays}
```

### 6.6 服务逻辑全流程

```text
客户端请求 GetSubscription
  -> PaymentGrpcService.getSubscription
     -> request.getUserId()
     -> PaymentSubscriptionService.getSubscription(userId)
        -> validateUserId(userId)
        -> PaymentSubscriptionManager.findByUserId(userId)
           -> PaymentUserSubscriptionMapper.selectById(userId)
        -> 如果没有订阅记录，返回 FREE
        -> 如果 status != 1，返回 FREE
        -> 如果 expireAt 为空或已过期，返回 FREE
        -> 根据 tier 计算权益
           -> WEEKLY: rightSwipe=40, card=80, superHi=0
           -> MONTHLY: rightSwipe=80, card=120, superHi=1
           -> YEARLY: rightSwipe=80, card=120, superHi=1
     -> 组装 GetSubscriptionResponse
```

### 6.7 数据库 / Redis 影响

数据库：

```text
读取 payment_user_subscriptions。
```

Redis：

```text
当前链路不依赖 Redis。
后续 match-service 如需高频读取，可以独立做短缓存。
```

### 6.8 FREE 兜底逻辑

以下情况都会返回免费权益：

```text
没有订阅记录
status != 1
expire_at 为空
expire_at 已过期
未知 tier
```

---

## 7. CreateOrder 创建订单

### 7.1 proto 定义

```proto
rpc CreateOrder (CreateOrderRequest) returns (CreateOrderResponse);

message CreateOrderRequest {
  int64 user_id = 1;
  string product_code = 2;
  string provider = 3;
}

message CreateOrderResponse {
  int32 code = 1;
  string message = 2;
  string order_no = 3;
  int64 user_id = 4;
  string product_type = 5;
  string product_code = 6;
  string provider = 7;
  int64 amount_cents = 8;
  string currency = 9;
  int64 coin_amount = 10;
  string subscription_tier = 11;
  int32 status = 12;
}
```

### 7.2 request 参数

| 字段             | 类型       | 说明                                 |
| -------------- | -------- | ---------------------------------- |
| `user_id`      | `int64`  | 下单用户 ID，必须大于 0                     |
| `product_code` | `string` | 商品编码，例如 `COIN_100` / `SUB_MONTHLY` |
| `provider`     | `string` | 支付渠道，例如 `PAYPAL` / `APPLE`         |

### 7.3 response 参数

| 字段                  | 类型       | 说明                      |
| ------------------- | -------- | ----------------------- |
| `code`              | `int32`  | `0` 成功，`4000` 参数错误      |
| `message`           | `string` | 成功为 `ok`                |
| `order_no`          | `string` | 本地订单号，格式 `pay_...`      |
| `user_id`           | `int64`  | 用户 ID                   |
| `product_type`      | `string` | `COIN` / `SUBSCRIPTION` |
| `product_code`      | `string` | 商品编码                    |
| `provider`          | `string` | 支付渠道，会转成大写              |
| `amount_cents`      | `int64`  | 订单金额，单位为分               |
| `currency`          | `string` | 币种，当前为 `USD`            |
| `coin_amount`       | `int64`  | 金币订单对应金币数，订阅订单为 `0`     |
| `subscription_tier` | `string` | 订阅档位，金币订单为空字符串          |
| `status`            | `int32`  | 订单状态，创建后为 `10`          |

### 7.4 支持的商品码

| 商品码           | 商品类型           | 金额分    | 金币数   | 订阅档位      |
| ------------- | -------------- | ------ | ----- | --------- |
| `COIN_100`    | `COIN`         | `99`   | `100` | 空         |
| `COIN_500`    | `COIN`         | `399`  | `500` | 空         |
| `SUB_WEEKLY`  | `SUBSCRIPTION` | `699`  | `0`   | `WEEKLY`  |
| `SUB_MONTHLY` | `SUBSCRIPTION` | `1999` | `0`   | `MONTHLY` |
| `SUB_YEARLY`  | `SUBSCRIPTION` | `9999` | `0`   | `YEARLY`  |

### 7.5 gRPC 接口

```text
dating.payment.v1.PaymentService/CreateOrder
```

grpcurl 示例：

```powershell
'{"userId":10001,"productCode":"COIN_100","provider":"PAYPAL"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/CreateOrder
```

### 7.6 HTTP debug 接口

```http
GET /internal/debug/payment/order/create?userId={userId}&productCode={productCode}&provider={provider}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/create?userId=10001&productCode=COIN_100&provider=PAYPAL"
```

### 7.7 服务逻辑全流程

```text
客户端请求 CreateOrder
  -> PaymentGrpcService.createOrder
     -> request.getUserId()
     -> request.getProductCode()
     -> request.getProvider()
     -> PaymentOrderService.createOrder(userId, productCode, provider)
        -> validateUserId(userId)
        -> validateProvider(provider)
        -> resolveProduct(productCode)
           -> 根据商品码得到 productType / amountCents / coinAmount / subscriptionTier
        -> generateOrderNo()
           -> 生成 pay_ 开头的订单号
        -> new PaymentOrderEntity
           -> status = 10
           -> providerTradeNo = null
           -> paidAt = null
           -> closedAt = null
        -> PaymentOrderManager.createOrder(order)
           -> PaymentOrderMapper.insert(order)
        -> 返回 CreateOrderResult
     -> 组装 CreateOrderResponse
```

### 7.8 数据库 / Redis 影响

数据库：

```text
payment_orders 新增一条订单：
  status = 10
  order_no = pay_...
  product_type = COIN / SUBSCRIPTION
  provider_trade_no = null
  paid_at = null
```

Redis：

```text
当前链路不依赖 Redis。
```

### 7.9 关键边界

```text
CreateOrder 只负责创建本地订单。
不会拉起真实支付。
不会发金币。
不会开订阅。
不会把订单标记为已支付。
```

---

## 8. PreparePayment 创建支付拉起参数

### 8.1 proto 定义

```proto
rpc PreparePayment (PreparePaymentRequest) returns (PreparePaymentResponse);

message PreparePaymentRequest {
  string order_no = 1;
  string return_url = 2;
  string cancel_url = 3;
}

message PreparePaymentResponse {
  int32 code = 1;
  string message = 2;
  string order_no = 3;
  int64 user_id = 4;
  string product_type = 5;
  string product_code = 6;
  string provider = 7;
  int64 amount_cents = 8;
  string currency = 9;
  int32 status = 10;
  string pay_url = 11;
  string provider_payload = 12;
  int64 expire_at_ms = 13;
}
```

### 8.2 request 参数

| 字段           | 类型       | 说明                                          |
| ------------ | -------- | ------------------------------------------- |
| `order_no`   | `string` | 本地订单号，来自 `CreateOrderResponse.order_no`     |
| `return_url` | `string` | 支付成功后前端希望跳回的地址，当前 mock 写入 providerPayload   |
| `cancel_url` | `string` | 用户取消支付后前端希望跳回的地址，当前 mock 写入 providerPayload |

### 8.3 response 参数

| 字段                 | 类型       | 说明                              |
| ------------------ | -------- | ------------------------------- |
| `code`             | `int32`  | `0` 成功，`4000` 参数或订单状态错误         |
| `message`          | `string` | 成功为 `ok`，失败为错误原因                |
| `order_no`         | `string` | 本地订单号                           |
| `user_id`          | `int64`  | 用户 ID                           |
| `product_type`     | `string` | `COIN` / `SUBSCRIPTION`         |
| `product_code`     | `string` | 商品编码                            |
| `provider`         | `string` | 支付渠道                            |
| `amount_cents`     | `int64`  | 订单金额，单位为分                       |
| `currency`         | `string` | 币种，当前为 `USD`                    |
| `status`           | `int32`  | 当前订单状态，只有 `10 = CREATED` 允许拉起支付 |
| `pay_url`          | `string` | 支付跳转地址；当前为 mock 地址              |
| `provider_payload` | `string` | 渠道拉起参数；当前为 mock JSON 字符串        |
| `expire_at_ms`     | `int64`  | 本次支付拉起参数过期时间毫秒时间戳               |

### 8.4 gRPC 接口

```text
dating.payment.v1.PaymentService/PreparePayment
```

grpcurl 示例：

```powershell
'{"orderNo":"pay_xxx","returnUrl":"https://app.hanlian.local/pay/success","cancelUrl":"https://app.hanlian.local/pay/cancel"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/PreparePayment
```

### 8.5 HTTP debug 接口

```http
GET /internal/debug/payment/order/prepare?orderNo={orderNo}&returnUrl={returnUrl}&cancelUrl={cancelUrl}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/prepare?orderNo=pay_xxx&returnUrl=https://app.hanlian.local/pay/success&cancelUrl=https://app.hanlian.local/pay/cancel"
```

### 8.6 服务逻辑全流程

```text
客户端请求 PreparePayment
  -> PaymentGrpcService.preparePayment
     -> request.getOrderNo()
     -> request.getReturnUrl()
     -> request.getCancelUrl()
     -> PaymentOrderService.preparePayment(orderNo, returnUrl, cancelUrl)
        -> validateOrderNo(orderNo)
        -> PaymentOrderManager.findByOrderNo(orderNo)
        -> 订单不存在，返回 4000
        -> 订单状态不是 CREATED，返回 4000
        -> 生成 mock payUrl / providerPayload / expireAtMs
     -> 组装 PreparePaymentResponse
```

HTTP debug 链路：

```text
GET /internal/debug/payment/order/prepare
  -> PaymentCoinDebugController.preparePayment
     -> PaymentOrderService.preparePayment
     -> 返回 Map JSON
```

### 8.7 数据库 / Redis 影响

```text
只读取 payment_orders。
不修改订单状态。
不发金币。
不开订阅。
当前链路不依赖 Redis。
```

### 8.8 当前边界

```text
PreparePayment 当前返回 mock 支付拉起参数。
还没有真实 PayPal / Apple SDK 或 HTTP API 调用。
后续接真实支付时，可以保留 request / response 结构，把 service 内部 mock 生成替换为真实 provider client。
```

---

## 9. MockPaySuccess 模拟支付成功

### 8.1 proto 定义

```proto
rpc MockPaySuccess (MockPaySuccessRequest) returns (MockPaySuccessResponse);

message MockPaySuccessRequest {
  string order_no = 1;
  string provider_trade_no = 2;
  int64 paid_amount_cents = 3;
  string currency = 4;
}

message MockPaySuccessResponse {
  int32 code = 1;
  string message = 2;
  string order_no = 3;
  int64 user_id = 4;
  string product_type = 5;
  string product_code = 6;
  string provider_trade_no = 7;
  int32 status = 8;
  int64 balance = 9;
  string ledger_no = 10;
  string tier = 11;
  bool active = 12;
  int64 expire_at_ms = 13;
}
```

### 8.2 request 参数

| 字段                  | 类型       | 说明                                      |
| ------------------- | -------- | --------------------------------------- |
| `order_no`          | `string` | 本地订单号，来自 `CreateOrderResponse.order_no` |
| `provider_trade_no` | `string` | 第三方支付流水号，当前 mock 时手动传                   |
| `paid_amount_cents` | `int64`  | 实际支付金额，单位为分，必须等于订单 `amount_cents`       |
| `currency`          | `string` | 实际支付币种，必须等于订单 `currency`，当前为 `USD`      |

### 8.3 response 参数

| 字段                  | 类型       | 说明                           |
| ------------------- | -------- | ---------------------------- |
| `code`              | `int32`  | `0` 成功，`4000` 参数或订单状态错误      |
| `message`           | `string` | `ok` / `already paid` / 错误原因 |
| `order_no`          | `string` | 本地订单号                        |
| `user_id`           | `int64`  | 用户 ID                        |
| `product_type`      | `string` | `COIN` / `SUBSCRIPTION`      |
| `product_code`      | `string` | 商品编码                         |
| `provider_trade_no` | `string` | 支付渠道流水号                      |
| `status`            | `int32`  | 成功后为 `20`                    |
| `balance`           | `int64`  | 金币订单返回最新余额，订阅订单为 `0`         |
| `ledger_no`         | `string` | 金币订单返回金币流水号，订阅订单为空           |
| `tier`              | `string` | 订阅订单返回订阅档位，金币订单为空            |
| `active`            | `bool`   | 订阅是否生效                       |
| `expire_at_ms`      | `int64`  | 订阅过期时间毫秒时间戳，金币订单为 `0`        |

### 8.4 gRPC 接口

```text
dating.payment.v1.PaymentService/MockPaySuccess
```

grpcurl 示例：

```powershell
'{"orderNo":"pay_xxx","providerTradeNo":"paypal-test-001","paidAmountCents":99,"currency":"USD"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/MockPaySuccess
```

### 8.5 HTTP debug 接口

```http
GET /internal/debug/payment/order/mock-pay-success?orderNo={orderNo}&providerTradeNo={providerTradeNo}&paidAmountCents={paidAmountCents}&currency={currency}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/mock-pay-success?orderNo=pay_xxx&providerTradeNo=paypal-test-001&paidAmountCents=99&currency=USD"
```

### 8.6 服务逻辑全流程

```text
客户端请求 MockPaySuccess
  -> PaymentGrpcService.mockPaySuccess
     -> request.getOrderNo()
     -> request.getProviderTradeNo()
     -> request.getPaidAmountCents()
     -> request.getCurrency()
     -> PaymentOrderService.mockPaySuccess(orderNo, providerTradeNo, paidAmountCents, currency)
        -> validateOrderNo(orderNo)
        -> validateProviderTradeNo(providerTradeNo)
        -> validatePaidAmountCents(paidAmountCents)
        -> validateCurrency(currency)
        -> PaymentCallbackRecordManager.createProcessingRecord(...)
           -> 先保存 provider / orderNo / providerTradeNo / paidAmountCents / currency / rawPayload
        -> PaymentOrderManager.findByOrderNo(orderNo)
           -> PaymentOrderMapper.selectOne
        -> 如果订单不存在，返回 4000
        -> 校验 paidAmountCents 必须等于 order.amountCents
        -> 校验 currency 必须等于 order.currency
        -> 校验 providerTradeNo 没有被其他订单使用
        -> 如果订单已经 PAID，返回 already paid，不重复发金币 / 开订阅
        -> 如果订单不是 CREATED，返回 4000
        -> PaymentOrderManager.markPaidIfCreated(orderNo, providerTradeNo, paidAt)
           -> UPDATE payment_orders
              SET status = 20,
                  provider_trade_no = providerTradeNo,
                  paid_at = paidAt
              WHERE order_no = orderNo
                AND status = 10
        -> 根据 productType 分支处理
        -> PaymentCallbackRecordManager.markSuccess(callbackRecordId)
```

### 8.7 金币订单分支

当订单：

```text
product_type = COIN
```

执行：

```text
PaymentOrderService.finishCoinOrder(order)
  -> 校验 coinAmount > 0
  -> PaymentCoinService.grantCoins(
       userId = order.userId,
       amount = order.coinAmount,
       reason = PAYMENT_ORDER,
       idempotencyKey = payment-order:{orderNo}
     )
  -> payment_coin_accounts 增加余额
  -> payment_coin_ledger 写入入账流水
  -> 返回 PaySuccessResult
```

金币订单成功后影响：

```text
payment_orders.status = 20
payment_orders.provider_trade_no = 请求传入值
payment_orders.paid_at 不为空
payment_coin_accounts.balance 增加 coinAmount
payment_coin_ledger 新增 PAYMENT_ORDER 入账流水
```

### 8.8 订阅订单分支

当订单：

```text
product_type = SUBSCRIPTION
```

执行：

```text
PaymentOrderService.finishSubscriptionOrder(order)
  -> 校验 subscriptionTier 非空
  -> subscriptionDays(subscriptionTier)
     -> WEEKLY = 7
     -> MONTHLY = 30
     -> YEARLY = 365
  -> PaymentSubscriptionService.setSubscription(userId, tier, expireDays)
     -> PaymentSubscriptionManager.upsertSubscription
     -> PaymentUserSubscriptionMapper.upsertSubscription
  -> 返回 PaySuccessResult
```

订阅订单成功后影响：

```text
payment_orders.status = 20
payment_orders.provider_trade_no = 请求传入值
payment_orders.paid_at 不为空
payment_user_subscriptions.tier = 订单 subscriptionTier
payment_user_subscriptions.status = 1
payment_user_subscriptions.expire_at = 当前时间 + 订阅天数
```

### 8.9 幂等和重复调用

第一层幂等：订单状态。

```text
只有 status = 10 的订单能被 UPDATE 成 status = 20。
已经 status = 20 的订单会返回 already paid。
```

第二层幂等：第三方支付流水号。

```text
provider_trade_no 表示支付渠道侧的交易流水。
同一个 provider_trade_no 只能归属同一个本地订单。
如果一个 provider_trade_no 已经绑定到订单 A，再拿来支付订单 B，会返回：
provider_trade_no already used by another order
```

第三层幂等：金币流水。

```text
金币订单发金币时 idempotencyKey = payment-order:{orderNo}
即使重复进入 grantCoins，也不会重复发金币。
```

### 8.10 并发和一致性设计

核心并发点：同一个订单可能被重复回调，或者两个请求同时尝试把订单改成已支付。

当前处理方式：

```text
1. 先查本地订单，校验订单存在。
2. 校验支付金额和币种必须匹配订单，防止少付、错币种也开通权益。
3. 校验 provider_trade_no 没有绑定到其他订单，防止同一第三方流水支付多笔本地订单。
4. 用 SQL 条件更新支付状态：WHERE order_no = ? AND status = 10。
5. 如果并发下只有一个请求 UPDATE 成功，另一个请求 updated = 0 后重新查订单状态。
6. 已支付订单重复请求返回 already paid，不重复发金币、不重复开订阅。
7. 金币发放再用 coin ledger 的 idempotency_key = payment-order:{orderNo} 兜底，避免重复发金币。
8. 回调原文先落库，再处理业务；即使后续处理失败，也能保留排查和对账证据。
```

这套设计的目标：

```text
订单状态不被重复推进。
第三方流水不被多订单复用。
金币不会重复发放。
订阅不会因为重复回调反复开通。
```

数据库层最终防线已经通过 Flyway migration 补上：

```sql
CREATE UNIQUE INDEX ux_payment_orders_provider_trade_no
ON payment_orders(provider_trade_no)
WHERE provider_trade_no IS NOT NULL;
```

对应 migration：

```text
dating-server/payment-service/src/main/resources/db/migration/V20260706_02__add_payment_order_provider_trade_no_unique_index.sql
```

原因：service 层查询可以拦截大多数重复请求，但数据库唯一索引才是并发下最终防线。

### 8.11 回调原文记录设计

支付回调和普通业务请求不一样：它来自第三方渠道，后续经常需要排查“渠道到底发了什么、我们为什么没有发金币、是否重复回调”。所以支付链路要保存回调原文。

当前新增表：

```text
payment_callback_records
```

对应 migration：

```text
dating-server/payment-service/src/main/resources/db/migration/V20260706_03__create_payment_callback_records.sql
```

核心字段：

| 字段                  | 说明                                                  |
| ------------------- | --------------------------------------------------- |
| `provider`          | 支付渠道，例如 `PAYPAL` / `APPLE` / `MOCK`                 |
| `event_type`        | 回调事件类型，当前 mock 可用 `MOCK_PAY_SUCCESS`                |
| `order_no`          | 本地订单号                                               |
| `provider_trade_no` | 第三方支付流水号                                            |
| `paid_amount_cents` | 回调声明的支付金额                                           |
| `currency`          | 回调声明的币种                                             |
| `raw_payload`       | 原始回调内容；mock 场景可以用 JSON 字符串拼出等价原文                    |
| `process_status`    | 处理状态：`10 = PROCESSING`，`20 = SUCCESS`，`30 = FAILED` |
| `error_message`     | 失败原因，成功时为空                                          |

设计方法：

```text
1. 收到回调后，先用独立事务写 payment_callback_records，状态为 PROCESSING。
2. 再校验订单、金额、币种、provider_trade_no 幂等。
3. 业务处理成功后，用独立事务把记录状态改为 SUCCESS。
4. 如果业务处理失败，用独立事务把错误原因写入 error_message，并把状态改为 FAILED，然后继续抛出原业务异常。
5. 后续对账时，可以拿 provider_trade_no / order_no / created_at 反查所有回调记录。
```

事务边界：

```text
payment_callback_records 的 create / markSuccess / markFailed 都使用 REQUIRES_NEW。
原因是支付回调属于外部事实，即使订单处理失败或主事务回滚，也必须保留“第三方发过什么、系统处理到哪一步、为什么失败”的证据。
markSuccess 必须放在订单状态推进、金币发放或订阅开通都成功之后。
markFailed 必须放在 catch 里，并且写完 FAILED 后继续抛出原异常，避免上层误判为支付成功。
```

并发和一致性价值：

```text
即使多个重复回调同时进来，每次回调原文都会有记录。
订单状态推进仍然由 status = 10 条件更新和 provider_trade_no 唯一索引兜底。
回调记录不负责决定是否发金币，只负责保留证据和处理结果。
```

### 8.12 当前边界

```text
MockPaySuccess 是本地模拟支付成功。
没有真实第三方验签。
已经校验 mock 请求里的支付金额和币种是否等于本地订单。
已经设计回调原文记录表，业务层接入后可保存 mock / 真实回调原文。
没有退款 / 对账。
```

后续真实支付回调可以复用 `PaymentOrderService.mockPaySuccess` 后半段业务逻辑，但前面必须补：

```text
渠道验签
异常告警
```

---

## 9. HTTP debug-only：SetSubscription

这个功能当前不是 proto RPC，但在 HTTP debug 里存在，用于本地手动设置订阅。

### 9.1 HTTP 接口

```http
GET /internal/debug/payment/subscription/set?userId={userId}&tier={tier}&expireDays={expireDays}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/subscription/set?userId=10001&tier=MONTHLY&expireDays=30"
```

### 9.2 参数

| 字段           | 类型        | 说明                                       |
| ------------ | --------- | ---------------------------------------- |
| `userId`     | `Long`    | 用户 ID                                    |
| `tier`       | `String`  | `FREE` / `WEEKLY` / `MONTHLY` / `YEARLY` |
| `expireDays` | `Integer` | 有效天数，非 FREE 时必须大于 0                      |

### 9.3 服务逻辑全流程

```text
HTTP /internal/debug/payment/subscription/set
  -> PaymentCoinDebugController.setSubscription
     -> PaymentSubscriptionService.setSubscription(userId, tier, expireDays)
        -> validateUserId
        -> validateTier
        -> 如果 tier = FREE
           -> upsertSubscription(userId, FREE, null, 0)
           -> 返回 FREE 权益
        -> 如果 tier != FREE
           -> 校验 expireDays > 0
           -> expireAt = now + expireDays
           -> upsertSubscription(userId, tier, expireAt, 1)
           -> 返回对应订阅权益
```

### 9.4 数据库影响

```text
payment_user_subscriptions:
  按 user_id upsert
  更新 tier / expire_at / status / updated_at
```

---

## 10. GetOrder 查询订单详情

### 10.1 proto 定义

```proto
rpc GetOrder (GetOrderRequest) returns (GetOrderResponse);

message GetOrderRequest {
  string order_no = 1;
}

message GetOrderResponse {
  int32 code = 1;
  string message = 2;
  string order_no = 3;
  int64 user_id = 4;
  string product_type = 5;
  string product_code = 6;
  string provider = 7;
  int64 amount_cents = 8;
  string currency = 9;
  int64 coin_amount = 10;
  string subscription_tier = 11;
  int32 status = 12;
  string provider_trade_no = 13;
  int64 paid_at_ms = 14;
  int64 created_at_ms = 15;
  int64 updated_at_ms = 16;
}
```

### 10.2 request 参数

| 字段         | 类型       | 说明                                      |
| ---------- | -------- | --------------------------------------- |
| `order_no` | `string` | 本地订单号，来自 `CreateOrderResponse.order_no` |

### 10.3 response 参数

| 字段                  | 类型       | 说明                                       |
| ------------------- | -------- | ---------------------------------------- |
| `code`              | `int32`  | `0` 成功，`4000` 参数错误或订单不存在                 |
| `message`           | `string` | 成功为 `ok`                                 |
| `order_no`          | `string` | 本地订单号                                    |
| `user_id`           | `int64`  | 用户 ID                                    |
| `product_type`      | `string` | `COIN` / `SUBSCRIPTION`                  |
| `product_code`      | `string` | 商品编码                                     |
| `provider`          | `string` | 支付渠道                                     |
| `amount_cents`      | `int64`  | 订单金额，单位为分                                |
| `currency`          | `string` | 币种，当前为 `USD`                             |
| `coin_amount`       | `int64`  | 金币订单对应金币数，订阅订单为 `0`                      |
| `subscription_tier` | `string` | 订阅档位，金币订单为空字符串                           |
| `status`            | `int32`  | `10 = CREATED`，`20 = PAID`，`30 = CLOSED` |
| `provider_trade_no` | `string` | 第三方支付流水号，未支付时为空字符串                       |
| `paid_at_ms`        | `int64`  | 支付成功时间，未支付时为 `0`                         |
| `created_at_ms`     | `int64`  | 订单创建时间                                   |
| `updated_at_ms`     | `int64`  | 订单更新时间                                   |

### 10.4 gRPC 接口

```text
dating.payment.v1.PaymentService/GetOrder
```

grpcurl 示例：

```powershell
'{"orderNo":"pay_xxx"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GetOrder
```

### 10.5 HTTP debug 接口

```http
GET /internal/debug/payment/order/get?orderNo={orderNo}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/get?orderNo=pay_xxx"
```

### 10.6 服务逻辑全流程

```text
客户端请求 GetOrder
  -> PaymentGrpcService.getOrder
     -> request.getOrderNo()
     -> PaymentOrderService.getOrder(orderNo)
        -> validateOrderNo(orderNo)
        -> PaymentOrderManager.findByOrderNo(orderNo)
           -> PaymentOrderMapper.selectOne
        -> 如果订单不存在，返回 4000
        -> 将 paidAt / createdAt / updatedAt 转成毫秒时间戳
        -> 返回 GetOrderResult
     -> 组装 GetOrderResponse
```

### 10.7 数据库 / Redis 影响

```text
只读取 payment_orders。
不修改订单。
当前链路不依赖 Redis。
```

---

## 11. CloseOrder 关闭未支付订单

### 11.1 proto 定义

```proto
rpc CloseOrder (CloseOrderRequest) returns (CloseOrderResponse);

message CloseOrderRequest {
  string order_no = 1;
}

message CloseOrderResponse {
  int32 code = 1;
  string message = 2;
  string order_no = 3;
  int64 user_id = 4;
  string product_type = 5;
  string product_code = 6;
  int32 status = 7;
  int64 closed_at_ms = 8;
}
```

### 11.2 request 参数

| 字段         | 类型       | 说明                                      |
| ---------- | -------- | --------------------------------------- |
| `order_no` | `string` | 本地订单号，来自 `CreateOrderResponse.order_no` |

### 11.3 response 参数

| 字段             | 类型       | 说明                             |
| -------------- | -------- | ------------------------------ |
| `code`         | `int32`  | `0` 成功，`4000` 参数错误或订单状态错误      |
| `message`      | `string` | `ok` / `already closed` / 错误原因 |
| `order_no`     | `string` | 本地订单号                          |
| `user_id`      | `int64`  | 用户 ID                          |
| `product_type` | `string` | `COIN` / `SUBSCRIPTION`        |
| `product_code` | `string` | 商品编码                           |
| `status`       | `int32`  | 成功关闭后为 `30`                    |
| `closed_at_ms` | `int64`  | 关闭时间毫秒时间戳                      |

### 11.4 gRPC 接口

```text
dating.payment.v1.PaymentService/CloseOrder
```

grpcurl 示例：

```powershell
'{"orderNo":"pay_xxx"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/CloseOrder
```

### 11.5 HTTP debug 接口

```http
GET /internal/debug/payment/order/close?orderNo={orderNo}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/close?orderNo=pay_xxx"
```

### 11.6 服务逻辑全流程

```text
客户端请求 CloseOrder
  -> PaymentGrpcService.closeOrder
     -> request.getOrderNo()
     -> PaymentOrderService.closeOrder(orderNo)
        -> validateOrderNo(orderNo)
        -> PaymentOrderManager.findByOrderNo(orderNo)
        -> 如果订单已经 CLOSED，返回 already closed
        -> 如果订单已经 PAID，返回 4000，不能关闭已支付订单
        -> PaymentOrderManager.markClosedIfCreated(orderNo, closedAt)
           -> 只允许 status = 10 的订单更新为 status = 30
        -> 返回 CloseOrderResult
     -> 组装 CloseOrderResponse
```

### 11.7 数据库 / Redis 影响

```text
payment_orders:
  CREATED(10) -> CLOSED(30)
  closed_at 写入当前时间
  updated_at 写入当前时间

当前链路不依赖 Redis。
```

---

## 12. ListUserOrders 查询用户订单列表

### 12.1 proto 定义

```proto
rpc ListUserOrders (ListUserOrdersRequest) returns (ListUserOrdersResponse);

message ListUserOrdersRequest {
  int64 user_id = 1;
  int32 limit = 2;
}

message ListUserOrdersResponse {
  int32 code = 1;
  string message = 2;
  int64 user_id = 3;
  repeated OrderSummary orders = 4;
}
```

### 12.2 request 参数

| 字段        | 类型      | 说明                        |
| --------- | ------- | ------------------------- |
| `user_id` | `int64` | 要查询订单列表的业务用户 ID，必须大于 0    |
| `limit`   | `int32` | 返回条数；小于等于 0 时默认 20，最大 100 |

### 12.3 response 参数

| 字段        | 类型                      | 说明                 |
| --------- | ----------------------- | ------------------ |
| `code`    | `int32`                 | `0` 成功，`4000` 参数错误 |
| `message` | `string`                | 成功为 `ok`           |
| `user_id` | `int64`                 | 用户 ID              |
| `orders`  | `repeated OrderSummary` | 订单摘要列表，按创建时间倒序     |

### 12.4 gRPC 接口

```text
dating.payment.v1.PaymentService/ListUserOrders
```

grpcurl 示例：

```powershell
'{"userId":10001,"limit":20}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/ListUserOrders
```

### 12.5 HTTP debug 接口

```http
GET /internal/debug/payment/order/list?userId={userId}&limit={limit}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/list?userId=10001&limit=20"
```

### 12.6 服务逻辑全流程

```text
客户端请求 ListUserOrders
  -> PaymentGrpcService.listUserOrders
     -> request.getUserId()
     -> request.getLimit()
     -> PaymentOrderService.listUserOrders(userId, limit)
        -> validateUserId(userId)
        -> normalizeLimit(limit)
        -> PaymentOrderManager.listByUserId(userId, normalizedLimit)
           -> PaymentOrderMapper.selectList
        -> 将 PaymentOrderEntity 转成 OrderSummaryResult
        -> 返回 ListUserOrdersResult
     -> 组装 ListUserOrdersResponse
```

### 12.7 数据库 / Redis 影响

```text
只读取 payment_orders。
按 user_id 查询，并按 created_at 倒序返回。
当前链路不依赖 Redis。
```

---

## 13. ListProducts 查询支付商品列表

### 13.1 proto 定义

```proto
rpc ListProducts (ListProductsRequest) returns (ListProductsResponse);

message ListProductsRequest {
}

message ProductItem {
  string product_code = 1;
  string product_type = 2;
  string title = 3;
  int64 amount_cents = 4;
  string currency = 5;
  int64 coin_amount = 6;
  string subscription_tier = 7;
  int32 subscription_days = 8;
}

message ListProductsResponse {
  int32 code = 1;
  string message = 2;
  repeated ProductItem products = 3;
}
```

### 13.2 request 参数

```text
当前无参数。
```

### 13.3 response 参数

| 字段         | 类型                     | 说明        |
| ---------- | ---------------------- | --------- |
| `code`     | `int32`                | `0` 成功    |
| `message`  | `string`               | 成功为 `ok`  |
| `products` | `repeated ProductItem` | 当前可购买商品列表 |

`ProductItem` 字段：

| 字段                  | 类型       | 说明                      |
| ------------------- | -------- | ----------------------- |
| `product_code`      | `string` | 商品编码，例如 `COIN_100`      |
| `product_type`      | `string` | `COIN` / `SUBSCRIPTION` |
| `title`             | `string` | 给前端展示的商品标题              |
| `amount_cents`      | `int64`  | 金额，单位为分                 |
| `currency`          | `string` | 币种，当前为 `USD`            |
| `coin_amount`       | `int64`  | 金币商品对应金币数，订阅商品为 `0`     |
| `subscription_tier` | `string` | 订阅档位，金币商品为空字符串          |
| `subscription_days` | `int32`  | 订阅天数，金币商品为 `0`          |

### 13.4 gRPC 接口

```text
dating.payment.v1.PaymentService/ListProducts
```

grpcurl 示例：

```powershell
'{}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/ListProducts
```

### 13.5 HTTP debug 接口

```http
GET /internal/debug/payment/products
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/products"
```

### 13.6 服务逻辑全流程

```text
客户端请求 ListProducts
  -> PaymentGrpcService.listProducts
     -> PaymentOrderService.listProducts()
        -> 返回当前写死在 service 里的商品配置
        -> 包含金币包和订阅商品
     -> 组装 ListProductsResponse
```

HTTP debug 链路：

```text
GET /internal/debug/payment/products
  -> PaymentCoinDebugController.listProducts
     -> PaymentOrderService.listProducts
     -> 返回 Map JSON
```

### 13.7 数据库 / Redis 影响

```text
当前不读数据库。
当前不写数据库。
当前链路不依赖 Redis。
```

### 13.8 当前边界

```text
当前商品配置先写死在 PaymentOrderService。
后续如果要运营后台动态调整价格、币种、上下架状态，可以迁移到 payment_products 表或 Nacos 配置。
```

---

## 14. ListCallbackRecords 查询支付回调记录

这个功能当前是 HTTP debug-only 排查接口，不是正式 proto RPC。原因是回调原文、失败原因属于内部支付排查数据，不应该直接暴露成客户端业务接口。

### 14.1 proto 定义

```text
无 proto RPC。
```

### 14.2 HTTP debug 接口

```http
GET /internal/debug/payment/callback-records?orderNo={orderNo}&providerTradeNo={providerTradeNo}&limit={limit}
```

curl 示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/callback-records?orderNo=pay_xxx&limit=20"
curl "http://localhost:18085/internal/debug/payment/callback-records?providerTradeNo=paypal-test-001&limit=20"
```

### 14.3 request 参数

| 字段                | 类型       | 说明                              |
| ----------------- | -------- | ------------------------------- |
| `orderNo`         | `string` | 本地订单号，和 `providerTradeNo` 至少传一个 |
| `providerTradeNo` | `string` | 第三方支付流水号，和 `orderNo` 至少传一个      |
| `limit`           | `int`    | 返回条数；小于等于 0 时默认 20，最大 100       |

### 14.4 response 参数

| 字段        | 类型       | 说明                       |
| --------- | -------- | ------------------------ |
| `code`    | `int`    | `0` 成功，`4000` 参数错误       |
| `message` | `string` | 成功为 `ok`，失败为错误原因         |
| `records` | `array`  | 回调记录列表，按 `created_at` 倒序 |

`records` 每一项：

| 字段                | 说明                                             |
| ----------------- | ---------------------------------------------- |
| `id`              | 回调记录 ID                                        |
| `provider`        | 支付渠道，例如 `MOCK`                                 |
| `eventType`       | 回调事件类型，例如 `MOCK_PAY_SUCCESS`                   |
| `orderNo`         | 本地订单号                                          |
| `providerTradeNo` | 第三方支付流水号                                       |
| `paidAmountCents` | 回调声明的支付金额                                      |
| `currency`        | 回调声明的币种                                        |
| `rawPayload`      | 回调原文                                           |
| `processStatus`   | `10 = PROCESSING`，`20 = SUCCESS`，`30 = FAILED` |
| `errorMessage`    | 失败原因，成功时为空                                     |
| `createdAtMs`     | 回调记录创建时间毫秒时间戳                                  |

### 14.5 服务逻辑全流程

```text
HTTP /internal/debug/payment/callback-records
  -> PaymentCoinDebugController.listCallbackRecords
     -> PaymentOrderService.listCallbackRecords(orderNo, providerTradeNo, limit)
        -> validateCallbackQuery
           -> orderNo / providerTradeNo 至少传一个
        -> normalizeLimit
           -> 默认 20，最大 100
        -> PaymentCallbackRecordManager.listForDebug(orderNo, providerTradeNo, limit)
           -> PaymentCallbackRecordMapper.selectList
           -> 按 order_no / provider_trade_no 条件查询
           -> 按 created_at 倒序返回
        -> 转成 CallbackRecordResult
     -> 返回 records JSON
```

### 14.6 数据库 / Redis 影响

```text
只读取 payment_callback_records。
不修改订单、不发金币、不改订阅。
当前链路不依赖 Redis。
```

### 14.7 排查价值

```text
1. 看第三方回调是否进入 payment-service。
2. 看回调金额、币种、provider_trade_no 是否和订单一致。
3. 看处理状态是 PROCESSING / SUCCESS / FAILED。
4. FAILED 时直接看 errorMessage，定位是金额不匹配、币种不匹配、订单状态错误，还是 provider_trade_no 被复用。
5. 重复回调时可以按 orderNo 或 providerTradeNo 查看多条记录，辅助判断第三方重试和本地幂等是否正常。
```

---

## 15. 当前功能完成状态

| 功能                  | gRPC | HTTP debug    | DB 写入       | 当前状态          |
| ------------------- | ---- | ------------- | ----------- | ------------- |
| Ping                | 有    | 有普通 HTTP ping | 无           | 已完成           |
| GetCoins            | 有    | 有             | 可能创建账户      | 已完成           |
| GrantCoins          | 有    | 有             | 账户 + 流水     | 已完成           |
| ConsumeCoins        | 有    | 有             | 账户 + 流水     | 已完成           |
| GetSubscription     | 有    | 有             | 无           | 已完成           |
| CreateOrder         | 有    | 有             | 订单表         | 已完成订单骨架       |
| PreparePayment      | 有    | 有             | 无，只读订单表     | 已完成 mock 拉起参数 |
| MockPaySuccess      | 有    | 有             | 订单表 + 金币或订阅 | 已完成模拟链路       |
| GetOrder            | 有    | 有             | 无，只读订单表     | 已完成           |
| CloseOrder          | 有    | 有             | 订单表         | 已完成           |
| ListUserOrders      | 有    | 有             | 无，只读订单表     | 已完成           |
| ListProducts        | 有    | 有             | 无           | 已完成           |
| ListCallbackRecords | 无    | 有             | 无，只读回调记录表   | debug 已完成     |
| SetSubscription     | 无    | 有             | 订阅表         | debug 已完成     |

---

## 16. 推荐学习顺序

建议按下面顺序读代码：

1. `payment.proto`：先看 request / response 长什么样。
2. `PaymentGrpcService`：看 proto 参数如何进入 Java。
3. `PaymentCoinDebugController`：看 HTTP debug 如何复用 service。
4. `PaymentCoinService`：理解金币余额、流水、幂等。
5. `PaymentSubscriptionService`：理解订阅权益计算。
6. `PaymentOrderService.createOrder`：理解订单骨架。
7. `PaymentOrderService.preparePayment`：理解支付拉起参数如何生成。
8. `PaymentOrderService.mockPaySuccess`：理解支付成功后的业务落地。
9. `PaymentOrderService.getOrder`：理解订单状态查询。
10. `PaymentOrderService.closeOrder`：理解未支付订单关闭。
11. `PaymentOrderService.listUserOrders`：理解用户订单列表。
12. `PaymentOrderService.listProducts`：理解商品配置如何提供给外部。
13. `PaymentOrderService.listCallbackRecords`：理解支付回调排查链路。
14. 各 `Manager`：理解 service 到 mapper 的中间层。
15. 各 `Mapper`：理解最终 SQL / MyBatis-Plus 如何操作 DB。

---

## 17. 一条完整支付链路例子

以购买 `COIN_100` 为例：

```text
1. 客户端调用 CreateOrder
   request:
     user_id = 10001
     product_code = COIN_100
     provider = PAYPAL

2. payment-service 创建订单
   payment_orders:
     order_no = pay_xxx
     product_type = COIN
     coin_amount = 100
     status = 10

3. 客户端调用 PreparePayment
   response:
     pay_url = mock 支付跳转地址
     provider_payload = 渠道拉起参数
     expire_at_ms = 拉起参数过期时间

4. 客户端或测试人员调用 MockPaySuccess
   request:
     order_no = pay_xxx
     provider_trade_no = paypal-test-001

5. payment-service 标记订单支付成功
   payment_orders:
     status = 20
     provider_trade_no = paypal-test-001
     paid_at = now

6. payment-service 发金币
   payment_coin_accounts:
     balance += 100

   payment_coin_ledger:
     reason = PAYMENT_ORDER
     idempotency_key = payment-order:pay_xxx

7. response 返回
   status = 20
   balance = 最新余额
   ledger_no = 金币流水号

8. 客户端调用 GetOrder 或 ListUserOrders
   查看订单状态、支付时间和订单列表
```

订阅订单类似，只是第 5 步不是发金币，而是写入 / 更新 `payment_user_subscriptions`。
