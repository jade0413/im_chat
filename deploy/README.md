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
- 生产注意：TLS 在前置 nginx/SLB 卸载；中间件端口不暴露公网（§13.8）；.env 不入库

## k8s/（二阶段，空）
