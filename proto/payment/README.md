# payment-proto

`payment-proto` 用来从 `payment.proto` 生成 Java gRPC 代码。

## 为什么要有这个模块

`payment-service` 和 `mobile-gateway` 都需要使用同一份 gRPC 契约。

后续订阅、金币、订单、支付回调等能力可以继续扩展这份 proto；当前只保留最小 `Ping`，用于验证工程链路。

## 本地生成命令

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\proto\payment
mvn clean install
```

当前还没有 Nexus 凭据，所以只做本地 `install`，不要执行 `deploy`。
