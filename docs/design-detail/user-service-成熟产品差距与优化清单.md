# user-service 成熟产品差距与优化清单

> 用途：记录当前 user-service 与成熟产品级实现之间的差距，方便后续迭代优化。  
> 定位：当前版本优先跑通身份解析主链路，目标是可测试、可理解、可继续扩展；本文不是否定当前实现，而是给后续升级排路线图。

---

## 1. 当前版本定位

当前 user-service 已完成身份解析 P0 主链路：

- `ResolveOrCreateByPhone`
- `ResolveOrCreateByDevice`
- `CheckBan`
- `CheckBan` Redis 短缓存
- 业务 `user_id`
- common 雪花 ID
- gRPC 服务注册
- 参数校验
- gRPC 异常转换
- HTTP debug DB / Redis 检查
- 身份链路接口验收文档

当前版本适合：

- 本地调试身份解析链路
- 学习微服务分层
- 理解手机号 / 设备到业务 `user_id` 的解析过程
- 理解 Redis cache-aside 短缓存
- 做 user-service 第一版交付型 demo

但距离成熟产品仍有不少差距，主要集中在：

- 注册并发保护
- 事务边界
- 用户资料 Profile
- 第三方登录
- 头像和兴趣
- 工程化测试
- 可观测性
- 风控和运营能力

---

## 2. 数据一致性与可靠性差距

### 2.1 注册锁和事务边界还可以拆得更细

当前计划中的注册锁做法通常会是：

```text
resolveOrCreateByPhone / resolveOrCreateByDevice
  -> 加注册锁
  -> 开启事务
  -> 查绑定
  -> 未命中则创建 user_info
  -> 插入 phone / device 绑定
  -> 提交事务
  -> 释放锁
```

这种写法的优点：

- 代码容易理解。
- 能快速解决并发创建重复用户的问题。
- 第一版适合学习和验收。

不足：

- 锁持有时间覆盖了整个事务。
- 如果事务里后续加入更多逻辑，锁持有时间会变长。
- 高并发下，同一个手机号 / 设备的等待时间可能被放大。
- 加锁逻辑和数据库事务逻辑混在同一个 service 方法里，后续维护不够清晰。

成熟产品更常见的方向是把边界拆细：

```text
外层：
  负责获取注册锁、释放注册锁

内层：
  负责数据库事务，只做必须保持一致的 DB 写入
```

更理想的结构：

```text
resolveOrCreateByPhone
  -> validate
  -> executeWithRegisterLock(lockKey, () -> resolveOrCreateByPhoneInTransaction(...))

resolveOrCreateByPhoneInTransaction
  -> @Transactional
  -> 查绑定
  -> 创建 user_info
  -> 插入 user_login_phone
```

需要注意：

- Spring 的 `@Transactional` 通过代理生效，同一个类内部直接调用 private 方法不会触发新事务代理。
- 如果要把事务方法单独拆出来，通常需要放到另一个 Spring Bean，例如 `UserIdentityRegisterService`。
- 外层锁方法可以留在编排 service，内层事务方法放到专门的注册 service。

后续优化方向：

1. 新增 `UserIdentityRegisterService`。
2. 把 phone / device / third-party 的数据库创建逻辑放进这个 service。
3. 在 `UserIdentityRegisterService` 的方法上加 `@Transactional`。
4. `UserIdentityServiceImpl` 只负责参数校验、拼锁 key、获取锁、调用注册 service。
5. 锁等待时间、锁租约时间改为配置项。
6. 针对拿不到锁的情况返回明确业务异常，而不是普通系统错误。
7. 注册锁等待时间、锁租约时间、头像 presign TTL、头像大小限制、兴趣数量限制改为 Nacos 配置项。

推荐结构：

```text
UserIdentityServiceImpl
  -> validate
  -> build lock key
  -> executeWithRegisterLock
     -> UserIdentityRegisterService.resolveOrCreateByPhoneInTransaction
        -> UserLoginPhoneManager.findByPhoneAndApp
        -> UserInfoManager.insertPlaceholder
        -> UserLoginPhoneManager.insertBinding
```

优先级：P1。

---

## 3. 后续优化优先级建议

### 3.1 优先做 P1

这些对稳定性提升明显，且不会太离谱：

1. ResolveOrCreate 注册锁。
2. 拆分注册锁和事务边界。
3. 补 `created` 字段。
4. 实现 `ResolveOrCreateByThirdParty`。
5. 补关键日志。
6. 补 user-service grpcurl / Postman 验收集合。

### 3.2 再做 P2

这些更像完整用户域能力：

1. `UserProfileService.GetProfile`。
2. `UserProfileService.UpdateProfile`。
3. `UpsertOnboarding`。
4. 头像 presign / confirm。
5. 兴趣标签。
6. Profile Redis 缓存。
7. 资料审核状态。

---

## 4. 总结

当前 user-service 的身份解析主链路已经具备第一版可用性。下一阶段重点不只是“能创建用户”，而是让创建链路在并发、多实例、异常情况下更稳定。

注册锁是 P1 的第一步；锁和事务边界拆分是后续企业级优化点。建议先把简单锁跑通，再逐步拆出更清晰的注册事务服务。
