# 2026-06-13 user-service PostgreSQL / Redis 接入复盘

## 1. 文档目的

这份文档记录 `user-service` 接入共享 PostgreSQL 和 Redis 的最小连通性验证过程。

重点不是记录真实密码，而是记录：

- 配置写在哪里。
- 密码应该放在哪里。
- 本地怎么启动服务。
- 怎么验证 PG / Redis 是否真的连通。
- 遇到 500、WRONGPASS 时怎么排查。

真实密码不要写进本文档，也不要提交到 GitHub。

---

## 2. 本次完成内容

本次已经完成：

- `user-service` 增加 PostgreSQL datasource 配置。
- `user-service` 增加 Redis 配置。
- PostgreSQL 密码使用环境变量 `${POSTGRES_PASSWORD}`。
- Redis 密码使用环境变量 `${REDIS_PASSWORD}`。
- Nacos 密码使用环境变量 `${NACOS_PASSWORD}`。
- 新增 `/internal/check/db`，执行 `select 1` 验证数据库连通。
- 新增 `/internal/check/redis`，写入短 TTL key 验证 Redis 连通。
- 本地验证两个接口都返回 200。
- 修改已经提交并推送到 GitHub。

提交记录：

```text
chore: add user-service database check
chore: add user-service redis check
```

---

## 3. 相关文件

配置文件：

```text
dating-server/user-service/src/main/resources/application.yml
```

数据库验证接口：

```text
dating-server/user-service/src/main/java/com/jianjiange/dating/user/DbCheckController.java
```

Redis 验证接口：

```text
dating-server/user-service/src/main/java/com/jianjiange/dating/user/RedisCheckController.java
```

Maven 依赖：

```text
dating-server/user-service/pom.xml
```

---

## 4. application.yml 当前关键配置

`user-service` 当前端口：

```yaml
server:
  port: 18081
```

PostgreSQL 配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://38.76.188.242:5433/dating_dev_hanlian?stringtype=unspecified
    username: jianjian_test
    password: ${POSTGRES_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-init-sql: SET TIME ZONE 'UTC'
```

Redis 配置：

```yaml
spring:
  data:
    redis:
      host: 38.76.188.242
      port: 6380
      password: ${REDIS_PASSWORD}
      database: 1
      timeout: 3s
```

Nacos Discovery 配置：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        server-addr: 38.76.188.242:8848
        namespace: dev-hanlian
        username: nacos
        password: ${NACOS_PASSWORD}
```

关键点：

- 配置文件里只放 `${变量名}`。
- 不把真实密码写进 `application.yml`。
- PostgreSQL 使用个人库 `dating_dev_hanlian`。
- Redis 使用 db `1`。
- Nacos namespace 使用 `dev-hanlian`。

---

## 5. 本地环境变量

本次服务启动需要三个环境变量：

```text
POSTGRES_PASSWORD
REDIS_PASSWORD
NACOS_PASSWORD
```

临时设置方式，只对当前 PowerShell 窗口有效：

```powershell
$env:POSTGRES_PASSWORD="从开发环境接入文档复制 PG 密码"
$env:REDIS_PASSWORD="从开发环境接入文档复制 Redis 密码"
$env:NACOS_PASSWORD="从开发环境接入文档复制 Nacos 密码"
```

查看当前窗口是否已经拿到变量：

```powershell
echo $env:POSTGRES_PASSWORD
echo $env:REDIS_PASSWORD
echo $env:NACOS_PASSWORD
```

注意：

- Windows 用户环境变量页面里有变量，不代表已经打开的 PowerShell 能读到。
- PowerShell / IDEA 启动时会复制一份环境变量。
- 修改 Windows 环境变量后，旧 PowerShell 不会自动继承。
- 最稳的做法是在启动服务的同一个 PowerShell 窗口里 `echo` 确认。

---

## 6. 本地启动方式

进入 `user-service` 目录：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server\user-service
```

确认环境变量：

```powershell
echo $env:POSTGRES_PASSWORD
echo $env:REDIS_PASSWORD
echo $env:NACOS_PASSWORD
```

启动服务：

```powershell
mvn spring-boot:run
```

启动成功后，不要关闭这个窗口。

另开一个 PowerShell 窗口验证接口。

---

## 7. PostgreSQL 验证接口

接口地址：

```text
http://localhost:18081/internal/check/db
```

验证命令：

```powershell
curl http://localhost:18081/internal/check/db
```

成功结果：

```json
{"result":"1","database":"ok"}
```

说明：

- 接口内部执行 `select 1`。
- 返回 200 表示 Java 代码已经真的连上共享 PostgreSQL。
- 这比服务能启动更进一步，因为它验证了 datasource 可以实际执行 SQL。

---

## 8. Redis 验证接口

接口地址：

```text
http://localhost:18081/internal/check/redis
```

验证命令：

```powershell
curl http://localhost:18081/internal/check/redis
```

成功结果：

```json
{"redis":"ok","ttlSeconds":"60","key":"hanlian:user-service:check:redis","value":"ok"}
```

当前验证 key：

```text
hanlian:user-service:check:redis
```

TTL：

```text
60 秒
```

说明：

- 接口会写入一个短 TTL key。
- 再把 value 和 TTL 读出来返回。
- 返回 200 表示 Java 代码已经真的连上共享 Redis。

---

## 9. Redis key 规范

共享 Redis 不能随便写 key。

当前约定：

```text
<个人前缀>:<服务名>:<领域>:<用途>
```

本次使用：

```text
hanlian:user-service:check:redis
```

要求：

- key 必须带个人前缀，避免和别人冲突。
- key 必须带服务名，方便排查来源。
- 测试 key 必须设置 TTL。
- 不要写永久 key。
- 不要把 Redis 当主数据库。

---

## 10. 本次排障记录

### 10.1 Redis 500

现象：

```text
/internal/check/redis 返回 500
```

日志里看到：

```text
WRONGPASS invalid username-password pair or user is disabled.
```

含义：

```text
Java 服务连接到了 Redis，但是认证密码不对。
```

本次原因：

```text
启动服务的进程没有拿到正确的 REDIS_PASSWORD，或者请求打到了旧的 user-service 进程。
```

处理方式：

```powershell
echo $env:REDIS_PASSWORD
netstat -ano | findstr :18081
```

如果 18081 没有旧进程，就在环境变量正确的 PowerShell 里重新启动：

```powershell
mvn spring-boot:run
```

### 10.2 父工程启动失败

现象：

```text
Unable to find a suitable main class
```

原因：

```text
在 dating-server 父工程上执行 spring-boot:run，父工程是 pom 包，没有启动类。
```

推荐处理：

```powershell
cd E:\heart-dev\workspace\hanlian-workspace\dating-server\user-service
mvn spring-boot:run
```

### 10.3 PowerShell 误复制提示符

现象：

```text
PS : 找不到名为“E:\heart-dev\workspace\...”的进程
```

原因：

```text
把 PowerShell 提示符也复制成命令执行了。
```

正确做法：

```text
只复制提示符后面的命令，不复制 PS E:\xxx> 这一段。
```

---

## 11. 当前验证结果

已经验证成功：

```text
/internal/check/db    -> 200
/internal/check/redis -> 200
```

Redis 返回：

```json
{"redis":"ok","ttlSeconds":"60","key":"hanlian:user-service:check:redis","value":"ok"}
```

PostgreSQL 返回：

```json
{"result":"1","database":"ok"}
```

说明：

```text
user-service 已经能同时连接共享 PostgreSQL 和共享 Redis。
```

---

## 12. 红线

- 不把真实密码写进 Git。
- 不把真实密码写进复盘文档。
- 不截图包含密钥的环境变量页面。
- Redis key 必须有 TTL。
- 不要直接操作别人的库、Redis key、Nacos namespace。
- 不要把共享开发环境的地址和密码用于生产环境。

---

## 13. 下一步

P0 已经完成：

- [x] user-service 接入 PostgreSQL 最小连通性验证。
- [x] user-service 接入 Redis 最小连通性验证。
- [x] PG / Redis 接入说明写成本地复盘文档。

下一步进入 P1：

1. proto 生成 Java 代码的本地验证。
2. user-service 建最小表结构和第一张业务表。
3. mobile-gateway -> user-service 的最小调用链。

