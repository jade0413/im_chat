# 中间件与数据库选型对比 v1.0（2026-06-13 与 Jade 讨论定稿）

> 前提：生产 = 云服务器自装（compose），规模目标 1~5 万在线（D4）。
> 评判标准：运维成本 > 团队熟悉度 > 性能上限（万级规模下性能都不是瓶颈，运维事故才是）。

## 1. 主数据库：MySQL 8 ✅（维持）

| 候选 | 评价 | 结论 |
|---|---|---|
| **MySQL 8** | 团队最熟；MyBatis-Plus/Canal/国内资料生态最全；消息表场景（按 seq 区间查、主键写入）是它的舒适区 | ✅ 采用 |
| PostgreSQL | JSONB/原生分区/并发写更优雅，技术上略胜；但 MyBatis-Plus 适配一般、国内 IM 圈运维经验少，出事难找答案 | 无足够收益驱动切换 |
| MongoDB（OpenIM 同款） | 消息这类 schema 简单/写多读少的数据很适合；但为此多养一个有状态系统，万级规模不值 | 百万级时仅迁移消息表可再评估 |
| TiDB | MySQL 协议兼容的分布式方案 | 不是现在的选择，是**升级路径**（见下） |

**消息表增长预案**（自装环境必须想在前面）：日百万消息 ≈ 年 3.65 亿行。
- 第一道：保留策略归档（§13.5，租户套餐 90 天~1 年，归档到 MinIO）
- 第二道：按月 RANGE 分区（uk_conv_seq 查询模式兼容分区裁剪）
- 第三道（百万级在线才需要）：ShardingSphere 按 conv_id 分表，或整体迁 TiDB（协议兼容，应用近零改动）

## 2. 缓存/KV：Redis 7 ✅（维持）

- 路由表、在线状态、幂等 key、token_ver、workerId 租约在这里；消息 seq 主路径已按 D26 放回 MySQL 事务内——Redis 仍是**仅次于 MySQL 的关键单点**，自装环境配置底线：
  AOF everysec（compose 已开）+ 主从 + 哨兵（二阶段，MVP 单机可接受）
- Valkey（Redis 开源分叉）：协议全兼容，若未来介意 license 可无感平替；Dragonfly：性能强但生态太新，不赌
- License 说明：RSALv2 限制的是"卖 Redis 服务"，自部署使用完全无影响

## 3. 消息队列：RabbitMQ 3.13 ✅（维持，D8 补充论证）

| 候选 | 评价 | 结论 |
|---|---|---|
| **RabbitMQ** | 我们的推送模式 = 每网关实例一个动态队列（auto-delete）+ topic 按租户路由——这是 RabbitMQ 的**原生强项**；管理 UI 对排查友好；单机数万 msg/s 足够 | ✅ 采用 |
| RocketMQ | 国内成熟，事务消息理论上可替代 Outbox(D18)；但 NameServer+Broker 至少 3 个进程，自装运维明显更重，万级规模杀鸡用牛刀 | 不采用；Outbox 保留（应用层方案不绑 MQ） |
| Kafka | 吞吐与回放最强；但"每网关动态队列"模式与 partition 模型不契合（需自己做路由层），ZK/KRaft 运维重 | 百万级时按 D8 的 MQ 抽象层替换 |
| Redis Streams | 省一个组件诱人；但 PEL/消费组语义比 AMQP 弱，且把缓存与 MQ 故障域耦合（Redis 挂 = 路由+在线+幂等+MQ 全挂） | 否决 |
| NATS JetStream | 轻、快；但国内生产案例少、Java 生态一般 | 否决 |

## 4. 对象存储：MinIO ✅（维持，自装环境唯一合理解）

- 应用侧只依赖 **S3 兼容 API**（im-file-service 抽象 StorageProvider）：未来上云换 OSS/COS/S3 改配置即可
- 图片/语音的 CDN 加速二阶段再加（预签名 URL + CDN 回源）
- 备份：MVP 单机卷 + 定期 `mc mirror` 到备份盘；文件丢失不可恢复，这是自装环境最需要纪律的一项

## 5. 明确不引入的组件（防技术栈膨胀）

| 组件 | 为什么不 |
|---|---|
| Elasticsearch | 消息搜索二阶段才做，且可能选客户端 SQLite FTS（隐私+省服务器） |
| ClickHouse | usage 统计（§13.6）万级规模用 MySQL 按天聚合表足够 |
| etcd/ZooKeeper/Nacos | 模块化单体无服务发现需求；配置走 .env/application.yml；K8s 时代再说 |
| MongoDB | 见 §1 |

## 6. 自装环境 HA 底线（MVP → 二阶段）

| 组件 | MVP（单机 compose） | 二阶段 |
|---|---|---|
| MySQL | 每日全备(xtrabackup/dump) + binlog 7 天（compose 已配） | 主从半同步 + 故障手工切换预案 |
| Redis | AOF everysec | 主从 + 哨兵 |
| RabbitMQ | 持久化队列 + Outbox 兜底（投递失败消息不丢，在 outbox 里等重试） | 镜像队列/Quorum 队列 |
| MinIO | 定期 mirror 备份 | 4 节点纠删码 或 上云 OSS |

> 单机 MVP 的总内存预算参考：MySQL 2G + Redis 1G + RabbitMQ 1G + MinIO 0.5G + im-server 2G + gateway 0.5G ≈ 8G 云主机起步，16G 从容。
