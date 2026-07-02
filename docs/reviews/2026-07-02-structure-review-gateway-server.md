# 工程结构与设计深度审查：im-gateway-rust + im-server

> 日期：2026-07-02 ｜ 审查人：Claude（资深架构师视角）
> 范围：im-gateway-rust 全部源码（~2000 行）、im-server 推送链路/Outbox/路由表/uplink 路由关键路径
> 前提：已知悉 CLAUDE.md 决策日志 D1~D42 与既往审查（PR-1/PR-2/PR-3、2026-06-29 perf review），本文不重复已修复项（C-5/C-7/P3/D-1 等）
>
> **实施状态（2026-07-02，D43）**：R1+J1 / R2 / R3 / R5 已由 Jade 拍板并当日实施完毕（含 3 条连接状态机测试 + 2 条 pending ack 单测）；⚠️ 待在有 Rust 工具链环境跑 `cargo test`（axum 0.8 需重拉依赖）与 Java 编译。R4 / R6 / R7 / J2 未实施，仍按 §4 优先级排队。

## 0. 总评

| 维度 | 评分 | 一句话结论 |
|------|------|-----------|
| 架构合理性 | ★★★★☆ | 边界干净，网关确实零业务；扣分点：网关→Java 单通道单地址，Java 扩容路径未打通 |
| 速度与性能 | ★★★☆☆ | 无阻塞运行时、无显著锁竞争；扣分点：扇出路径 O(N) 拷贝 + 每 ack 一个 spawn 任务 |
| 抽象与解耦 | ★★★☆☆ | 传输/编解码/业务三层分离到位；扣分点：Rust 侧零 Trait，核心逻辑不可脱离 tonic/lapin 测试 |
| 冗余与规范 | ★★★★☆ | proto 单一事实来源做得好，无 DTO 重复；扣分点：错误码/ErrorBody 在 Rust 手写镜像有漂移风险 |

**服务边界结论（回答问题 1）**：`im-server` 与 `im-gateway-rust` 职责划分**不存在越权**。抽查确认：网关不解析任何业务 body（D19 落实到位，`GatewayErrorBody` 是唯一例外且有注释说明）；Java 侧不管理连接生命周期，只维护 Redis 路由表。三段 ACK、踢线、慢消费者断连的职责归属都正确。这是本项目最值得保持的资产。

---

## 1. 痛点清单（Pain Points）

严重度：🔴 应尽快修 ｜ 🟡 计划内优化 ｜ 🟢 记录在案，规模上来再动

### Rust 网关（im-gateway-rust）

#### R1 🔴 扇出路径 O(N) 内存拷贝 + O(N) 重复编码

`push.rs handle_push_delivery`：

```rust
handle.send_push(envelope.cmd, envelope.body.clone(), ...)  // 每个 target 完整拷贝 body
```

且 `send_push` 内部对每个 target 各自构造 `Frame` 并在 writer 里 `frame_codec::encode` 一次。500 人群、300 人在线同网关的场景：**一条消息 = 300 次 body 深拷贝 + 300 次 protobuf 编码**，而 `need_ack=false` 时（req_id 恒为 0）这 300 帧字节完全相同。`need_ack=true` 时帧因 req_id 不同无法共享，但 body 部分仍可零拷贝。

修法见 §3.1：prost `Bytes` + 广播帧预编码一次。注意 axum 0.7 的 `Message::Binary(Vec<u8>)` 会在 socket 边界强制最后一次拷贝，**升级 axum 0.8（`Message::Binary(Bytes)`）才能打通全链路零拷贝**。

#### R2 🔴 PendingAcks：每条 need_ack 推送 spawn 一个定时任务 + 断连 O(全表) retain

`connection.rs PendingAcks::track`：每个 need_ack target `tokio::spawn` 一个 `sleep(10s)` 任务。万人在线高峰期，这是**短时数万个 timer 任务**的分配/调度开销。更痛的是 `cancel_connection`：

```rust
self.inner.retain(|key, _| &key.conn != conn);  // 遍历全局所有 pending，锁全部分片
```

连接风暴（网络抖动批量重连）时，每个断连都全表扫描一次 → O(断连数 × 全局 pending 数)。

根因是 pending ack 本质是**连接局部状态**却放在了全局 map。修法见 §3.2：把 pending 移进 `ConnectionHandle`，随连接销毁自然消失，全局索引和 retain 都不再需要；定时用单个 sweeper（`DelayQueue` 或复用 writer 的 select tick），不 per-push spawn。

#### R3 🔴 网关→Java 单 TCP 通道、单上游地址

`rpc.rs`：

```rust
let channel = Channel::from_shared(upstream_grpc)?.connect().await?;
```

所有 verify/dispatch/conn_event 复用**一条 HTTP/2 连接到一个地址**。三个问题：
1. **吞吐上限**：单 TCP 连接受拥塞窗口与 HTTP/2 流控限制，万级在线的 uplink 全走一条连接；
2. **TCP 层队头阻塞**：一个丢包重传拖慢所有复用流；
3. **Java 扩容死路**：D4/D5 说"架构预留水平扩展点"，但 im-bootstrap 起第二个实例时网关无法感知——除非前置 L4 LB，而单连接打 LB 也只会落到一台后端。

修法见 §3.3：`Channel::balance_list` 多端点 + 或至少同址 N 条连接轮询。这是**唯一一个和"水平扩容"承诺直接冲突**的实现点，建议在扩容需求到来前修掉，改动极小。

#### R4 🟡 read_loop 队头阻塞：dispatch 内联 await，慢上游拖死同连接 PING

`connection.rs read_loop`：业务帧 `state.rpc.dispatch(...).await` 内联等待（有 dispatch_timeout 兜底）。这是有意的**单连接内帧序保证**，方向没错；但代价是上游慢时（如 DB 抖动打满 timeout），同连接后续帧包括 PING 全部排队，最坏情况 `dispatch_timeout × 连续慢帧数 > idle_timeout` 会误判半死链断连。

MVP 可接受，但建议两个低成本缓解之一：a) PING/ACK 在 await dispatch 之前优先处理（需要小规模重排循环，改成先 `try_next` 排空控制帧）；b) 或明确约束 `dispatch_timeout × 2 < heartbeat_interval`，把不变量写进 config 校验。

#### R5 🟡 零 Trait 抽象：核心逻辑焊死在 tonic/lapin/axum 上

现状：`RpcClients` 是具体 struct、push 消费循环直接依赖 lapin、`read_loop` 签名绑定 `SplitStream<WebSocket>`。后果不是"以后换不了组件"（换 MQ 的概率确实低），而是**当下测试盲区**：现有测试全部是纯函数单测（backoff/limiter/codec），read_loop 的认证流程、踢线、ack、慢消费者断连——网关最核心的状态机——没有任何一条测试覆盖，因为无法注入假上游。

修法见 §3.4：一个 `Upstream` trait + 泛型化 read_loop 的 Sink/Stream（`send_auth_resp` 已经是泛型 Sink 写法，说明作者会这套，只差推广到主循环）。lapin 侧收益低，可不动。

#### R6 🟢 手写镜像常量的漂移风险

`connection.rs` 头部 `TOKEN_INVALID: i32 = 1001` 等注释"mirrored from im-common ErrorCode"；`frame_codec.rs GatewayErrorBody` 手写镜像 `body/messages.proto ErrorBody`。D19 决定网关不编译业务 proto，方向对，但错误码这类**连接层语义**不该属于"业务 proto"。建议把连接层错误码枚举 + ErrorBody 挪进 `common/error.proto`（网关本就编译 common），两端生成，消灭手抄。

#### R7 🟢 若干小项

- `ConnKey.conn_id: String`：每次路由查找都 clone + hash 一个 36 字节字符串。conn_id 是自产 UUID，可在网关内部解析为 `u128`（`Uuid::as_u128`），Key 变 Copy 类型。收益中等、改动局部。
- `IpHandshakeLimiter::allow` 每次握手都 `cleanup_idle()` 全表 retain——握手限速本身挡住了频率，但仍建议加时间闸门（如 30s 才清一次）。
- push 消费循环逐条 `delivery.ack().await`：prefetch 已缓解，可改 `multiple ack` 批量确认再涨一档吞吐，优先级低。
- 优雅停机 `sleep(drain_timeout)` 无条件睡满：可改为轮询 registry.len()==0 提前退出。

### Java 侧（im-server）

#### J1 🟡 PushEnvelope 构造中的 `ByteString.copyFrom(body)`

`PushDispatchService.publishGrouped`：每个网关分组都 `copyFrom` 一次 body。用 `UnsafeByteOperations.unsafeWrap(body)`（调用方不再改 body 的前提成立：body 来自 event 解码后只读）省掉 N_gateway 次拷贝。小改动，配合 R1 是同一条链路的两端。

#### J2 🟡 Java 侧水平扩容的对偶问题

网关侧 R3 修掉后，Java 侧还差一件事：`ConnEvent/Uplink/GatewayAuth` 三个 gRPC 服务是无状态的（状态在 Redis/MySQL），**天然可多实例**，但需要确认 RabbitMQ 消费者（MsgSavedEventConsumer 等）多实例竞争消费的幂等已覆盖——抽查见 `RedisConsumerIdempotency` 存在，方向正确。建议在 docs/architecture.md 把"Java 扩到 2 实例的操作清单"（LB/网关多端点/消费者组）写成一节，避免真要扩时现场摸索。

#### J3 🟢 结构性优点（记录，防止未来劣化）

- `CmdHandlerRegistry`：cmd→handler 注册表 + 重复注册 fail-fast，Java 侧 uplink 解耦的正确形态；
- 路由表 `findAllByUsers` 用 MGET 批量（无 N+1）、写用 Lua 原子脚本（无 check-then-act 竞态）；
- Outbox poller：信号唤醒 + 满批连续排空 + 虚拟线程，实现质量高；
- per-gateway-instance 队列：网关水平扩容的正确拓扑，**网关加机器完全不需要动 Java**。

#### J4 冗余审计结论（回答问题 4）

跨 Rust/Java 的模型重复：**基本没有**。proto（im-proto）是唯一事实来源，双端生成；心跳只在网关实现（Java 无重复）；加解密无重复（JWT 校验只在 Java，网关调 RPC）。唯二的手抄镜像就是 R6 的错误码与 ErrorBody。

**是否要抽 `im-common` 基础库？**——Java 侧**已经有了**（im-common：tenant/outbox/mq/redis/uplink/error，划分合理）。跨语言的 "im-common" 不存在也不应存在：Rust 与 Java 能共享的只有 proto 契约，正确动作不是建新库，而是**把 R6 的镜像常量上收进 im-proto**。若未来出现第二个 Rust 服务，再从网关拆 `gateway-core`（codec/limiter/registry）crate，现在不必。

---

## 2. 优化架构图思路

现拓扑（正确，保持）：

```
Client ──WS──> im-gateway-rust ──gRPC──> im-bootstrap(Java 单体)
                     ▲                        │
                     │ per-instance queue     │ Outbox→RabbitMQ
                     └────── RabbitMQ <───────┘
                                 (push.envelope)
```

扩容形态（本次审查建议打通的路）：

```
Client ──WS──> gw-1..gw-N ──gRPC(balance_list)──> java-1..java-M (无状态,LB 或多端点直连)
                  ▲                                   │
                  │ queue: gw.push.{instance_id}      │ 竞争消费(幂等已备)
                  └──────────── RabbitMQ <────────────┘
```

关键点：网关扩容已就绪（per-instance 队列 + Redis 路由表带 gw_instance）；缺口只在 gRPC 单通道（R3）。数据层扩容按 D24 既定路径，不在本次范围。

---

## 3. Rust 重构示例

### 3.1 扇出零拷贝（修 R1）

```toml
# Cargo.toml：axum 0.7 → 0.8（ws Message::Binary 从 Vec<u8> 变 Bytes）
axum = { version = "0.8", features = ["ws"] }
bytes = "1"
```

```rust
// build.rs：让 prost 把 bytes 字段生成为 bytes::Bytes（引用计数，clone 零拷贝）
tonic_build::configure()
    .bytes(["."])
    .compile_protos(&protos, &includes)?;
```

```rust
// connection.rs：Outbound 增加预编码变体
enum Outbound {
    Frame(Frame),      // need_ack 路径：req_id 不同，逐帧编码
    Encoded(Bytes),    // 广播路径：预编码一次，所有 target 共享
}

impl ConnectionHandle {
    pub fn send_encoded(&self, frame_bytes: Bytes) -> FrameSendResult {
        self.try_send(Outbound::Encoded(frame_bytes))  // Bytes::clone = 引用计数+1
    }
}

// writer task
Outbound::Encoded(bytes) => ws_sender.send(Message::Binary(bytes)).await, // axum 0.8 直收 Bytes
Outbound::Frame(frame)   => ws_sender.send(Message::Binary(frame_codec::encode(&frame).into())).await,
```

```rust
// push.rs：need_ack=false 时整只信封只编码一次
let shared_frame: Option<Bytes> = (!envelope.need_ack).then(|| {
    frame_codec::encode(&frame_codec::new_frame(
        envelope.cmd as i32, 0, envelope.body.clone(),   // body 已是 Bytes，clone 免费
    )).into()
});
for target in &envelope.targets {
    let Some(handle) = state.registry.get(/* ... */) else { continue };
    match &shared_frame {
        Some(bytes) => { handle.send_encoded(bytes.clone()); }           // 零拷贝广播
        None => { handle.send_push(envelope.cmd, envelope.body.clone(), true, runtime.clone()); }
    }
}
```

效果：300 target 广播从 300 次编码 + 300 次 body 拷贝 → **1 次编码 + 300 次引用计数递增**。

### 3.2 PendingAcks 连接局部化（修 R2）

```rust
pub struct ConnectionHandle {
    // ...
    // pending ack 是连接局部状态：随连接销毁整体 drop，
    // 不再需要全局 DashMap，也不再需要 cancel_connection 的全表 retain
    pending_acks: Arc<DashMap<u64, Instant>>,  // req_id -> deadline
}

// send_push 里：不再 spawn per-push 定时任务，只登记 deadline
if need_ack {
    self.pending_acks.insert(req_id, Instant::now() + runtime.ack_timeout);
}

// writer task 的 select 里加一个低频 tick（每秒扫本连接的 pending，量级 = 单连接在途 ack 数）
let mut sweep = tokio::time::interval(Duration::from_secs(1));
tokio::select! {
    outbound = outbound_rx.recv() => { /* ... */ }
    _ = sweep.tick() => {
        let now = Instant::now();
        if handle.pending_acks.iter().any(|e| *e.value() <= now) {
            break; // 超时未 ack → 半死链，退出 writer 触发断连清理
        }
    }
    changed = close_rx.changed() => { /* ... */ }
}
```

效果：万级在线下**零额外 timer 任务**；断连清理从 O(全局 pending) 变 O(1)（跟随 handle drop）。超时精度降到 ±1s，对"10s 未 ack 断连"语义无影响（协议文档写的就是约值）。

### 3.3 gRPC 多端点负载均衡（修 R3）

```rust
// config: IM_GATEWAY_UPSTREAM_GRPC=http://java-1:9090,http://java-2:9090
let endpoints = config.upstream_grpc
    .split(',')
    .map(|addr| Endpoint::from_shared(addr.trim().to_string()))
    .collect::<Result<Vec<_>, _>>()?;
let channel = Channel::balance_list(endpoints.into_iter());  // tower p2c 负载均衡
```

单实例期配一个地址行为不变；扩容时只改环境变量。若走 K8s Service + L4 LB，则改为同址开 N 条 channel 轮询（绕开"单连接只落一台后端"）。

### 3.4 Upstream Trait（修 R5，打开状态机可测性）

```rust
#[async_trait::async_trait]
pub trait Upstream: Clone + Send + Sync + 'static {
    async fn verify_token(&self, req: VerifyTokenReq) -> Result<VerifyTokenResp>;
    async fn dispatch(&self, ctx: ConnCtx, cmd: u32, body: Bytes, req_id: u64) -> Result<UplinkResp>;
    async fn on_connected(&self, ctx: ConnCtx) -> Result<()>;
    async fn on_disconnected(&self, ctx: ConnCtx) -> Result<()>;
    async fn refresh_route(&self, ctx: ConnCtx) -> Result<()>;
    async fn on_push_acked(&self, ctx: ConnCtx, ack_body: Bytes) -> Result<()>;
}

pub struct GrpcUpstream { /* 现 RpcClients 原样搬入 */ }

// AppState 泛型化（或 Arc<dyn Upstream> 动态分发，网关 QPS 下 vtable 开销可忽略）
pub struct AppState<U: Upstream = GrpcUpstream> { pub rpc: U, /* ... */ }
```

配合把 read_loop 的收发参数从 `SplitStream<WebSocket>` 放宽为泛型 `Stream<Item=...> + Sink<Message>`（`send_auth_resp` 已经是这个写法），即可用内存双工通道对**认证超时/重放拒绝/重复 AUTH/协议过旧踢线/ack 超时断连**写真正的状态机测试——这是当前测试覆盖最值钱的空白。

---

## 4. 建议执行顺序

| 优先级 | 项 | 改动面 | 预期收益 |
|--------|----|--------|---------|
| P0 | R3 多端点 channel | rpc.rs + config，~30 行 | 解锁 Java 水平扩容，消除单连接吞吐上限 |
| P0 | R2 PendingAcks 局部化 | connection.rs，中等 | 消除万级 timer 任务与断连全表扫描 |
| P1 | R1+J1 扇出零拷贝 | axum 升 0.8 + build.rs + push.rs | 群发路径 CPU/内存显著下降 |
| P1 | R5 Upstream trait + read_loop 泛型化 | 结构性重构，不改行为 | 补上网关状态机测试空白 |
| P2 | R6 错误码上收 proto | im-proto + 双端重生成 | 消灭手抄漂移 |
| P2 | R4 控制帧优先 / 超时不变量校验 | 小 | 防慢上游误杀心跳 |
| P3 | R7 各小项 | 各自独立 | 逐项微收益 |

按流程约定：R3/R2/R1 涉及行为与依赖变更，实施前应在 CLAUDE.md Open Questions 挂对应条目，讨论定稿后由实现者按本文档动手。
