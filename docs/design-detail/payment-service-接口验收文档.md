# payment-service 接口验收文档

> 用途：本文件用于你自己做 HTTP / gRPC / DB 验收，同时帮助复习 payment-service 每个接口背后的代码链路和设计逻辑。  
> 范围：HTTP debug 接口、gRPC 方法、核心代码链路、数据库 / Redis 验收点、异常测试点。  
> 本地 HTTP 地址：`http://localhost:18085`  
> 本地 gRPC 地址：`localhost:19085`

---

## 1. 当前完成进度

### P0 已完成

- `proto/payment/src/main/proto/payment.proto` 已定义 payment P0 RPC。
- `dating-server/payment-service` 已注册 gRPC server，端口默认 `19085`。
- `Ping` 连通性接口可用。
- `GetCoins` 查询金币余额可用。
- `GrantCoins` 内部入账可用，支持幂等键。
- `ConsumeCoins` 扣金币可用，支持幂等键和余额不足返回。
- `GetSubscription` 查询订阅档位和 Match 权益可用。
- `CreateOrder` 创建支付订单骨架可用，当前只创建本地订单，不拉起真实三方支付。
- `MockPaySuccess` 模拟支付成功回调可用，金币订单发金币，订阅订单开订阅。
- `GetOrder` 按订单号查询订单详情可用，可查看订单待支付 / 已支付状态。
- Flyway migration 已增加金币账户表和金币流水表。
- Flyway migration 已增加订阅表和订单表。
- HTTP debug 接口已覆盖加金币、查余额、扣金币、查订阅、创建订单、模拟支付成功、查询订单。

### 后续 P1 / P2 待做

- 真实支付拉起参数。
- 三方支付接入，例如 Apple / PayPal / 微信 / 支付宝。
- 支付回调验签。
- 真实支付回调接入。
- 退款和对账。
- 风控、限流、告警、运营后台。
- 正式 Postman / grpcurl collection。

---

## 2. 验收前准备

### 2.1 安装 payment-proto

修改 proto 后必须先安装本地 proto 模块：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\payment
mvn clean install -DskipTests
```

预期：

```text
BUILD SUCCESS
```

如果 IDEA 里 `GrantCoinsRequest` / `GrantCoinsResponse` 爆红，通常是 proto 没重新 install 或 Maven 没刷新。

### 2.2 编译 payment-service

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server
mvn -pl payment-service -am -DskipTests package
```

预期：

```text
BUILD SUCCESS
```

### 2.3 服务准备

1. 启动 `payment-service`。
2. 确认配置连接的是：
   - HTTP 端口：`18085`
   - gRPC 端口：`19085`
   - PostgreSQL：以 Nacos / 环境配置为准
   - Redis：以 Nacos / 环境配置为准

不要把数据库、Redis、Nacos 密码写进本地文档或提交到 Git。

---

## 3. 涉及文件

```text
proto/payment/src/main/proto/payment.proto
proto/payment/payment.proto
dating-server/payment-service/pom.xml
dating-server/payment-service/src/main/resources/application.yml
dating-server/payment-service/src/main/resources/db/migration/V20260704_01__create_payment_coin_tables.sql
dating-server/payment-service/src/main/resources/db/migration/V20260705_01__create_payment_subscription_tables.sql
dating-server/payment-service/src/main/resources/db/migration/V20260706_01__create_payment_order_tables.sql
dating-server/payment-service/src/main/java/com/aurora/dating/payment/PaymentCoinDebugController.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/grpc/PaymentGrpcService.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/PaymentCoinService.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/PaymentSubscriptionService.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/PaymentOrderService.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/SubscriptionResult.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/CreateOrderResult.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/PaySuccessResult.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/GetOrderResult.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/GrantCoinsResult.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/service/ConsumeCoinsResult.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/manager/PaymentCoinManager.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/manager/PaymentSubscriptionManager.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/manager/PaymentOrderManager.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/mapper/PaymentCoinAccountMapper.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/mapper/PaymentCoinLedgerMapper.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/mapper/PaymentUserSubscriptionMapper.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/mapper/PaymentOrderMapper.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/entity/PaymentCoinAccountEntity.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/entity/PaymentCoinLedgerEntity.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/entity/PaymentUserSubscriptionEntity.java
dating-server/payment-service/src/main/java/com/aurora/dating/payment/entity/PaymentOrderEntity.java
```

---

## 4. 总体代码分层

当前 payment-service 的主要调用方向：

```text
HTTP:
PaymentCoinDebugController
  -> PaymentCoinService
     -> PaymentCoinManager
        -> PaymentCoinAccountMapper / PaymentCoinLedgerMapper
           -> PostgreSQL

gRPC:
PaymentGrpcService
  -> PaymentCoinService
     -> PaymentCoinManager
        -> PaymentCoinAccountMapper / PaymentCoinLedgerMapper
           -> PostgreSQL

PaymentGrpcService
  -> PaymentSubscriptionService
     -> PaymentSubscriptionManager
        -> PaymentUserSubscriptionMapper
           -> PostgreSQL

PaymentCoinDebugController.createOrder
PaymentGrpcService.createOrder
  -> PaymentOrderService.createOrder
     -> PaymentOrderManager.createOrder
        -> PaymentOrderMapper.insert
           -> PostgreSQL

PaymentCoinDebugController.mockPaySuccess
PaymentGrpcService.mockPaySuccess
  -> PaymentOrderService.mockPaySuccess
     -> PaymentOrderManager.markPaidIfCreated
        -> PaymentOrderMapper.markPaidIfCreated
           -> PostgreSQL
     -> PaymentCoinService.grantCoins / PaymentSubscriptionService.setSubscription

PaymentCoinDebugController.getOrder
PaymentGrpcService.getOrder
  -> PaymentOrderService.getOrder
     -> PaymentOrderManager.findByOrderNo
        -> PaymentOrderMapper.selectOne
           -> PostgreSQL
```

关键原则：

- `controller` / `grpc` 只负责接请求、组装响应。
- `service` 负责业务逻辑、参数校验、幂等判断和事务边界。
- `manager` 负责组织 mapper。
- `mapper` 负责具体 SQL / MyBatis-Plus 调用。
- 金币账户和流水最终落 PostgreSQL。
- 当前金币主链路不依赖 Redis。
- 当前订单骨架只写入 `payment_orders`，不调用真实支付渠道。
- 当前模拟支付成功会更新订单状态，并复用金币入账 / 订阅开通逻辑。
- 当前订单查询只读 `payment_orders`，用于确认订单状态和支付时间。

---

## 5. HTTP debug 接口

### 5.1 Ping

```http
GET http://localhost:18085/internal/ping
```

作用：确认 Spring Boot HTTP 服务已启动。

预期：

```json
{"service":"payment-service","status":"ok"}
```

### 5.2 DB

```http
GET http://localhost:18085/internal/check/db
```

作用：确认 payment-service 能连 PostgreSQL。

预期：

```json
{"database":"ok","result":"1"}
```

### 5.3 Redis

```http
GET http://localhost:18085/internal/check/redis
```

作用：确认 payment-service 能读写 Redis。

预期：

```json
{"redis":"ok","key":"hanlian:payment-service:check:redis","value":"ok","ttlSeconds":"..."}
```

注意：当前金币余额 / 入账 / 扣费主链路不依赖 Redis，Redis 当前只做连通性检查。

---

## 6. 订阅查询 GetSubscription

### 6.1 HTTP 接口

```http
GET /internal/debug/payment/subscription?userId={userId}
```

示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/subscription?userId=10001"
```

免费用户响应：

```json
{
  "code": 0,
  "message": "ok",
  "userId": 10001,
  "tier": "FREE",
  "active": false,
  "expireAtMs": 0,
  "dailyRightSwipeLimit": 20,
  "dailyCardLimit": 50,
  "dailySuperHiLimit": 0
}
```

月度订阅响应：

```json
{
  "code": 0,
  "message": "ok",
  "userId": 10001,
  "tier": "MONTHLY",
  "active": true,
  "expireAtMs": 1780000000000,
  "dailyRightSwipeLimit": 80,
  "dailyCardLimit": 120,
  "dailySuperHiLimit": 1
}
```

### 6.2 gRPC 方法

```text
GetSubscription(GetSubscriptionRequest) returns (GetSubscriptionResponse)
```

请求字段：

| 字段        | 说明              |
| --------- | --------------- |
| `user_id` | 要查询订阅权益的业务用户 ID |

响应字段：

| 字段                        | 说明                                          |
| ------------------------- | ------------------------------------------- |
| `code`                    | `0` 表示成功，`4000` 表示参数错误                      |
| `message`                 | 返回说明                                        |
| `user_id`                 | 用户 ID                                       |
| `tier`                    | 档位：`FREE` / `WEEKLY` / `MONTHLY` / `YEARLY` |
| `active`                  | 当前订阅是否有效                                    |
| `expire_at_ms`            | 订阅过期时间，毫秒时间戳；免费用户为 `0`                      |
| `daily_right_swipe_limit` | 每日右划次数                                      |
| `daily_card_limit`        | 每日可划卡片数                                     |
| `daily_super_hi_limit`    | 每日免费 Super Hi 次数                            |

### 6.3 完整链路

```text
PaymentCoinDebugController.getSubscription
PaymentGrpcService.getSubscription
  -> PaymentSubscriptionService.getSubscription
     -> PaymentSubscriptionManager.findByUserId
        -> PaymentUserSubscriptionMapper.selectById
```

### 6.4 数据库验收

查询订阅表：

```sql
SELECT user_id, tier, expire_at, status
FROM payment_user_subscriptions
WHERE user_id = 10001;
```

手动插入一条测试订阅：

```sql
INSERT INTO payment_user_subscriptions (user_id, tier, expire_at, status)
VALUES (10001, 'MONTHLY', now() + interval '30 days', 1)
ON CONFLICT (user_id)
DO UPDATE SET tier = EXCLUDED.tier,
              expire_at = EXCLUDED.expire_at,
              status = EXCLUDED.status,
              updated_at = CURRENT_TIMESTAMP;
```

预期：

```text
MONTHLY 返回 active=true
daily_right_swipe_limit = 80
daily_card_limit = 120
daily_super_hi_limit = 1
```

### 6.5 Redis 验收

当前链路不依赖 Redis。后续 match-service 可以自行做 5 分钟订阅缓存。

### 6.6 异常验收

| 场景              | 预期            |
| --------------- | ------------- |
| 没有订阅记录          | 返回 `FREE`     |
| `status != 1`   | 返回 `FREE`     |
| `expire_at` 为空  | 返回 `FREE`     |
| `expire_at` 已过期 | 返回 `FREE`     |
| 未知 `tier`       | 返回 `FREE`     |
| `userId <= 0`   | `code = 4000` |

---

## 7. 创建订单 CreateOrder

### 7.1 HTTP 接口

```http
GET /internal/debug/payment/order/create?userId={userId}&productCode={productCode}&provider={provider}
```

金币包示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/create?userId=10001&productCode=COIN_100&provider=PAYPAL"
```

订阅示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/create?userId=10001&productCode=SUB_MONTHLY&provider=APPLE"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "orderNo": "pay_...",
  "userId": 10001,
  "productType": "COIN",
  "productCode": "COIN_100",
  "provider": "PAYPAL",
  "amountCents": 99,
  "currency": "USD",
  "coinAmount": 100,
  "subscriptionTier": "",
  "status": 10
}
```

### 7.2 gRPC 方法

```text
CreateOrder(CreateOrderRequest) returns (CreateOrderResponse)
```

请求字段：

| 字段             | 说明                                      |
| -------------- | --------------------------------------- |
| `user_id`      | 创建订单的业务用户 ID                            |
| `product_code` | 商品编码，例如 `COIN_100` / `SUB_MONTHLY` |
| `provider`     | 支付渠道，例如 `APPLE` / `PAYPAL`              |

响应字段：

| 字段                  | 说明                         |
| ------------------- | -------------------------- |
| `code`              | `0` 表示成功，`4000` 表示参数错误 |
| `message`           | 返回说明                       |
| `order_no`          | 本地业务订单号，格式为 `pay_...`    |
| `user_id`           | 用户 ID                      |
| `product_type`      | 商品类型：`COIN` / `SUBSCRIPTION` |
| `product_code`      | 商品编码                       |
| `provider`          | 支付渠道                       |
| `amount_cents`      | 订单金额，单位为分                  |
| `currency`          | 币种，当前为 `USD`               |
| `coin_amount`       | 金币包对应金币数，订阅订单为 `0`        |
| `subscription_tier` | 订阅档位，金币订单为空字符串            |
| `status`            | 订单状态，`10 = CREATED`，表示等待支付 |

### 7.3 当前支持的商品码

| 商品码           | 商品类型           | 金额分  | 金币数 | 订阅档位      |
| ------------- | ---------------- | ---- | ---- | ---------- |
| `COIN_100`    | `COIN`           | `99` | `100` | 空          |
| `COIN_500`    | `COIN`           | `399` | `500` | 空          |
| `SUB_WEEKLY`  | `SUBSCRIPTION`   | `699` | `0` | `WEEKLY`   |
| `SUB_MONTHLY` | `SUBSCRIPTION`   | `1999` | `0` | `MONTHLY`  |
| `SUB_YEARLY`  | `SUBSCRIPTION`   | `9999` | `0` | `YEARLY`   |

### 7.4 完整链路

```text
PaymentCoinDebugController.createOrder
PaymentGrpcService.createOrder
  -> PaymentOrderService.createOrder
     -> PaymentOrderManager.createOrder
        -> PaymentOrderMapper.insert
           -> payment_orders
```

### 7.5 数据库验收

```sql
SELECT order_no, user_id, product_type, product_code, provider,
       amount_cents, currency, coin_amount, subscription_tier, status
FROM payment_orders
WHERE user_id = 10001
ORDER BY id DESC;
```

预期：

```text
order_no 以 pay_ 开头
status = 10
product_code / provider / amount_cents 与请求商品一致
provider_trade_no / paid_at / closed_at 当前为空
```

### 7.6 Redis 验收

当前创建订单链路不依赖 Redis。

### 7.7 异常验收

| 场景                    | 预期            |
| --------------------- | ------------- |
| `userId <= 0`         | `code = 4000` |
| `productCode` 为空     | `code = 4000` |
| 不支持的 `productCode`  | `code = 4000` |
| `provider` 为空        | `code = 4000` |

### 7.8 关键设计记忆点

- `CreateOrder` 当前只是订单骨架，负责把“用户想买什么”保存成一条本地订单。
- 当前不会跳转支付、不会验签、不会发金币、不会开订阅。
- 后续支付回调成功后，才会根据 `product_type` 决定发金币或开通订阅。
- `status = 10` 表示订单已创建，等待真实支付结果。

---

## 8. 模拟支付成功 MockPaySuccess

### 8.1 HTTP 接口

```http
GET /internal/debug/payment/order/mock-pay-success?orderNo={orderNo}&providerTradeNo={providerTradeNo}
```

金币订单示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/mock-pay-success?orderNo=pay_xxx&providerTradeNo=paypal-test-001"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "orderNo": "pay_xxx",
  "userId": 10001,
  "productType": "COIN",
  "productCode": "COIN_100",
  "providerTradeNo": "paypal-test-001",
  "status": 20,
  "balance": 190,
  "ledgerNo": "...",
  "tier": "",
  "active": false,
  "expireAtMs": 0
}
```

订阅订单成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "orderNo": "pay_xxx",
  "userId": 10001,
  "productType": "SUBSCRIPTION",
  "productCode": "SUB_MONTHLY",
  "providerTradeNo": "apple-test-001",
  "status": 20,
  "balance": 0,
  "ledgerNo": "",
  "tier": "MONTHLY",
  "active": true,
  "expireAtMs": 1780000000000
}
```

### 8.2 gRPC 方法

```text
MockPaySuccess(MockPaySuccessRequest) returns (MockPaySuccessResponse)
```

请求字段：

| 字段                  | 说明                           |
| ------------------- | ---------------------------- |
| `order_no`          | 本地业务订单号，例如 `pay_...`        |
| `provider_trade_no` | 模拟第三方支付流水号，测试时自己传唯一字符串 |

响应字段：

| 字段                  | 说明                              |
| ------------------- | ------------------------------- |
| `code`              | `0` 表示成功，`4000` 表示参数或订单状态错误 |
| `message`           | 返回说明                            |
| `order_no`          | 本地业务订单号                         |
| `user_id`           | 用户 ID                           |
| `product_type`      | `COIN` / `SUBSCRIPTION`          |
| `product_code`      | 商品编码                            |
| `provider_trade_no` | 第三方支付流水号                         |
| `status`            | `20 = PAID`，表示订单已支付             |
| `balance`           | 金币订单返回当前余额，订阅订单为 `0`           |
| `ledger_no`         | 金币订单返回入账流水号，订阅订单为空字符串          |
| `tier`              | 订阅订单返回档位，金币订单为空字符串             |
| `active`            | 订阅是否有效                           |
| `expire_at_ms`      | 订阅过期时间毫秒时间戳，金币订单为 `0`          |

### 8.3 完整链路

```text
PaymentCoinDebugController.mockPaySuccess
PaymentGrpcService.mockPaySuccess
  -> PaymentOrderService.mockPaySuccess
     -> PaymentOrderManager.findByOrderNo
        -> PaymentOrderMapper.selectOne
     -> PaymentOrderManager.markPaidIfCreated
        -> PaymentOrderMapper.markPaidIfCreated
     -> COIN: PaymentCoinService.grantCoins
     -> SUBSCRIPTION: PaymentSubscriptionService.setSubscription
```

### 8.4 数据库验收

订单表：

```sql
SELECT order_no, user_id, product_type, product_code, provider_trade_no,
       status, paid_at, updated_at
FROM payment_orders
WHERE order_no = 'pay_xxx';
```

预期：

```text
status = 20
provider_trade_no = 请求传入值
paid_at 不为空
```

金币订单额外检查：

```sql
SELECT user_id, balance, total_recharge
FROM payment_coin_accounts
WHERE user_id = 10001;

SELECT ledger_no, user_id, change_amount, direction, reason, idempotency_key
FROM payment_coin_ledger
WHERE idempotency_key = 'payment-order:pay_xxx';
```

预期：

```text
balance 增加订单 coin_amount
change_amount = 正数 coin_amount
direction = 1
reason = PAYMENT_ORDER
```

订阅订单额外检查：

```sql
SELECT user_id, tier, expire_at, status
FROM payment_user_subscriptions
WHERE user_id = 10001;
```

预期：

```text
tier = 订单 subscription_tier
status = 1
expire_at 不为空且在当前时间之后
```

### 8.5 Redis 验收

当前模拟支付成功链路不依赖 Redis。

### 8.6 异常验收

| 场景                         | 预期            |
| -------------------------- | ------------- |
| `orderNo` 为空              | `code = 4000` |
| `providerTradeNo` 为空      | `code = 4000` |
| 订单不存在                     | `code = 4000` |
| 订单不是 `CREATED` 或 `PAID` | `code = 4000` |
| 重复调用已支付订单                 | 返回 `already paid`，不重复发金币 |

### 8.7 关键设计记忆点

- `MockPaySuccess` 是本地模拟回调，不是真实第三方回调验签。
- 订单状态从 `10 CREATED` 改为 `20 PAID`。
- 金币订单通过 `PaymentCoinService.grantCoins` 发金币，幂等键是 `payment-order:{orderNo}`。
- 订阅订单通过 `PaymentSubscriptionService.setSubscription` 开订阅。
- 后续真实支付回调要在这条链路前面补验签、金额校验、渠道流水幂等和回调原文记录。

---

## 9. 查询订单 GetOrder

### 9.1 HTTP 接口

```http
GET /internal/debug/payment/order/get?orderNo={orderNo}
```

示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/order/get?orderNo=pay_xxx"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "orderNo": "pay_xxx",
  "userId": 10001,
  "productType": "COIN",
  "productCode": "COIN_100",
  "provider": "PAYPAL",
  "amountCents": 99,
  "currency": "USD",
  "coinAmount": 100,
  "subscriptionTier": "",
  "status": 20,
  "providerTradeNo": "paypal-test-001",
  "paidAtMs": 1780000000000,
  "createdAtMs": 1780000000000,
  "updatedAtMs": 1780000000000
}
```

### 9.2 gRPC 方法

```text
GetOrder(GetOrderRequest) returns (GetOrderResponse)
```

请求字段：

| 字段 | 说明 |
| --- | --- |
| `order_no` | 本地业务订单号，例如 `pay_...` |

响应字段：

| 字段 | 说明 |
| --- | --- |
| `code` | `0` 表示成功，`4000` 表示参数错误或订单不存在 |
| `message` | 返回说明 |
| `order_no` | 本地业务订单号 |
| `user_id` | 用户 ID |
| `product_type` | `COIN` / `SUBSCRIPTION` |
| `product_code` | 商品编码 |
| `provider` | 支付渠道 |
| `amount_cents` | 订单金额，单位为分 |
| `currency` | 币种，当前为 `USD` |
| `coin_amount` | 金币订单对应金币数，订阅订单为 `0` |
| `subscription_tier` | 订阅档位，金币订单为空字符串 |
| `status` | `10 = CREATED`，`20 = PAID`，`30 = CLOSED` |
| `provider_trade_no` | 第三方支付流水号，未支付时为空字符串 |
| `paid_at_ms` | 支付成功时间，未支付时为 `0` |
| `created_at_ms` | 订单创建时间 |
| `updated_at_ms` | 订单更新时间 |

### 9.3 完整链路

```text
PaymentCoinDebugController.getOrder
PaymentGrpcService.getOrder
  -> PaymentOrderService.getOrder
     -> PaymentOrderManager.findByOrderNo
        -> PaymentOrderMapper.selectOne
           -> payment_orders
```

### 9.4 数据库验收

```sql
SELECT order_no, user_id, product_type, product_code, provider,
       amount_cents, currency, coin_amount, subscription_tier,
       status, provider_trade_no, paid_at, created_at, updated_at
FROM payment_orders
WHERE order_no = 'pay_xxx';
```

预期：

```text
刚创建未支付订单：status = 10，provider_trade_no 为空，paid_at 为空。
模拟支付成功后：status = 20，provider_trade_no 不为空，paid_at 不为空。
```

### 9.5 Redis 验收

当前查询订单链路不依赖 Redis。

### 9.6 异常验收

| 场景 | 预期 |
| --- | --- |
| `orderNo` 为空 | `code = 4000` |
| 订单不存在 | `code = 4000` |

### 9.7 关键设计记忆点

- `GetOrder` 只读订单，不改订单状态。
- 它用于确认订单是待支付、已支付还是后续关闭状态。
- 前端 / gateway 后续可以用它轮询或查询支付结果。

---

## 10. 金币入账 GrantCoins

### 10.1 HTTP 接口

```http
GET /internal/debug/payment/grant?userId={userId}&amount={amount}&reason={reason}&idempotencyKey={idempotencyKey}
```

示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/grant?userId=10001&amount=100&reason=TEST_GRANT&idempotencyKey=grant-10001-001"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "userId": 10001,
  "balance": 100,
  "ledgerNo": "..."
}
```

### 10.2 gRPC 方法

```text
GrantCoins(GrantCoinsRequest) returns (GrantCoinsResponse)
```

请求字段：

| 字段                | 说明                                |
| ----------------- | --------------------------------- |
| `user_id`         | 要入账的业务用户 ID                       |
| `amount`          | 入账金币数量，必须大于 0                     |
| `reason`          | 入账原因，例如 `TEST_GRANT` / `RECHARGE` |
| `idempotency_key` | 幂等键，同一笔入账必须使用同一个 key              |

响应字段：

| 字段          | 说明                     |
| ----------- | ---------------------- |
| `code`      | `0` 表示成功，`4000` 表示参数错误 |
| `message`   | 返回说明                   |
| `user_id`   | 用户 ID                  |
| `balance`   | 入账后的余额                 |
| `ledger_no` | 金币流水号                  |

### 10.3 完整链路

```text
PaymentCoinDebugController.grantCoins
PaymentGrpcService.grantCoins
  -> PaymentCoinService.grantCoins
     -> PaymentCoinManager.createAccountIfAbsent
        -> PaymentCoinAccountMapper.selectById / insert
     -> PaymentCoinManager.findLedgerByIdempotencyKey
        -> PaymentCoinLedgerMapper.selectOne
     -> PaymentCoinManager.grantCoins
        -> PaymentCoinAccountMapper.grantCoins
     -> PaymentCoinManager.findAccount
        -> PaymentCoinAccountMapper.selectById
     -> PaymentCoinManager.createLedger
        -> PaymentCoinLedgerMapper.insert
```

### 10.4 数据库验收

账户表：

```sql
SELECT user_id, balance, total_recharge, total_consume, status
FROM payment_coin_accounts
WHERE user_id = 10001;
```

预期：

```text
balance 增加 amount
total_recharge 增加 amount
total_consume 不变
status = 1
```

流水表：

```sql
SELECT ledger_no, user_id, change_amount, balance_after, direction, reason, idempotency_key
FROM payment_coin_ledger
WHERE user_id = 10001
ORDER BY id DESC;
```

预期：

```text
change_amount = 正数 amount
direction = 1
reason = TEST_GRANT
idempotency_key = grant-10001-001
```

### 10.5 Redis 验收

当前链路不依赖 Redis。

### 10.6 异常验收

| 场景                        | 预期                 |
| ------------------------- | ------------------ |
| `userId <= 0`             | `code = 4000`      |
| `amount <= 0`             | `code = 4000`      |
| `reason` 为空               | `code = 4000`      |
| `idempotencyKey` 为空       | `code = 4000`      |
| 同一个 `idempotencyKey` 重复请求 | 余额不重复增加，只返回同一笔流水结果 |

---

## 11. 查询余额 GetCoins

### 11.1 HTTP 接口

```http
GET /internal/debug/payment/coins?userId={userId}
```

示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/coins?userId=10001"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "userId": 10001,
  "balance": 100
}
```

### 11.2 gRPC 方法

```text
GetCoins(GetCoinsRequest) returns (GetCoinsResponse)
```

请求字段：

| 字段        | 说明          |
| --------- | ----------- |
| `user_id` | 要查询的业务用户 ID |

响应字段：

| 字段        | 说明                     |
| --------- | ---------------------- |
| `code`    | `0` 表示成功，`4000` 表示参数错误 |
| `message` | 返回说明                   |
| `user_id` | 用户 ID                  |
| `balance` | 当前金币余额                 |

### 11.3 完整链路

```text
PaymentCoinDebugController.getCoins
PaymentGrpcService.getCoins
  -> PaymentCoinService.getCoins
     -> PaymentCoinManager.createAccountIfAbsent
        -> PaymentCoinAccountMapper.selectById / insert
```

### 11.4 数据库验收

```sql
SELECT user_id, balance, total_recharge, total_consume, status
FROM payment_coin_accounts
WHERE user_id = 10001;
```

如果用户第一次进入金币系统，查询余额会自动创建一条 `balance = 0` 的账户记录。

### 11.5 Redis 验收

当前链路不依赖 Redis。

### 11.6 异常验收

| 场景            | 预期            |
| ------------- | ------------- |
| `userId` 为空   | `code = 4000` |
| `userId <= 0` | `code = 4000` |

---

## 12. 扣金币 ConsumeCoins

### 12.1 HTTP 接口

```http
GET /internal/debug/payment/consume?userId={userId}&amount={amount}&reason={reason}&idempotencyKey={idempotencyKey}
```

示例：

```powershell
curl "http://localhost:18085/internal/debug/payment/consume?userId=10001&amount=10&reason=CHAT_MESSAGE&idempotencyKey=im-msg-001"
```

成功响应：

```json
{
  "code": 0,
  "message": "ok",
  "userId": 10001,
  "balance": 90,
  "ledgerNo": "..."
}
```

余额不足响应：

```json
{
  "code": 3001,
  "message": "insufficient coins",
  "userId": 10001,
  "balance": 0,
  "ledgerNo": ""
}
```

### 12.2 gRPC 方法

```text
ConsumeCoins(ConsumeCoinsRequest) returns (ConsumeCoinsResponse)
```

请求字段：

| 字段                | 说明                                  |
| ----------------- | ----------------------------------- |
| `user_id`         | 要扣费的业务用户 ID                         |
| `amount`          | 扣费金币数量，必须大于 0                       |
| `reason`          | 扣费原因，例如 `CHAT_MESSAGE` / `SUPER_HI` |
| `idempotency_key` | 幂等键，同一笔扣费必须使用同一个 key                |

响应字段：

| 字段          | 说明                             |
| ----------- | ------------------------------ |
| `code`      | `0` 成功，`3001` 金币不足，`4000` 参数错误 |
| `message`   | 返回说明                           |
| `user_id`   | 用户 ID                          |
| `balance`   | 扣费后的余额，或当前余额                   |
| `ledger_no` | 金币流水号                          |

### 12.3 完整链路

```text
PaymentCoinDebugController.consumeCoins
PaymentGrpcService.consumeCoins
  -> PaymentCoinService.consumeCoins
     -> PaymentCoinManager.createAccountIfAbsent
        -> PaymentCoinAccountMapper.selectById / insert
     -> PaymentCoinManager.findLedgerByIdempotencyKey
        -> PaymentCoinLedgerMapper.selectOne
     -> PaymentCoinManager.consumeCoins
        -> PaymentCoinAccountMapper.consumeCoins
     -> PaymentCoinManager.findAccount
        -> PaymentCoinAccountMapper.selectById
     -> PaymentCoinManager.createLedger
        -> PaymentCoinLedgerMapper.insert
```

### 12.4 数据库验收

账户表：

```sql
SELECT user_id, balance, total_recharge, total_consume, status
FROM payment_coin_accounts
WHERE user_id = 10001;
```

预期：

```text
balance 减少 amount
total_consume 增加 amount
total_recharge 不变
status = 1
```

流水表：

```sql
SELECT ledger_no, user_id, change_amount, balance_after, direction, reason, idempotency_key
FROM payment_coin_ledger
WHERE user_id = 10001
ORDER BY id DESC;
```

预期：

```text
change_amount = 负数 -amount
direction = 2
reason = CHAT_MESSAGE
idempotency_key = im-msg-001
```

### 12.5 Redis 验收

当前链路不依赖 Redis。

### 12.6 异常验收

| 场景                        | 预期                   |
| ------------------------- | -------------------- |
| `userId <= 0`             | `code = 4000`        |
| `amount <= 0`             | `code = 4000`        |
| `reason` 为空               | `code = 4000`        |
| `idempotencyKey` 为空       | `code = 4000`        |
| 余额不足                      | `code = 3001`，不写扣费流水 |
| 同一个 `idempotencyKey` 重复请求 | 余额不重复扣减，只返回同一笔流水结果   |

---

## 13. gRPC 验收命令

以下命令默认服务已启动，gRPC 端口为 `19085`。

### 13.1 Ping

```powershell
'{"message":"hello"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/Ping
```

预期：

```json
{"message":"payment-service pong: hello"}
```

### 13.2 GetSubscription

```powershell
'{"userId":10001}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GetSubscription
```

预期：

```json
{"code":0,"message":"ok","userId":"10001","tier":"FREE","active":false,"expireAtMs":"0","dailyRightSwipeLimit":20,"dailyCardLimit":50,"dailySuperHiLimit":0}
```

### 13.3 GrantCoins

```powershell
'{"userId":10001,"amount":100,"reason":"TEST_GRANT","idempotencyKey":"grant-10001-001"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GrantCoins
```

预期：

```json
{"code":0,"message":"ok","userId":"10001","balance":"100","ledgerNo":"..."}
```

### 13.4 GetCoins

```powershell
'{"userId":10001}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GetCoins
```

预期：

```json
{"code":0,"message":"ok","userId":"10001","balance":"100"}
```

### 13.5 ConsumeCoins

```powershell
'{"userId":10001,"amount":10,"reason":"CHAT_MESSAGE","idempotencyKey":"im-msg-001"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/ConsumeCoins
```

预期：

```json
{"code":0,"message":"ok","userId":"10001","balance":"90","ledgerNo":"..."}
```

### 13.6 CreateOrder

```powershell
'{"userId":10001,"productCode":"COIN_100","provider":"PAYPAL"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/CreateOrder
```

预期：

```json
{"code":0,"message":"ok","orderNo":"pay_...","userId":"10001","productType":"COIN","productCode":"COIN_100","provider":"PAYPAL","amountCents":"99","currency":"USD","coinAmount":"100","subscriptionTier":"","status":10}
```

### 13.7 MockPaySuccess

```powershell
'{"orderNo":"pay_xxx","providerTradeNo":"paypal-test-001"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/MockPaySuccess
```

预期：

```json
{"code":0,"message":"ok","orderNo":"pay_xxx","userId":"10001","productType":"COIN","productCode":"COIN_100","providerTradeNo":"paypal-test-001","status":20,"balance":"190","ledgerNo":"...","tier":"","active":false,"expireAtMs":"0"}
```

### 13.8 GetOrder

```powershell
'{"orderNo":"pay_xxx"}' | & C:\tmp\grpcurl\grpcurl.exe -plaintext -proto proto/payment/src/main/proto/payment.proto -d '@' localhost:19085 dating.payment.v1.PaymentService/GetOrder
```

预期：

```json
{"code":0,"message":"ok","orderNo":"pay_xxx","userId":"10001","productType":"COIN","productCode":"COIN_100","provider":"PAYPAL","amountCents":"99","currency":"USD","coinAmount":"100","subscriptionTier":"","status":20,"providerTradeNo":"paypal-test-001","paidAtMs":"1780000000000","createdAtMs":"1780000000000","updatedAtMs":"1780000000000"}
```

---

## 14. 推荐验收顺序

建议你按下面顺序完整跑一遍：

1. `GET /internal/ping`
2. `GET /internal/check/db`
3. `GET /internal/check/redis`
4. `GetSubscription` 查询无订阅用户，确认返回 `FREE`
5. 手动插入 `MONTHLY` 订阅后再次查询，确认返回 `MONTHLY`
6. `GrantCoins` 给 `10001` 加 `100` 金币
7. `GetCoins` 确认余额为 `100`
8. 再次用同一个 `idempotencyKey` 调 `GrantCoins`，确认余额仍为 `100`
9. `ConsumeCoins` 扣 `10` 金币
10. `GetCoins` 确认余额为 `90`
11. 再次用同一个 `idempotencyKey` 调 `ConsumeCoins`，确认余额仍为 `90`
12. 用新用户直接 `ConsumeCoins`，确认返回 `3001 insufficient coins`
13. `CreateOrder` 创建 `COIN_100` 订单，确认 `payment_orders.status = 10`
14. `GetOrder` 查询 `COIN_100` 订单，确认 `status = 10`
15. `CreateOrder` 创建 `SUB_MONTHLY` 订单，确认 `product_type = SUBSCRIPTION`
16. `GetOrder` 查询 `SUB_MONTHLY` 订单，确认 `status = 10`
17. `MockPaySuccess` 支付 `COIN_100` 订单，确认状态变 `20` 且金币入账
18. `GetOrder` 再查 `COIN_100` 订单，确认 `status = 20`
19. `MockPaySuccess` 支付 `SUB_MONTHLY` 订单，确认状态变 `20` 且订阅开通
20. `GetOrder` 再查 `SUB_MONTHLY` 订单，确认 `status = 20`
21. 重复调用同一个已支付订单，确认返回 `already paid` 且不重复发金币
22. 数据库检查 `payment_user_subscriptions`
23. 数据库检查 `payment_coin_accounts`
24. 数据库检查 `payment_coin_ledger`
25. 数据库检查 `payment_orders`

---

## 15. 关键设计记忆点

- `payment_coin_accounts` 是余额表，每个用户一行。
- `payment_coin_ledger` 是流水表，每次入账 / 扣费一行。
- `payment_orders` 是支付订单表，每次下单一行。
- `idempotency_key` 是幂等核心，同一笔业务请求必须传同一个 key。
- 入账流水 `change_amount` 是正数，`direction = 1`。
- 扣费流水 `change_amount` 是负数，`direction = 2`。
- 扣费 SQL 使用 `balance >= amount`，避免并发扣成负数。
- 当前 P0 是内部金币能力，不代表真实三方支付完成。
- `CreateOrder` 当前只创建本地订单，不代表已经支付成功。
- `MockPaySuccess` 当前模拟真实支付成功后的业务落地，不包含渠道验签。
- `GetOrder` 当前用于查订单状态，不做任何状态变更。
- `GetSubscription` 当前只查询本地订阅表，不代表真实订阅购买链路完成。
- 订阅权益当前在 `PaymentSubscriptionService` 写死，后续可迁到配置表或 Nacos。

---

## 16. 当前实现与技术方案差异

`docs/design/payment-service-design.md` 当前内容异常，仅为 `400: Invalid request`，所以本验收文档以当前代码实现和上下游需求为准。

| 能力                           | 当前状态  |
| ---------------------------- | ----- |
| gRPC Ping                    | 已完成   |
| HTTP ping / DB / Redis check | 已完成   |
| 金币账户表                        | 已完成   |
| 金币流水表                        | 已完成   |
| 查询余额 GetCoins                | 已完成   |
| 内部加金币 GrantCoins             | 已完成   |
| 扣金币 ConsumeCoins             | 已完成   |
| 查询订阅 GetSubscription         | 已完成   |
| 创建订单 CreateOrder             | 已完成订单骨架 |
| 模拟支付成功 MockPaySuccess       | 已完成本地模拟 |
| 查询订单 GetOrder                | 已完成   |
| 幂等键防重复加 / 重复扣                | 已完成   |
| 真实支付拉起参数                     | 未完成   |
| 第三方支付                        | 未完成   |
| 支付回调验签                       | 未完成   |
| 支付成功后发金币 / 开订阅               | 已完成模拟链路 |
| 订阅购买 / 续费真实闭环                | 未完成   |
| 退款 / 对账                      | 未完成   |
| Redis 缓存余额                   | 当前未使用 |

这份文档验收的是 payment-service 当前 P0 金币主链路、订阅查询、订单骨架和模拟支付成功链路，不要求一次性完成真实支付、支付回调验签、订阅续费和对账。
