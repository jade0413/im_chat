# PR-2 审查报告（两个提交分别审查）

审查人：Claude｜日期：2026-06-13
- **PR-A** `38e6708` feat: implement push routing and token versioning（Java 侧）
- **PR-B** `c258266` feat: implement rust gateway foundation（Rust 网关）

---

# PR-A（38e6708 push/token_ver）：✅ 通过（附 3 条建议）

## 验证清单（checklist §5 + D11/D27/D28）

| 项 | 验证结果 |
|---|---|
| 互踢三步：登录按平台类 token_ver++ → 旧 token 立即失效 → OnConnected 发现同平台异连接发 KICK | ✅ 完整且顺序正确（D27 落地准确）：AuthService 登录携 platform 递增版本入 JWT；TokenVerifier.ensureCurrent 严格校验；refresh 不递增但校验当前版本 |
| 心跳重放 OnConnected 不误踢 | ✅ sameConnection 过滤——细节意识好 |
| 推送按网关实例分组批量投递（禁止逐用户投） | ✅ publishGrouped 按 gw_instance 聚合成单 PushEnvelope |
| OnDisconnected 幂等保护 | ✅ deleteIfCurrent：旧连接断开不会误删新连接路由 |
| 路由 TTL 与心跳匹配 | ✅ routeTtl 配置 + 心跳续期（经网关 on_connected，见 PR-B B1 的优化建议） |
| MQ 消费幂等 | ✅ MsgSavedEventConsumer 按 event_id 去重（PushEventDeduplicator） |
| KickUser（管理员/封号路径） | ✅ 先 token_ver++ 再踢路由，离线也能使 token 失效 |

## 建议（不阻塞）

- **P1**. pushToUsers 对每个 userId 单独一次 Redis findAll——500 人群聊 = 每条消息 500 次 Redis 往返。
  下一 PR 改 pipeline/批量 MGET（群聊 PR 之前必须解决，否则群消息延迟会被 Redis RTT 支配）。
- **P2**. token_ver 严格相等校验依赖 Redis 持久性：若 Redis 数据丢失（灾难恢复），current=0 → 全员 token 失效需重新登录。
  行为可接受，但要写进运维手册（Redis 恢复后的预期现象，避免误判为故障）。
- **P3**. TokenVersionService/PlatformClass 放在 im-common——属基础设施可接受，但 im-common"禁业务逻辑"的边界请在类注释里说明理由，防止先例被滥用。
- PR-1 遗留 L1（im-common 绑定 enforcer）本次仍未补，继续挂账。

---

# PR-B（c258266 Rust 网关）：❌ 打回（架构正确，生产成熟度不足）

## 一、直接回答 Jade 的三个问题

**"是不是新手代码？"——不是。** 并发模型是地道的 tokio 风格（每连接 reader 循环 + 独立 writer task + mpsc 解耦，
ConnectionHandle 克隆共享，DashMap 注册表）；无 unwrap/panic 滥用；错误路径都有响应帧；
KICK 先发帧后 close 靠队列顺序天然保证——这些是有经验的写法。

**"是否符合设计思想？"——架构层完全符合：**

| 设计要求 | 实现 | |
|---|---|---|
| D19 冻结面：只编译 frame.proto + gateway.proto | build.rs 精确两个文件，业务帧 bytes 透传 Dispatch | ✅ |
| 网关零业务（核心约定 2） | 无任何业务判断，仅用连接层 4 个错误码 | ✅ |
| AUTH 5s 限时 + 防重放 ±5min + 版本协商 KICK(PROTO_TOO_OLD) | 全实现，含单测 | ✅ |
| D28 need_ack：req_id 跟踪 + 10s 超时→死链断连，不重推 | PendingAcks + 超时任务，断连+清路由+上报 | ✅ |
| 断连清理链路 | pending_acks.cancel + registry.remove + OnDisconnected | ✅ |
| 每实例动态队列消费 PushEnvelope，KICK 推后即断 | push.rs 与 Java 投递端参数语义一致 | ✅ |

**"是否健全？"——不健全。** 缺的是同一类东西：**超时、重连、背压、可观测性**——
实验室能跑、线上必出事的"生产成熟度"层。这是本次打回的全部原因，见 R1~R5。

## 二、严重（必须整改，附验收标准）

**R1. 服务端无 idle 超时——半死链永不清理（最重要）。**
read_loop 对静默连接无超时：死链的 ConnMap 条目、writer task 永久泄漏；need_ack 判定只覆盖"恰好有推送"的连接。
AuthResp 下发了 heartbeat_interval 却不在服务端执行。
整改：`tokio::time::timeout(heartbeat_interval * 3, receiver.next())`，超时→断连清理。
验收：模拟静默连接，90s 内 registry 条目消失。

**R2. dispatch 无超时 + 失败即断连——重连风暴放大器。**
(a) 无 deadline：Java 慢一帧卡死整条连接读循环（连 PING 都停）；
(b) Java 重启瞬间所有活跃发送的连接被 `?` 断掉——上游抖动放大成全量重连风暴（§13.3 反目标）。
整改：全部 gRPC 调用加超时（dispatch 10s/verify 5s）；dispatch 失败回 ERROR 帧保持连接。
验收：杀 im-server 5s 再拉起，已连客户端不断线，期间发送收到 ERROR 帧。

**R3. push 消费者无重连——MQ 闪断后网关永久失去下行能力。**
run_push_consumer 返回即结束，无重试，网关沦为"只收不推"僵尸实例。
整改：外层 loop + 指数退避（1s..30s）；消费异常记日志不退出。
验收：重启 RabbitMQ 容器，30s 内推送恢复。

**R4. Outbound 无界队列——慢消费者无背压。**
客户端收不动时 outbound 无限增长，单连接可拖爆内存——恰好违背"Rust 网关控内存"的立项理由（D1）。
整改：bounded(256) + try_send，满即判慢消费者=半死链断连（与 R1 同处置路径）。
验收：压测不读数据的客户端，网关内存平稳、该连接被主动断开。

**R5. metrics 缺失。**
§13.4 是 MVP 要求，prometheus.yml 已配抓取 im-gateway:8080 却抓不到任何东西。
整改：/metrics 端点，首批四指标：在线连接数(按 tenant)、上行帧速率(按 cmd)、推送送达/失败数、ack 超时断连数。
验收：compose --profile obs 后 Grafana 可见网关连接数曲线。

## 三、建议（不阻塞，B1/B2 强烈建议随 R1/R2 顺手做）

- B1. PING→on_connected 做路由续期：语义混用 + 每连接 30s 一次 gRPC+Redis 写（5 万在线 ≈ 1.7k QPS）。
  建议每 3 次心跳续 1 次（TTL 三倍冗余足够），或加专用轻量 RenewRoute 接口。
- B2. 握手限流缺失（§13.3 令牌桶/实例）——重连风暴第一道闸门。
- B3. Web 平台 Origin 校验缺失（§13.8）。
- B4. trace_id 整条连接复用一个，建议每上行帧派生 `{conn_trace}-{n}`，否则日志无法区分帧。
- B5. PendingAcks 每条 need_ack 推送 spawn 一个 sleep task——万级可接受，高峰需换 DelayQueue/时间轮。
- B6. 优雅停机不广播 KICK、不 drain（发版硬断靠客户端自愈）——二阶段补。
- B7. push 投默认交换机，网关掉线时 unroutable 静默丢（SYNC 兜底可接受）——建议 mandatory + ReturnCallback 计数告警。
- B8. 硬编码错误码 1001/1005/9999 是 D19 设计使然，但需注释标注与 common/error.proto 的对应关系。

---

# 总结论

- **PR-A（38e6708）通过**，P1（推送路由批量查询）须在群聊 PR 前解决。
- **PR-B（c258266）打回**：R1~R5 全部集中在网关，预估 200~300 行改动，不动协议不动 Java。
  复审门槛：R1~R5 闭环 + 验收标准可演示 + `cargo clippy -- -D warnings` 干净。
