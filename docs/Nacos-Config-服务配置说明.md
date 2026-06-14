# Nacos Config 服务配置说明

## 1. 文档目的

这份文档记录当前服务接入 Nacos Config 的约定。

目标是：

```text
代码仓库不提交数据库、Redis、MinIO 等敏感连接信息。
服务启动时先用 NACOS_PASSWORD 登录 Nacos，再从 Nacos Config 拉业务配置。
```

---

## 2. 当前约定

Nacos 地址：

```text
38.76.188.242:8848
```

命名空间：

```text
dev-hanlian
```

Group：

```text
DEFAULT_GROUP
```

Data ID 命名：

```text
<service-name>.yaml
```

例如：

```text
user-service.yaml
match-service.yaml
im-service.yaml
post-service.yaml
payment-service.yaml
```

---

## 3. 本机仍然需要环境变量

`NACOS_PASSWORD` 不能放到 Nacos Config 里。

原因：

```text
服务必须先拿 NACOS_PASSWORD 登录 Nacos，才能读取 Nacos Config。
```

本机启动前设置：

```powershell
$env:NACOS_PASSWORD="jianjiange"
```

---

## 4. user-service.yaml 模板

下面内容放到 Nacos Config，不要提交到 Git。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://38.76.188.242:5433/dating_dev_hanlian?stringtype=unspecified
    username: jianjian_test
    password: 这里填PG密码
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-init-sql: SET TIME ZONE 'UTC'
  data:
    redis:
      host: 38.76.188.242
      port: 6380
      password: 这里填Redis密码
      database: 1
      timeout: 3s
```

验证接口：

```powershell
curl http://localhost:18081/internal/check/db
curl http://localhost:18081/internal/check/redis
```

---

## 5. post-service.yaml 模板

下面内容放到 Nacos Config，不要提交到 Git。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://38.76.188.242:5433/dating_dev_hanlian?stringtype=unspecified
    username: jianjian_test
    password: 这里填PG密码
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-init-sql: SET TIME ZONE 'UTC'
  data:
    redis:
      host: 38.76.188.242
      port: 6380
      password: 这里填Redis密码
      database: 1
      timeout: 3s

storage:
  endpoint: https://minio-api.jianjiange.site
  region: us-east-1
  access-key: admin
  secret-key: 这里填MinIO密码
  bucket: dating-hanlian
  path-style-access: true
```

验证接口：

```powershell
curl http://localhost:18084/internal/check/db
curl http://localhost:18084/internal/check/redis
curl http://localhost:18084/internal/check/minio
```

---

## 6. match-service.yaml 模板

下面内容放到 Nacos Config，不要提交到 Git。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://38.76.188.242:5433/dating_dev_hanlian?stringtype=unspecified
    username: jianjian_test
    password: 这里填PG密码
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-init-sql: SET TIME ZONE 'UTC'
  data:
    redis:
      host: 38.76.188.242
      port: 6380
      password: 这里填Redis密码
      database: 1
      timeout: 3s
```

验证接口：

```powershell
curl http://localhost:18083/internal/check/db
curl http://localhost:18083/internal/check/redis
```

---

## 7. im-service.yaml 模板

下面内容放到 Nacos Config，不要提交到 Git。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://38.76.188.242:5433/dating_dev_hanlian?stringtype=unspecified
    username: jianjian_test
    password: 这里填PG密码
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-init-sql: SET TIME ZONE 'UTC'
  data:
    redis:
      host: 38.76.188.242
      port: 6380
      password: 这里填Redis密码
      database: 1
      timeout: 3s
```

验证接口：

```powershell
curl http://localhost:18082/internal/check/db
curl http://localhost:18082/internal/check/redis
```

---

## 8. payment-service.yaml 模板

下面内容放到 Nacos Config，不要提交到 Git。

```yaml
spring:
  datasource:
    url: jdbc:postgresql://38.76.188.242:5433/dating_dev_hanlian?stringtype=unspecified
    username: jianjian_test
    password: 这里填PG密码
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 1
      connection-init-sql: SET TIME ZONE 'UTC'
  data:
    redis:
      host: 38.76.188.242
      port: 6380
      password: 这里填Redis密码
      database: 1
      timeout: 3s
```

验证接口：

```powershell
curl http://localhost:18085/internal/check/db
curl http://localhost:18085/internal/check/redis
```

---

## 9. 当前服务接入范围

当前按服务需要接入：

```text
user-service: PG / Redis
match-service: PG / Redis
im-service: PG / Redis
post-service: PG / Redis / MinIO
payment-service: PG / Redis
```

当前没有强行让所有服务都接 MinIO。

原因：

```text
MinIO 属于对象存储，只有需要上传或读取对象文件的服务才接。
post-service 需要处理图片/动态资源，所以先接。
im-service 后续如果技术方案确认要直连对象存储，再单独补。
```

不要为了统一而强行让每个服务都接所有中间件。
