# IM 项目架构与代码审查报告

> 审查日期：2026-06-15 ｜ 范围：im-gateway-rust / im-server(Java) / im-web / deploy / 数据库
> 性质：审查 + 已实施部分修复。下方"本轮已修复"列出已落地项（不再重复列入问题清单）。

---

## 本轮已修复（代码已改）

> 这些项已在本次会话中实现并通过类型检查/静态核对（Java 无 Maven 环境，未跑完整构建，建议合并前本地 `mvn test` 回归）。

- **会话列表/同步 N+1（批量化）** — `ConversationService`：`listActiveMemberConvs` 与增量事件路径改为批量取会话行、C2C 对端、群信息（`buildConvInfos`/`batchLoad*`），消除每会话逐条查库。单条路径 `toConvInfo` 重构为 `buildConvInfo`(不查库)+`loadGroupInfo`。
- **推送扇出成员列表缓存** — 新增 `com.im.common.conversation.ConversationMemberCache`（Redis，60s TTL，键 `conv:members:{tenant}:{conv}`）。`ConversationService.getMembersResult`/`getMemberUserIds` 走缓存；会话行（type/cs_status/agent_id）仍实时读以保证 CS 路由新鲜；`GroupService` 加人/踢人处显式失效。
- **网络层客户端存活探测** — `ImSocket.startHeartbeat` 增加看门狗：超过 2.5 个心跳周期无任何下行帧即判定半死链，主动断开并重连（`forceReconnect`）。
- **网络层断网恢复/可见性即时重连** — `ImSocket` 监听 `online` 与 `visibilitychange(visible)`，命中即重置退避并立即重连（`reconnectNow`）；已连接时改为主动探活。

---

## 一、项目整体结论

### 当前架构是否适合继续开发
**适合，且基础质量明显高于多数 MVP 阶段 IM 项目。** 模块化单体 + Rust 网关 + in-process gRPC + Outbox + 会话级 seq 收件箱模型成熟自洽，决策日志（D1–D39）与实现高度一致。

### 最大的问题（仍待处理）
**两个"部署即不可用"的配置错误**，会让 `docker compose --profile app up` 直接跑不起来（详见 P0）。这两个都是改一行的低风险修复，但不修就上不了线。

### 哪些部分可以保留
- Rust 网关连接生命周期（`connection.rs`）：握手限流、Origin 白名单、AUTH 超时、重放窗口、帧大小限制、空闲超时、发送队列满阈值断连、ACK 超时断连。
- 前端 `ImSocket.ts` 状态机：generation 失效保护、指数退避+抖动、pending 队列+TTL+重连补发、token 刷新单次防抖、KICK 处理（本轮又补齐了存活探测与断网恢复重连）。
- 消息可靠性链路：Outbox（claim/release/owner/TTL）+ 消费侧幂等 + 双 ID 幂等。
- `deleteIfCurrent` 路由清理；多端互踢（`token_ver` + `onConnected` 同类踢旧）。
- 鉴权与越权防护：REST 全局 JWT 拦截、消息历史/已读均校验会话成员、群管理校验 owner/admin（见第四节复核结论）。

### 哪些部分建议重构（剩余）
- 单会话 seq 行锁串行化（busy 大群写入上限）——见性能风险。
- `message` 单表无分区 + 缺 retention 清理作业——见数据库。
- 仓库内硬编码敏感默认值——见 P1-安全。

---

## 二、架构问题清单（剩余）

### P0 — 必须马上处理（影响上线/稳定性）

#### P0-1 nginx 把 WebSocket 反代到了错误的服务名和端口
- **位置**：`im-web/nginx.conf` → `location /ws { proxy_pass http://im-gateway-rust:9090; }`
- **说明**：compose 中网关服务名为 `im-gateway`、容器内监听 `8080`（`GW_WS_BIND: 0.0.0.0:8080`）；前端 `WS_URL` 默认 `ws(s)://当前host/ws`（`config.ts`）。
- **为什么是问题**：DNS 解析不到 `im-gateway-rust`、端口也错，compose 部署下 WS 永远连不上。
- **建议**：改为 `proxy_pass http://im-gateway:8080;`，并补 `proxy_read_timeout`（> 心跳*3，建议 ≥120s）。
- **风险**：极低。验证：起 compose 后浏览器能完成 AUTH_ACK。

#### P0-2 启动自检要求的表名与 schema 不一致，导致 im-server 启动失败
- **位置**：`application.yml` `im.startup-check.required-tables` 含 `outbox_event`；`01-schema.sql` 实际表为 `outbox`。
- **说明**：docker profile 默认开启自检，`StartupSelfCheckRunner.checkMysql()` 对缺失表抛异常。
- **建议**：把 `outbox_event` 改成 `outbox`。
- **风险**：极低。验证：自检通过日志 `mysql tables=[...]`。

### P1 — 建议优先处理

#### P1-安全：敏感默认配置硬编码进仓库
- **位置**：`application.yml`：`spring.rabbitmq.host` 默认 `103.45.65.84`、用户 `admin`/`admin123`；`im.auth.jwt.secret` 默认 `dev-only-change-me...`；`datasource.password` 默认 `123456`。
- **为什么是问题**：公网 IP + 弱口令进版本库属敏感泄露；弱默认密钥若误用到生产可被伪造 JWT。
- **建议**：生产 profile 移除默认值，缺失即 fail-fast；公网 IP/口令从仓库清除并轮换。
- **风险**：低。

#### P1-群计数：`group_info.member_count` 读改写存在并发漂移
- **位置**：`GroupService.addMembers`/`removeMember` 用 `group.getMemberCount() ± n` 后 `updateById`，无乐观锁/原子自增。
- **为什么是问题**：同一群并发加/退人时计数可能丢更新；`ensureWithinLimit` 基于该计数，极端情况下可越过人数上限。成员行本身是真值，计数仅缓存。
- **建议**：改原子 `UPDATE ... SET member_count = member_count + ?`（或加 version 乐观锁）；上限校验以成员行 `COUNT` 为准。
- **风险**：低。验证：并发加/退人计数一致用例。

#### P1-访客：widget 会话创建无频控、租户未校验
- **位置**：`VisitorSessionController.enterWidget`（免鉴权）→ `VisitorSessionService.enter`；tenantId 仅来自 `X-Tenant-Id` 头。
- **为什么是问题**：(1) 每个新 `visitorToken` 都会在 `user` 表创建访客用户，端点无频控/验证码 → 可被刷量撑大 user 表；(2) tenantId 不校验存在/启用，可在任意租户下创建访客用户。
- **建议**：对 `/sessions` 加按 IP/租户的频控；校验租户存在且 `status=正常`；可选按 widget 配置做来源域名校验。
- **风险**：低。验证：超频被拒、非法租户被拒用例。

### P2 — 后续优化

- **P2-1** `onPushAcked` 是空实现（`ConnEventGrpcService`），但网关对每条已送达消息都回调一次该 gRPC（`connection.rs`）——下行每条消息多一次无意义 gRPC。建议去掉调用或落地送达回执。
- **P2-2** `onConnected` 的 find-then-save 非原子（`PushDispatchService`），同平台同时登录存在竞态。可用 Lua/SET CAS 原子化。
- **P2-3** 网关 `allowed_origins` 默认 `*`（`config.rs`），生产务必显式配置。
- **P2-4** `token_ver` 仅存 Redis（虽开 AOF）；Redis 数据丢失会致全员被动重登。可考虑落库或文档化该风险。
- **P2-5** 缺消息保留/清理任务：`tenant_config.msg_retention_days` 有列但未见 purge 作业，`message` 单表会无限增长。
- **P2-6** 访客身份模型：`visitorToken` 是客户端生成、无服务端密钥绑定的 bearer 凭证，一旦泄露（共享设备/XSS/日志）可被签发 JWT 冒充该访客。UUIDv4 不可枚举，MVP 可接受；建议永不记日志，并考虑绑定 `device_fp`/设置过期。

---

## 三、代码冗余与重复逻辑（剩余）

- **OutboxEntity 双份**：`com.im.common.outbox.dao.entity.OutboxEntity` 与 `com.im.message.dao.entity.OutboxEntity` 重叠，建议统一到 common。
- **空值/类型小工具重复**：`nullToZero / nullToBlank / toBool` 在多个 service 各写一份，可下沉 `com.im.common`。
- **成员列表查询**：`ConversationService`（已抽出 `queryMemberUserIds`）、`ReadReceiptService.getMemberUserIds` 仍各有一份"按 conv 取未删成员"，可统一为公共仓储方法（并复用 `ConversationMemberCache`）。
- **`ConversationMemberClient` 接口在 message 与 push 模块各一份**：符合模块隔离铁律（可保留），实现体接近，可考虑下沉公共 gRPC stub 包装。
- **可简化**：`ReadReceiptService.reportRead` 更新后又回查一次 member，可用 `Math.max` 直接推导。

---

## 四、IM 核心链路审查

> ✅ 已实现 / ⚠️ 有缺口 / ❌ 未发现实现。

**用户连接建立** ✅：握手限流→Origin 校验→读首帧(AUTH 超时)→协议版本校验→`VerifyToken`→生成 conn_id→注册路由→`AUTH_ACK`。

**鉴权** ✅：网关侧校验 JWT 签名(HS256 常量时间比较)+签发方+类型+过期+租户+platform_class+`token_ver`(Redis)+用户存在/未封禁；REST 侧全局 `JwtAuthInterceptor` 拦 `/api/**`，放行 auth 与 cs/widget。

**私聊消息发送** ✅：幂等(DB 查→Redis 锁→DuplicateKey 兜底)→关系校验→会话 resolve→文件引用校验→`@Transactional` 持久化(seq 行锁自增 + message + conversation 进度 + outbox 同事务)→`MSG_SEND_ACK`。

**群聊消息发送** ✅：共用发送链路，按 `conversation_member` 扇出（成员列表本轮已加缓存）。⚠️ 单会话 seq 行锁串行化见第六节。

**离线消息** ✅：按 per-conversation `local_max_seq` 增量同步，无独立离线表。

**会话列表更新** ✅（本轮已批量化消 N+1）：用 `user_conv_event` 版本号增量；首次/落后过多回退全量。**仍需确认**：`listActiveMemberConvs` 按 `member.created_at` 排序而非 `last_msg_time`，列表"按最近活跃排序"实际依赖前端，确认是否预期。

**已读未读** ✅：校验成员→`read_seq` 单调更新(带 `lt` 防回退)→变更才推 `READ_NOTIFY`；群聊只回推自己，C2C 推对端。

**ACK / 重试 / 幂等** ✅：上行 pending 队列(TTL 60s)+重连补发+手动重试；网关→消息服务 gRPC 同步返 seq；下行 `need_ack` 用 `req_id` 跟踪，超时(10s)断连→重连 SYNC 补齐（不重推）。

**多端登录 / 连接状态** ✅：三平台类各限 1，同类踢旧，`deleteIfCurrent` 防误删，前端 KICK 停重连+提示。

**群权限（GroupService 复核结论）** ✅：`loadAuthorizedGroup` 要求操作者为成员且 `canManage`(owner/admin) 才能加人/踢人/改名；不可移除群主；`getGroup`/`getMembers` 要求成员身份。⚠️ 计数并发漂移见 P1-群计数；未见"退群/转让群主/解散群"（MVP 可接受）。

**❌ 未发现实现**：
- 送达回执落地（`onPushAcked` 为空，P2-1）。
- 消息保留/清理作业（P2-5）。
- presence/typing（E2，文档已标二阶段）。

---

## 五、数据库与缓存审查

**表结构/索引**：收件箱模型清晰；`message` 用 `uk_conv_seq`/`uk_client_msg`，`conversation_member` PK + `idx_tenant_user` 双向可走索引。建议补 `message(tenant_id, sender_id)`（审核/管理）。

**大表风险**：`message` 单表、所有租户共用、无分区——最需提前规划。建议按 `created_at`(月) 或 `conversation_id` 哈希分区 + 落实 `msg_retention_days` 清理作业。

**高并发写入**：单写路径合理；瓶颈在单会话串行（第六节）。

**Redis**：用途清晰（路由 TTL 180s、token_ver、消息幂等锁 30s、事件去重 24h、本轮新增成员缓存 60s）。`findAllByUsers` MGET 批量取路由。已开 AOF。

**MQ**：Outbox→RabbitMQ→按网关实例分队列 + 消费侧幂等。至少一次 + 幂等消费组合正确。

---

## 六、性能风险（剩余）

- **单会话 seq 串行**：`UPDATE conversation SET max_seq=max_seq+1` 行锁持有到事务提交，同会话消息串行写（D26 既定权衡）。busy 大群需关注；可演进 Redis seq 预分配。
- **会话列表排序**：`listActiveMemberConvs` 按 `created_at` 排，非 `last_msg_time`（确认是否预期）。
- **Redis 热 key**：大群成员路由集中在少量 key；`getOnlineAgentIds`（CS open 推全部在线坐席）坐席多时可能成热点。
- **MQ 入站压力**：下行每条消息多一次 `on_push_acked` 空 gRPC（P2-1）。

> 已修复：会话列表/同步 N+1、扇出成员每条查库（见"本轮已修复"）。

---

## 七、安全风险

- **未鉴权接口**：`/api/**` 全局拦截，仅放行 auth 与 cs/widget；widget 端点的频控/租户校验/凭证模型见 P1-访客、P2-6。
- **WebSocket token 校验** ✅；**越权读消息** ✅（`history`/已读均校验成员）；**群成员权限** ✅（owner/admin 校验）。
- **敏感配置泄露**：P1-安全。
- **日志敏感信息**：网关/前端日志未见 token/明文 body。
- **CORS**：Java 侧无配置，依赖 nginx 同源；网关 `allowed_origins` 默认 `*`（P2-3）。
- **SQL 注入**：MyBatis-Plus 参数化；`last("LIMIT " + n)` 中 n 为内部 int 且已夹取，无注入面。
- **Docker/env**：MinIO bucket 私有 + 预签名直传，正确；管理端口(15672/9001/3000)直映宿主，生产应限内网。

---

## 八、建议重构路线（剩余）

### 阶段 1：低风险（不改业务逻辑）
- 修 P0-1（nginx /ws）、P0-2（required-tables）；清理 P1-安全 敏感默认值并 fail-fast。
- **涉及**：`im-web/nginx.conf`、`application*.yml`。**验证**：compose 起栈→AUTH_ACK→收发一条消息。

### 阶段 2：中等风险（需回归）
- P1-群计数 原子化；P1-访客 频控+租户校验；去掉 P2-1 空 gRPC；统一重复 OutboxEntity/成员查询/空值工具。
- **涉及**：`GroupService`、`VisitorSessionController/Service`、`ConnEventGrpcService`+`connection.rs`、common。

### 阶段 3：架构级（动数据结构/边界）
- `message` 分区 + retention 清理；评估 seq Redis 预分配；`token_ver` 持久化定稿。
- **涉及**：schema/Flyway、`SequenceService`、`TokenVersionService`。**验证**：分区前后同步正确性 + seq 并发用例。

---

## 九、可执行任务清单（剩余）

| 优先级 | 任务 | 涉及模块 | 修改类型 | 风险 | 验证方式 |
| --- | --- | --- | --- | --- | --- |
| P0 | nginx `/ws` 改指 `im-gateway:8080` + 补长连接超时 | im-web (nginx) | 配置 | 极低 | compose 起栈后完成 AUTH_ACK |
| P0 | `required-tables` 的 `outbox_event` 改为 `outbox` | im-bootstrap 配置 | 配置 | 极低 | 启动自检日志通过 |
| P1 | 清理仓库内公网 IP/弱口令/默认密钥，生产 fail-fast | im-bootstrap 配置 | 配置/安全 | 低 | 缺失关键配置启动报错；密钥已轮换 |
| P1 | `group_info.member_count` 改原子自增/乐观锁 | group | 代码 | 低 | 并发加/退人计数一致用例 |
| P1 | widget `/sessions` 频控 + 租户存在/启用校验 | cs-service | 代码 | 低 | 超频/非法租户被拒用例 |
| P2 | 去掉或落地 `onPushAcked` 空 gRPC 回调 | push/gateway | 代码 | 低 | 下行 gRPC 调用量下降 |
| P2 | `onConnected` find-then-save 原子化 | push | 代码 | 低 | 同平台并发登录踢旧不丢 |
| P2 | 网关 `allowed_origins` 生产显式收紧 | gateway 配置 | 配置 | 低 | 非白名单 Origin 被拒 |
| P2 | message 分区 + retention 清理作业 | DB/迁移 | 架构 | 高 | 分区后同步正确 + 过期清理生效 |
| P2 | 统一重复 OutboxEntity / 成员查询 / 空值工具 | common 等 | 代码 | 低 | 单测 + 全量回归 |
| P2 | 访客 visitorToken 不入日志 + 评估绑定 device_fp/过期 | cs-service | 代码/规范 | 低 | 日志审计 + 冒充用例 |

---

## 十、待你确认的信息

1. **会话列表预期排序**：后端按 `created_at` 排，是否由前端按 `last_msg_time` 重排？确认后判定是否为 bug。
2. **生产 TLS 卸载**：是否有 nginx/SLB 在网关前卸载 TLS（compose 注释提到，目前未确认）。影响 wss 与超时配置建议——确认后我再给出网关/前端的对应设置。
