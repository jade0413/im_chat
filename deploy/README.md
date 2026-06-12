# deploy

## docker-compose/（MVP）

```bash
cd docker-compose
cp .env.example .env          # 改密码
docker compose up -d                          # 仅中间件（日常开发模式）
docker compose --profile app up -d --build    # 含 gateway/im-server/im-web（镜像就绪后）
docker compose --profile obs up -d            # 观测栈 Prometheus/Grafana/Loki（§13.4）
```

- MySQL 首启自动执行 `init/mysql/01-schema.sql`（15 张表 + dev 租户种子）；后续表演进走 Flyway，不再改该文件
- MinIO 由 minio-init 自动建私有 bucket `im-media`（D10 预签名直传）
- RabbitMQ 管理台 :15672，MinIO 控制台 :9001，Grafana :3000
- `--profile app` 会构建 `im-server`、`im-gateway`、`im-web` 三个镜像；构建上下文是仓库根目录，因为 server/gateway 都需要读取同级 `im-proto`
- `im-server` 的 docker profile 默认开启启动自检：MySQL 表、Redis、RabbitMQ exchange、MinIO bucket、workerId 租约任一失败都会 fail-fast
- `im-gateway` 默认启用 Origin 白名单和实例级握手限流；生产务必把 `IM_GATEWAY_ALLOWED_ORIGINS` 改成真实 Web 域名
- 生产注意：TLS 在前置 nginx/SLB 卸载；中间件端口不暴露公网（§13.8）；.env 不入库
- Redis 中的 `token_ver:{tenant}:{user}:{platform_class}` 是登录态版本。若 Redis 灾难恢复后丢失该类 key，已签发 token 会因 current=0 被判无效，用户需要重新登录；这是预期的安全降级，不是鉴权服务故障。

## k8s/（二阶段，空）
