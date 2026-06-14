# post-service Nacos Config 配置说明

## 1. 文档目的

`post-service` 已接入 Nacos Config，用来从配置中心读取 PostgreSQL、Redis、MinIO 配置。

代码仓库里的 `application.yml` 只保留：

```text
服务端口
Nacos Discovery
Nacos Config
gRPC 端口
```

数据库、Redis、MinIO 连接信息放到 Nacos Config。

---

## 2. Nacos 配置位置

Nacos 控制台：

```text
http://38.76.188.242:8848/nacos
```

命名空间：

```text
dev-hanlian
```

Data ID：

```text
post-service.yaml
```

Group：

```text
DEFAULT_GROUP
```

配置格式：

```text
YAML
```

---

## 3. 配置内容模板

下面这段内容放到 Nacos Config，不要提交到 Git。

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

---

## 4. 本机仍然需要的环境变量

`NACOS_PASSWORD` 仍然不能放在 Nacos Config 里。

原因：

```text
服务必须先拿 NACOS_PASSWORD 登录 Nacos，才能读取 Nacos Config。
```

本机启动前设置：

```powershell
$env:NACOS_PASSWORD="jianjiange"
```

---

## 5. 验证接口

启动 `post-service` 后验证：

```powershell
curl http://localhost:18084/internal/check/db
curl http://localhost:18084/internal/check/redis
curl http://localhost:18084/internal/check/minio
```

预期：

```text
database = ok
redis = ok
minio = ok
```
