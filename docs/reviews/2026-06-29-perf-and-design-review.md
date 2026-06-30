# IM 后端工程级评审：性能/可靠性 + 设计模型优化

> 审查日期：2026-06-29 ｜ 范围（用户指定）：im-server(Java 业务后端) + im-gateway-rust(网关)
> 关注重点：**代码级性能/可靠性** + **设计模型/架构优化**
> 性质：**纯审查，未改代码**。本轮在 2026-06-15 两份评审之上做"更深一层"的复审——
> 上轮的 P0/P1（部署配置、弱密钥、群计数丢更新、孤儿路由、访客频控、消息保留清理）经核对**均已落地**，本轮不再重复，
> 聚焦此前未触及的**热路径吞吐瓶颈**与**读写数据模型**。
> 工具链：沙箱无 Maven/cargo，结论经源码静态核对 + 调用点检索，落地前请本地 `mvn test` / `cargo test` 回归。

---

## 实施记录（2026-06-29，已改代码）

> 用户拍板"全部直接改码"。下列已落地到源码；**沙箱无 Maven/cargo 未能本地构建**，合并前务必
> `cd im-server && mvn -q test` 与 `cd im-gateway-rust && cargo test && cargo clippy` 回归。
> 配套测试已同步修改（见末列）。

### 已改（14 项；第二批 C-3/C-1b/D-5 于同日补齐）

| 编号 | 改动 | 主要文件 |
|---|---|---|
| D-1 | Outbox 提交后即时发布：poller 由 `Thread.sleep` 改信号量 `awaitInterval`（满批继续排空），`OutboxWriter` 写入后注册 `afterCommit` 唤醒 → 常态推送延迟从"一个轮询间隔"降到≈0，轮询退化为兜底 | `OutboxPoller.java`、`OutboxWriter.java` |
| D-2 | 推送扇出消费者并发化：`listener.simple.concurrency=4 / max-concurrency=16 / prefetch=32`（可环境变量覆盖） | `application.yml` |
| C-2 | seq 分配去掉回表 SELECT：`UPDATE … max_seq=LAST_INSERT_ID(max_seq+1)` + `SELECT LAST_INSERT_ID()`，锁内少一次热点行访问 | `ConversationSequenceMapper.java`、`ConversationSequenceService.java` |
| C-6 | 读回执去掉冗余回查：`Math.max` 直接推导 `effectiveReadSeq` | `ReadReceiptService.java` |
| C-7 | 网关 slow-consumer 断连 gRPC `tokio::spawn` 出消费循环，不阻塞下行管线 | `push.rs` |
| C-5 | 网关 `read_loop` 持有 handle，免每帧 DashMap 查找 + 克隆 ConnCtx | `connection.rs` |
| C-1a | `friend_required` 准静态配置加 30s 进程内缓存，C2C 发送不再每条读库 | `RelationService.java` |
| R-1 | `outbox.payload` 放大为 `MEDIUMBLOB`（V11），代码防御上限提到 1MB | `V11__outbox_payload_widen.sql`、`OutboxWriter.java` |
| D-4 | 会话列表改"取活跃会话（≤1000 兜底）→ 按 `last_msg_time` 内存排序 → 截断"，修正"按加入时间排+LIMIT 把最近活跃会话挤出首屏" | `ConversationService.java` |
| D-3 | `message` 聚簇主键迁移 `(tenant_id,conversation_id,seq)`、`id` 退为唯一键（V12，**含上线警示：大表须 gh-ost/pt-osc**） | `V12__message_clustered_pk.sql` |
| P3 | 每条推送日志 `info→debug`（网关 + Java 两处） | `push.rs`、`PushDispatchService.java` |
| C-3 | 会话 type 不可变缓存（无锁 `ConcurrentHashMap`，超 10w clear）：非 CS 会话扇出**跳过实时读会话行**，CS 仍实时读 cs_status/agent_id | `ConversationService.java` |
| C-1b | `(tenant:from:to)` 关系结果短 TTL（3s）缓存：已建会话发送不再逐条查黑名单/好友；代价是拉黑/好友变更 ≤3s 生效（IM 可接受） | `RelationService.java` |
| D-5 | C2C 列表项填充对端昵称/头像：**新实现** `UserRpc.GetUsers` 服务端 + conversation 侧 `UserProfileClient` 客户端 + `buildConvInfos` 批量取资料（缺失降级 peerUserId 文案，不阻断列表） | `UserRpcGrpcService.java`、`UserProfileClient.java`(新)、`ConversationService.java` |

测试同步：`ConversationSequenceServiceTest`（stub `selectAllocatedSeq`）、`OutboxWriterTest`（构造器 + 上限改 1MB）、`UserRpcGrpcServiceTest`（构造器加 `AuthService`）、`ConversationServiceTest`（加 `UserProfileClient` mock）、`ConversationServiceIntegrationTest`（`@MockBean UserProfileClient`）。

> D-5 设计取舍：服务端 `GetUsers` 此前 proto 有定义但未实现，本轮补上；列表路径批量取资料（一次 gRPC），单条 `getMemberConv` 路径暂不补（C2C 标题回退 peerUserId，客户端可用本地用户缓存补齐）。`AuthService.batchGetUsers` 原注释建议 ≤50，会话列表极端可达 ~1000（D-4 扫描上限），`IN(1000)` MySQL 可承受；如需更稳可在 `UserProfileClient` 分批，留作后续。

### 暂未改（仅 1 项，附技术理由）

- **C-4 网关下行 body 每 target clone**：复审确认 `prost` 生成的 `Frame.body` 是 `Vec<u8>`，且 `MSG_PUSH` 每连接 `req_id` 不同→帧本就各异，简单 `Arc/Bytes` **省不掉**最终每帧那次分配；真正有效的解法是按 `bytes="Bytes"` 重新生成 proto + 自定义编码路径，属构建层改动且**必须编译验证**，不宜在无 cargo 环境盲改（避免做无效或破坏 codegen 的改动）。建议放到能本地 `cargo build` 时单独做。

---

## 0. 总体结论

核心链路（握手→鉴权→发消息→seq 行锁同事务→Outbox→扇出→三段 ACK→增量同步）**成熟自洽，Rust 网关接近生产级**，与决策日志 D1–D42 高度一致。上轮已把"部署即崩/可被伪造/并发丢更新"这类**单点可用性**问题清掉。

本轮发现的问题**不影响功能正确性，但会在上量（接近 D4 的 1~5 万在线 + 活跃聊天）后成为吞吐与体验的天花板**，且多数是"改配置/合并 SQL/换数据结构"级别的优化，性价比高。最值得优先做的两件事：

1. **实时下行被 Outbox 100ms 轮询拉长**（D-1）——影响每条消息的端到端延迟，是体验最直接的优化点。
2. **msg.saved→推送扇出消费者是单线程**（D-2）——决定整个系统的实时推送吞吐上限。

二者都是低风险、可量化收益的改动。

---

## 一、设计模型 / 架构优化

### D-1 ★ 实时下行链路被 Outbox 轮询拉长（默认 +~50ms/条）— P1

- **现象**：`OutboxWriter.write` 只是 `INSERT ... status=PENDING`，**全链路没有任何"提交后立即发布"的快路径**（已全仓检索 `afterCommit/TransactionSynchronization`，确认无）。消息进 RabbitMQ 的唯一通道是 `OutboxPoller` 的轮询循环，间隔 `im.outbox.interval` **默认 100ms**（`application.yml:86`、`OutboxProperties:11`）。
- **影响**：每条消息从"落库成功"到"被推送"平均要等 ~50ms（最坏 100ms）才进 MQ，再叠加 MQ→push 消费→网关→客户端。对 IM 这是可感知的固定延迟，且发送方自己也要等这一跳才看到对端实时收到。
- **优化建议**（经典 outbox 演进，**保留可靠性不削弱**）：在发送事务里注册 `TransactionSynchronization.afterCommit`，提交成功后**尽力而为地立即触发一次发布/唤醒 poller**（直接 publish 该 event 或 `LockSupport.unpark` 轮询线程）。轮询从"唯一通道"退化为"失败/漏发兜底"。常态延迟≈0，崩溃/异常仍由 outbox 补齐。
- **代价/风险**：afterCommit 内发布失败要吞掉异常交给 poller 兜底（不能影响已提交事务）；幂等消费侧（`PushEventDeduplicator`、按 `server_msg_id`）已具备，重复发布安全。风险低。

### D-2 ★ msg.saved → 扇出消费者单线程，封顶整体推送吞吐 — P1

- **现象**：`MsgSavedEventConsumer`（`@RabbitListener`，push-service）是**整个系统实时推送扇出的入口**：每条消息在这里做 `getMembersResult`(gRPC) + 路由 MGET + 按网关分组 publish。但 `application.yml` 仅配了 `listener.simple.auto-startup`，**未配 concurrency/prefetch**（已检索确认），Spring AMQP 默认 `concurrentConsumers=1` → **全系统的消息扇出跑在一个消费线程上**。
- **影响**：单条扇出 = 1 次 gRPC + 1 次 Redis MGET(+可能成员查库) + N 次 publish，串行处理。活跃聊天下这一个线程就是吞吐瓶颈，与 D4 的万级在线目标不匹配。
- **优化建议**：为 push 消费者配置 `spring.rabbitmq.listener.simple.concurrency`（如 4~16）+ `max-concurrency` + 合理 `prefetch`。dedup + 幂等设计已使并发消费安全，无需改业务逻辑。moderation 消费者同理（异步、优先级低）。
- **风险**：低。注意并发后日志/指标按 tenant 聚合即可。

### D-3 message 聚簇主键与主读路径不一致，同步/历史拉取回表随机 IO — P2（架构级）

- **现象**：`message` 聚簇主键 = `id`(snowflake)，主读路径却是 `WHERE tenant_id=? AND conversation_id=? AND seq BETWEEN ?` 走二级唯一索引 `uk_conv_seq`（`01-schema.sql:105`）。二级索引命中后需按随机的 snowflake 主键**回表**取 `content` 等列。
- **影响**：离线增量补齐、历史翻页（IM 最高频的批量读）变成"二级索引区间扫 + 大量随机聚簇回表"，IO 局部性差；表越大越明显。
- **优化建议**：把聚簇主键改为 `(tenant_id, conversation_id, seq)`，`id` 退为二级 `UNIQUE KEY`。则同步读 = 顺序聚簇区间扫描，同一会话的消息物理相邻，回表消失。
- **代价/风险**：表结构演进，需迁移 + 同步正确性回归；跨会话插入会落在不同主键区间（每会话内 seq 单调、近似追加，可接受）。建议与"message 分区/retention"（上轮 V9 已补索引）一起排进阶段 3。

### D-4 会话列表读模型与"最近活跃"排序不匹配（潜在正确性问题）— P1

- **现象**：`ConversationService.listActiveMemberConvs`（`ConversationService.java:155`）按 `conversation_member.created_at DESC` + `LIMIT n` 取会话列表——即"按加入会话的时间"排序，而非 IM 应有的 `last_msg_time`。
- **影响**：不只是排序问题。当用户会话数 > limit（默认/上限 100）时，**首屏会漏掉"很久前加入但最近很活跃"的会话**（被 created_at 排序 + LIMIT 截断在外）。这是读模型缺陷，不是前端重排能补救的（数据根本没返回）。另：`conversation_member` 仅有 `idx_tenant_user`，按 created_at 排还会 filesort。
- **优化建议**（任一）：
  - (a) 把 `last_msg_time`/`last_seq` **反范式进 `conversation_member`**，建索引 `(tenant_id, user_id, last_msg_time DESC)`，列表按它排序分页（写消息时已在更新会话进度，可顺带更新成员行或异步刷）；
  - (b) 以 `conv_list_version` 流水（E1，protocol.md §7 已列为"最实质的洞"）作为列表真相，列表/置顶/免打扰都靠版本 diff 对齐。
- **风险**：(a) 增加写放大（每消息更新成员行），需权衡；(b) 改动面更大但与既定演进方向一致。建议先确认产品预期排序，再定方案。

### D-5 C2C 会话列表项无对端昵称/头像，标题是 userId 字符串 — P2

- **现象**：`buildConvInfo` 对 C2C 走 `defaultTitle` = `Long.toString(peerUserId)`（`ConversationService.java:528`），不带 peer 昵称/头像；只有 GROUP 会填 name/avatar。
- **影响**：客户端会话列表对单聊要么显示一串数字 ID，要么得二次批量查用户资料（客户端 N+1）。协议里消息 `Sender` 已冗余昵称/头像（protocol.md §4），但会话列表项没有对齐这一策略。
- **优化建议**：列表项对 C2C 反范式 peer 的 `nickname/avatar`（批量取，`buildConvInfos` 已有批量框架，加一次 user 批查即可），或明确约定由客户端本地用户缓存补齐并在文档记录。
- **风险**：低。

---

## 二、代码级性能 / 可靠性

### C-1 关系门禁在每条 C2C 消息都执行（gRPC + 2~3 次 DB）— P1

- **现象**：`MessageSendService.send` 对 `to_user_id` 新会话和 `conv_id` 已存在会话**都**调 `ensureCanSendC2c`（`MessageSendService.java:51` 与 `:76` 的 `ensureExistingConversationPolicy`）。每次进 `RelationService.check`：黑名单 count + `tenant_config.friend_required` 查库 + friend count（`RelationService.java:35-47`）。
- **影响**：对一个已建立的会话，每发一条消息都跨服务 gRPC + 2~3 次 DB 重复校验关系；`friend_required` 是准静态租户配置却逐条读库。高频聊天下纯属重复开销。
- **优化建议**：
  1. `friend_required` 等租户配置走本地缓存（短 TTL 或变更失效）——立即可做、零语义变化；
  2. 已建会话的关系门禁可只在**会话创建/首条**校验；黑名单的"中途拉黑"改在**接收端扇出过滤**（不向已拉黑发起方的用户投递）而非每条发送校验；或对 `(from,to)` 关系结果加短 TTL 缓存。
- **风险**：方案 2 改变"拉黑即时阻断发送"的语义（变为投递侧阻断），需产品确认；方案 1 无风险，建议先做。

### C-2 热点会话行每条消息 2×UPDATE + 1×SELECT（D26 行锁内）— P2

- **现象**：`MessagePersistService.persist` 在同一事务、同一 `conversation` 行上：`ConversationSequenceService.nextSeq` 先 `UPDATE max_seq=max_seq+1` 再 `SELECT max_seq`（`ConversationSequenceMapper`），随后 `ConversationProgressMapper.updateLastMessage` 又 `UPDATE last_msg_abstract/last_msg_time`（同一行）。即每条消息对最热的会话行 **2 次 UPDATE + 1 次回读 SELECT**，全部在 D26 行锁持有期内。
- **影响**：D26 已接受"单会话串行"权衡，但锁内多一条 UPDATE + 一次行 SELECT 直接拉长持锁时间，放大忙群/忙客服会话的串行瓶颈。
- **优化建议**：合并为一条自增+写摘要语句，并用连接本地变量回读 seq，避免二次访问该行：
  ```sql
  UPDATE conversation
     SET max_seq = LAST_INSERT_ID(max_seq + 1),
         last_msg_abstract = #{abstract},
         last_msg_time = #{time}
   WHERE id = #{convId};
  SELECT LAST_INSERT_ID();   -- 取回新 seq，连接本地、不再访问该行
  ```
  原 `updateLastMessage` 的 `AND max_seq=#{seq}` 防乱序保护在行锁串行下本就不会乱序，可安全并入。锁内从 3 次行操作降到 1 次 UPDATE，持锁时间显著缩短。
- **风险**：中。依赖 MySQL `LAST_INSERT_ID(expr)` 语义，需补并发用例验证 seq 无空洞/无重复（与 `ConversationSequenceServiceTest` 一起回归）。

### C-3 getMembersResult 对非 CS 会话也每条消息读一次会话行取 type — P2

- **现象**：扇出热路径 `MsgSavedEventConsumer` → `ConversationService.getMembersResult` 对**所有**会话都 `conversationMapper.selectById` 实时读会话行（为拿 CS 的 `cs_status/agent_id`），成员列表才走缓存（`ConversationService.java:108-122`）。但 C2C/GROUP 的 `type` 不可变，却仍每条消息读库。
- **优化建议**：把 `convType` 与成员列表一起进 `ConversationMemberCache`（同失效）；仅当 `type==CS` 时才实时读 `cs_status/agent_id`。非 CS 会话扇出可做到 0 次会话行查库。
- **风险**：低（缓存已有失效点：加人/踢人）。

### C-4 Rust 下行 body 每个 target 全量 clone — P2

- **现象**：`push.rs:120` 对 envelope 内每个 target 都 `envelope.body.clone()`（`Vec<u8>` 深拷贝）。大群单网关多在线连接时，同一 body 被拷贝 N 份，随后 `frame_codec::encode` 再各编码一次。
- **优化建议**：body 用 `bytes::Bytes` 或 `Arc<[u8]>` 承载，跨 target 共享只读，clone 仅增引用计数；帧封装持有 `Bytes` 即可零拷贝写出。
- **风险**：低（涉及 `Outbound::Frame` 与 `frame_codec` 的类型小改）。

### C-5 Rust 读循环每帧重新 DashMap 查找 + 克隆整个 ConnectionHandle — P2

- **现象**：`read_loop` 对每个 Ping/MsgRecvAck/dispatch 都 `state.registry.get(...)` 重新做并发哈希查找并 `clone()` 出整个 `ConnectionHandle`（内含 `ConnCtx` 5 个 `String` + 多个 `Arc`）（`connection.rs:601/633/694/722`）。dispatch 成功/失败分支还各查一次。
- **影响**：每条上行帧固定多若干次并发哈希查找 + 多次 String 堆分配，纯属热路径常量开销。
- **优化建议**：握手后将 `handle` 直接传入 `read_loop` 持有（它已在 `handle_socket_inner` 作用域内存在）；连接被踢/关闭由已有的 `close_rx` watch 通道感知即可，无需靠"查不到"来判活。可去掉绝大多数每帧查找与克隆。
- **风险**：低，但要确认移除"查不到即跳过"后，KICK/互踢仍能通过 watch 关闭读循环（当前 writer 收到 close 会关 socket → `receiver.next()` 结束，逻辑成立）。

### C-6 reportRead 读回执路径 3~4 次 DB（含一次冗余回查）— P2

- **现象**：`ReadReceiptService.reportRead`：`selectById(conv)` + `findMember` + 条件 `UPDATE` + **再 `findMember` 回查**取 effectiveReadSeq（`ReadReceiptService.java:36/45/60`）。
- **影响**：读回执是高频上行；每次 3~4 次 DB 往返偏重。
- **优化建议**：UPDATE 带 `.lt(readSeq, req)` 单调守卫，`updated>0` 时 effectiveReadSeq 即 `req.getReadSeq()`，否则取 `max(currentReadSeq, …)`——用 `Math.max` 直接推导，省掉回查（上轮已提，归并到本轮一起做）。
- **风险**：低。

### C-7 网关 slow-consumer 断连在下行消费循环内同步 await gRPC — P2

- **现象**：`push.rs` `disconnect_slow_consumer` 在 `handle_push_delivery` 内 `await state.rpc.on_disconnected(...)`，而该函数是下行消费循环串行调用的。一次慢消费者断连要阻塞整条下行管线一个 gRPC 往返。
- **优化建议**：把 `on_disconnected` 上报 `tokio::spawn` 出去（本地 `registry.remove` + `handle.close()` 仍同步，关键动作不受影响），与上轮 F7 对 ACK 的处理一致。
- **风险**：低。

### R-1 outbox payload 上限(16KB) 相对 content 上限(8KB) 余量偏小 — P2（可靠性）

- **现象**：`message.content` `VARBINARY(8192)` / 代码 `CONTENT_BYTES_LIMIT=8192`；`outbox.payload` `VARBINARY(16384)` / `OutboxWriter.MAX_PAYLOAD_BYTES=16384`。`MsgSavedEvent` 包了 `MsgPush`（content + 冗余 sender 昵称/头像 + envelope）。极端的 8KB CUSTOM 消息叠加冗余字段逼近 16KB 时，`OutboxWriter.validate` 会抛校验异常 → **整条发送事务回滚**（消息发不出去）。
- **优化建议**：放大 outbox payload 列（如 `MEDIUMBLOB`）留足余量，或显式收紧 content 上限并在协议文档标注"content + 冗余 ≤ outbox 上限"的约束关系。
- **风险**：低，属边界加固。

---

## 三、观测性 / 规范小项（P3）

- **日志量**：`PushDispatchService.pushToUsers`（`:80`）与 `push.rs` `handle_push_delivery`（`:147`）对**每条**推送打 `info` 级日志。万级吞吐下日志 IO 可观，建议降 `debug` 或采样。
- **trace 粒度**：网关每**连接**生成一个 `trace_id`（`connection.rs:482`），连接内所有消息共享同一 trace，无法区分单条消息链路。建议每条上行帧生成 request 级 trace（或复用客户端传入的 `req_id` 维度）串到 `dispatch`/`PushEnvelope.trace_id`。
- **重复代码**（上轮已列、仍在）：`OutboxEntity` 双份（common 与 message 各一）、`getMemberUserIds` 在 `ConversationService`/`ReadReceiptService` 各一份、`nullToZero/nullToBlank/toBool` 多处重写——建议下沉 common。
- **发送方自回显 MSG_PUSH 去重**：CLAUDE.md Open Question 已记录、protocol.md §3 已分析（`MsgSavedEvent.sender_conn_id` + `excludeConnId`），优先级低，规模内可接受。

---

## 四、值得肯定（保持，勿动）

- **Outbox 可靠性内核**：claim/release/owner/TTL + 死信 + 消费侧幂等 + 双 ID 幂等，至少一次 + 幂等组合正确（仅需补 D-1 的"提交后即时发布"快路径）。
- **发送事务边界干净**：gRPC（关系/会话解析）在事务**外**，`@Transactional persist` 内纯 DB，无"持 DB 行锁跨网络 RPC"的反模式。
- **Rust 网关**：背压（有界队列 + 满阈值断连）、ACK 超时判半死链断连、握手多级限流(全局+按 IP)、Origin 白名单、帧大小上限、AUTH 重放窗口、gRPC 超时**全部生效**（`rpc.rs` time::timeout 已接线）、draining 优雅下线。`onConnected` 原子 Lua 取旧值踢线（上轮 F5）已消除孤儿路由竞态。
- **租户隔离**：MyBatis `TenantLineHandler` 强制注入 + `outbox/tenant/sensitive_word` 正确进忽略表，poller 跨租户排空成立；`requiredTenantId()` 缺上下文即抛，背景任务（retention）用 `runWithTenant` 包裹。
- **推送扇出装配**：路由 MGET 批量、按网关分组、body 仅序列化一次复用（Java 侧）——结构正确，问题只在 D-2 的消费并发度与 C-4 的网关侧 body 拷贝。

---

## 五、优先级与落地清单

| 优先级 | 编号 | 项 | 涉及 | 改动类型 | 风险 | 验证 |
|---|---|---|---|---|---|---|
| P1 | D-1 | 事务提交后即时发布 + poller 退化为兜底 | common(outbox)+message | 代码 | 低 | 端到端延迟下降；崩溃注入仍补齐；重复发布幂等 |
| P1 | D-2 | push 消费者配 concurrency/prefetch | bootstrap 配置 | 配置 | 低 | 并发消费下吞吐上升、无重复气泡(幂等) |
| P1 | C-1 | friend_required 缓存（先做）+ 关系门禁策略评估 | user/message | 代码 | 低/中 | 每条 C2C 的 DB 次数下降；拉黑语义确认 |
| P1 | D-4 | 会话列表排序/分页读模型修正 | conversation(+schema) | 代码/架构 | 中 | 会话多于一屏时最近活跃不丢、按 last_msg_time 排 |
| P2 | C-2 | 会话行自增+写摘要合并为一条 SQL | common(seq)+message | 代码 | 中 | seq 并发无空洞/重复；持锁时间下降 |
| P2 | C-3 | convType 随成员列表一起缓存 | conversation/common | 代码 | 低 | 非 CS 扇出 0 次会话行查库 |
| P2 | C-4 | 网关下行 body 用 Bytes 共享 | gateway | 代码 | 低 | 大群单网关推送分配下降 |
| P2 | C-5 | read_loop 持有 handle 免每帧查找/克隆 | gateway | 代码 | 低 | 上行处理路径分配下降；KICK 仍生效 |
| P2 | C-6 | reportRead 去掉冗余回查(Math.max 推导) | conversation | 代码 | 低 | 读回执 DB 次数下降 |
| P2 | C-7 | slow-consumer 断连 spawn 出消费循环 | gateway | 代码 | 低 | 下行管线不被单次断连阻塞 |
| P2 | R-1 | outbox payload 列放大/约束文档化 | schema/common | 配置/迁移 | 低 | 近上限大消息可正常发送 |
| P2 | D-3 | message 聚簇主键改 (tenant,conv,seq) | schema/迁移 | 架构 | 高 | 同步/历史读 IO 局部性；迁移后同步正确 |
| P3 | — | 推送日志降级/采样、request 级 trace、重复代码下沉 | 多处 | 代码/规范 | 低 | 日志量、可追踪性、单测回归 |

### 建议节奏
- **阶段 A（本周，低风险高收益）**：D-1、D-2、C-1(仅缓存)、C-6、C-7、P3 日志降级。
- **阶段 B（需回归）**：C-2、C-3、C-4、C-5、R-1、C-1 关系门禁策略。
- **阶段 C（架构级，配合迁移与同步正确性验证）**：D-4 读模型、D-3 聚簇主键，与既有"message 分区/retention"一并规划。

---

## 六、待你确认

1. **会话列表预期排序**（D-4 决定方案）：当前后端按"加入时间"排并 LIMIT，是否应改为 `last_msg_time`？是否接受为此在 `conversation_member` 反范式 `last_msg_time`（写放大）？
2. **C2C 会话列表对端资料**（D-5）：列表项希望后端带 peer 昵称/头像，还是约定客户端本地缓存补齐？
3. **拉黑语义**（C-1 方案 2）：可否接受"中途拉黑"由投递侧过滤（而非每条发送实时校验），以换取已建会话发送链路免去逐条关系查库？
