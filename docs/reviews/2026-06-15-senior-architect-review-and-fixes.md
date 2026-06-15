# 资深架构师审查 + 修复报告（Rust 网关 / Java 业务后端）

> 审查日期：2026-06-15 ｜ 范围（用户指定）：im-gateway-rust + im-server(Java)
> 性质：**独立深审 + 直接改代码（含架构级）**。下方"已实施修复"均已落地到源码。
> 工具链限制：本环境仅有 JDK 11、无 Maven、无 cargo，**无法跑构建**。所有改动经人工静态核对
> 与调用点全量检索；合并前请在本地执行 `cd im-server && mvn -q test` 与 `cd im-gateway-rust && cargo test`。

---

## 0. 总体结论

核心链路（握手→鉴权→发消息→seq 行锁同事务→Outbox→推送扇出→三段 ACK→增量同步）成熟自洽，
Rust 网关基本是生产级。本轮聚焦"工业级别还差的最后一公里"：**部署即崩的配置、可被伪造的密钥、
并发丢更新、静默孤儿路由、读路径被同步 RPC 阻塞、消息表无限增长**。这些都是上量后才暴露、
但单点就能拖垮可用性的问题。

---

## 1. 已实施修复（代码已改）

### P0 / 安全

| # | 问题 | 文件 | 改动 | 风险 |
|---|------|------|------|------|
| F1 | 启动自检要求表 `outbox_event`，实际表名是 `outbox` → docker 自检开启时启动即失败 | `application.yml` | `required-tables` 改 `outbox` | 极低 |
| F2 | 仓库默认 RabbitMQ 指向公网 IP `103.45.65.84` + 弱口令 `admin/admin123` 进版本库 | `application.yml` | 默认改 `localhost` / `im` / 开发口令；并注明生产走 docker profile 注入 | 低 |
| F3 | **docker profile 未覆盖 `im.auth.jwt.secret`，生产部署会沿用弱常量密钥 `dev-only-change-me…` → JWT 可被伪造** | `application-docker.yml` | 注入 `${IM_AUTH_JWT_SECRET:${JWT_SECRET}}`，无默认值 → 未配置即 fail-fast | 低 |

> F2/F3 注意：公网 IP 与口令既然已进过 git 历史，应视为**已泄露**，请在服务端轮换 MQ 口令与 JWT 密钥。

### 并发 / 正确性

**F4 群人数 `member_count` 读改写丢更新（P1）** — `GroupService` + `GroupInfoMapper`
原实现 `selectById → setMemberCount(±n) → updateById`，同群并发加/退人会丢更新，并发下可越过人数上限。
改为成员变更路径走 `selectByIdForUpdate`（`SELECT … FOR UPDATE`，tenant_id 由租户拦截器注入）行锁，
把同一群的变更串行化 —— `member_count` 读改写一致、上限校验精确。群管理本就是低频操作，行锁开销可忽略。
（已同步更新 `GroupServiceTest`：变更用例改 stub `selectByIdForUpdate`。）

**F5 `onConnected` 先查后写的孤儿路由竞态（P2-2）** — `PushDispatchService` + `OnlineRouteRepository*`
原实现"先 `find` 判断踢旧，再 `save`"。同平台两连接并发登录时双方都读到空 → 都不踢 → 后写者静默覆盖
前写者的路由键，前一条连接变成"在线但收不到任何推送"的孤儿。新增**原子 Lua `SET_RETURNING_PREVIOUS`**
（取旧值并带 PX TTL 写新值），`onConnected` 改为"写入必然生效 + 拿回被顶替的旧路由再踢线"。
Redis 串行执行脚本，彻底消除竞态。（已更新 `PushDispatchServiceTest`。）

**F6 访客 widget 端点无频控、不校验租户（P1）** — `VisitorSessionService` + `VisitorSessionController` + 新 `TenantStatusMapper`
免鉴权的 `/api/v1/cs/widget/sessions` 每个新 `visitorToken` 都会在 `user` 表建访客用户，无频控可被刷量撑爆表；
`tenantId` 仅来自 `X-Tenant-Id` 头、不校验。新增：
- **固定窗口频控**（按 租户+来源 IP，30 次/分钟），用原子 Lua `INCR + 首次 PEXPIRE` 实现，避免两步间崩溃导致 key 永不过期变成永久封禁；
- **租户存在且 `status=1` 校验**（`tenant` 在租户拦截器忽略表内，按主键安全直查）；
- 控制器解析 `X-Forwarded-For`（反代后取真实 IP）传入。
超频返回 `RATE_LIMITED(429)`，非法/停用租户返回 `TENANT_DISABLED(403)`。

**F7 网关 ACK 处理阻塞读循环（P2-1）** — `im-gateway-rust/src/connection.rs`
每条 `MSG_RECV_ACK` 都同步 `await state.rpc.on_push_acked(...)`，让单连接读循环阻塞一个 gRPC 往返，
高吞吐下拖慢同连接后续上行帧；而 Java 侧 `onPushAcked` 目前是空实现。改为 `tokio::spawn` 尽力而为转达
（本地 `pending_acks.ack` 仍同步执行 —— 取消 ACK 超时这一关键动作不受影响）。线上送达回执落地（已读/已送达双勾）
留作后续功能，无需再动协议与网关。

### 架构级

**F8 `message` 单表无限增长 + 缺索引（P2-5 / 大表风险）** — 新增 Flyway `V9` + `MessageRetentionService` + `MessageRetentionMapper`
`message` 全租户共用单表、`created_at` 无索引、`tenant_config.msg_retention_days` 有列但无清理作业。新增：
- **`V9__message_retention_index.sql`**：`idx_tenant_created(tenant_id, created_at)`（清理扫描）+ `idx_tenant_sender(tenant_id, sender_id)`（审核/管理查询）；
- **`MessageRetentionService`**：沿用 `OutboxPoller` 的 `SmartLifecycle` + 虚拟线程后台循环（不引入 Spring `@Scheduled`），
  周期枚举活跃租户 → 每租户在 `TenantContext.runWithTenant` 内按各自保留天数分批 `DELETE`（走 `idx_tenant_created`）。
  **安全阀**：保留天数 `< 1` 视为误配跳过（绝不误删全库）；每租户每轮批次上限，避免长占 DB；单租户失败不影响他人与下一轮。
  配置 `im.message.retention.*`（默认关在代码、由组装应用 `application.yml` 打开，避免污染模块级测试）。

> 分区（按月 / 按会话哈希）属更高阶演进，需配合迁移与同步正确性验证，本轮只补索引与清理，未动表结构分区。

---

## 2. 发现但**未改**（建议 + 理由）

- **`client_msg_id` 幂等仅按 `(tenant_id, client_msg_id)`、不含 `sender_id`**（`uk_client_msg`）。
  同租户内若 B 复用 A 的 `client_msg_id`，`findExisting` 会把 A 的消息当作 B 的去重结果返回（轻度信息泄露 / 串号）。
  生产建议把唯一键收窄为 `(tenant_id, sender_id, client_msg_id)`；属 schema 变更，需评估存量数据与同步影响，未在本轮动。
- **网关 `config.rs` 默认 `rabbitmq_url` 内嵌口令、`allowed_origins` 默认 `*`**：仅本地默认值，生产务必用环境变量覆盖并显式配置白名单（P2-3）。
- **`token_ver` 仅存 Redis**：Redis 数据丢失会致全员被动重登；可考虑落库或在运维文档明确该故障语义（P2-4）。
- **访客 `visitorToken` 是无服务端密钥绑定的 bearer 凭证**：泄露可冒充该访客。UUIDv4 不可枚举，MVP 可接受；建议永不入日志、可选绑定 `device_fp`/过期（P2-6）。
- **会话列表排序**：`listActiveMemberConvs` 按 `member.created_at` 排而非 `last_msg_time`，确认是否预期由前端重排。
- **部署基线 `deploy/.../01-schema.sql` 未含 V9 索引**：该文件是 Flyway baseline(V1)，新库由 V9 迁移补齐；如直接以该脚本建库需手动同步两索引（deploy 不在本轮指定范围）。

---

## 3. 改动文件清单

```
im-gateway-rust/src/connection.rs                         F7
im-server/im-bootstrap/src/main/resources/application.yml          F1 F2 F8(config)
im-server/im-bootstrap/src/main/resources/application-docker.yml   F3
im-server/im-bootstrap/src/main/resources/db/migration/V9__message_retention_index.sql  F8 (新增)
im-server/im-group-service/.../dao/mapper/GroupInfoMapper.java     F4
im-server/im-group-service/.../service/GroupService.java           F4
im-server/im-group-service/.../test/.../GroupServiceTest.java      F4 (测试)
im-server/im-push-service/.../route/OnlineRouteRepository.java     F5
im-server/im-push-service/.../route/RedisOnlineRouteRepository.java F5
im-server/im-push-service/.../service/PushDispatchService.java     F5
im-server/im-push-service/.../test/.../PushDispatchServiceTest.java F5 (测试)
im-server/im-common/.../redis/RedisKeys.java                       F6
im-server/im-cs-service/.../visitor/dao/mapper/TenantStatusMapper.java  F6 (新增)
im-server/im-cs-service/.../visitor/service/VisitorSessionService.java  F6
im-server/im-cs-service/.../visitor/rest/VisitorSessionController.java  F6
im-server/im-message-service/.../dao/mapper/MessageRetentionMapper.java F8 (新增)
im-server/im-message-service/.../service/MessageRetentionService.java   F8 (新增)
```

---

## 4. 合并前验证清单（因本环境无构建工具，务必本地执行）

1. `cd im-server && mvn -q test` —— 重点回归 `GroupServiceTest`、`PushDispatchServiceTest`。
2. `cd im-gateway-rust && cargo test && cargo clippy` —— ACK spawn 改动。
3. 起 compose 验证 docker profile：**不设** `JWT_SECRET` 应启动失败（验证 F3 fail-fast）；设置后能完成 AUTH_ACK 并收发一条消息。
4. 并发用例：同群并发加/退人 → `member_count` 与成员行 `COUNT` 一致（F4）；同平台并发登录 → 旧连接被踢、无孤儿路由（F5）。
5. 频控用例：单 IP 超频建会话被 429、停用租户被 403（F6）。
6. 保留清理：插入早于 `cutoff` 的消息 → 一轮后被删、`msg_retention_days<1` 不删（F8）；确认 DELETE 走 `idx_tenant_created`（EXPLAIN）。
