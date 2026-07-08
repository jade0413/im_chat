# im-gateway-rust 运行期问题审查与修复

> 日期：2026-07-08 ｜ 审查人：Claude（资深 Rust 架构师视角）｜ 拍板：Jade（"按建议处理"，当日实施）
> 范围：im-gateway-rust 全部源码（~2400 行）+ 部署侧（im-web/nginx.conf、docker-compose）
> 前提：D19/D28/D43 已知悉；本文不重复 2026-07-02 结构审查已修复项（R1/R2/R3/R5）
>
> **实施状态（2026-07-08）**：下列 P0-1/P0-2/P1-3/P2-4/P2-5/P2-6/P2-7 + 冗余清理 + 连接数上限
> 已全部当日实施并本机验证：`cargo test` **25/25 通过、零警告**（含 2 条新回归测试）。
> 本次同时闭环 D43 遗留事项——axum 0.8 升级后的 `cargo test` 首次在真实工具链跑通（修复前 18/18）。

## 0. 总评

架构方向正确（D19"网关零业务"落实到位，R1~R5 均已生效），测试覆盖质量高于平均。
本次发现的问题集中在**连接关闭路径的单向感知**与**攻击面防御的最后一公里**，均为运行期问题，不涉及架构调整。

## 1. 发现与修复

### P0-1 🔴 僵尸读循环：被踢连接可继续上行 Dispatch，绕过踢线语义

**问题**：`read_loop` 不监听 close 信号，只依赖"writer 发 WS Close → 对端回应 → socket 结束"感知关闭。
不配合的客户端在被 KICK / ack 超时判半死链 / drain 关停后：读循环无限存活（每帧重置 idle timeout），
且继续以旧 ConnCtx 向 Java `Dispatch` 上行帧——D11 互踢、D27 token 失效只在 AUTH 时校验，Java 侧无从分辨。

**修复**：`run_connection` 克隆一只 `close_rx` 传入 `read_loop`，循环内 `tokio::select!`（biased，close 优先）
直接响应 close 信号退出。回归测试 `close_signal_terminates_read_loop_even_if_client_keeps_socket_open`
验证：客户端拒不关闭输入流时，`close_all()` 后 run_connection 仍在 2s 内完成清理（修复前该测试永久挂起）。

### P0-2 🔴 WS 传输层无消息大小上限，64KB 帧检查形同虚设

**问题**：`ws.on_upgrade()` 未配置 tungstenite 消息上限（默认 64 MiB）；`decode_with_limit` 的检查发生在
整条消息**已完整缓冲进内存之后**。攻击者每连接可迫使网关缓冲 64MB。

**修复**：`ws.max_message_size(max_frame_bytes).max_frame_size(max_frame_bytes)`，超限由传输层直接断连；
`decode_with_limit` 保留为第二道防线。

### P1-3 🟡 uplink 指标基数无界

**问题**：`metrics.uplink_frame(cmd)` 对客户端任意 u32 cmd 建表项，恶意遍历取值空间可撑爆指标表与 /metrics 输出。

**修复**：仅 frame.proto 已知 `Cmd` 值按值展开标签，未知值归并 `cmd="other"`（`UNKNOWN_CMD_KEY`）。

### P2-4 🟡 Web 端经 nginx 反代，per-IP 限流退化为全局限流

**问题**：im-web nginx 反代 `/ws` 且不传 X-Forwarded-For，网关只认 peer IP → 所有 Web 用户共享
一只 20/s 桶；网关重启后 Web 重连风暴大面积 429，而 Flutter 直连不受影响，行为不一致难排查。

**修复**（三处联动）：
- 新模块 `client_ip.rs`：极简 CIDR 匹配 + `resolve_client_ip`（**仅当 peer ∈ trusted CIDR** 才信 XFF，
  从右往左取第一个不可信地址；退回 X-Real-IP，再退回 peer——直连客户端伪造转发头无效）；
- nginx `/ws` 补 `X-Forwarded-For` / `X-Real-IP`；
- compose 新增 `IM_GATEWAY_TRUSTED_PROXY_CIDRS`（默认 RFC1918 三段；网关侧默认**空=谁都不信**，安全默认）。

### P2-5 🟡 优雅停机时间不封顶

**问题**：drain 后 axum graceful shutdown 无限等待在途 WS handler；配合 P0-1 的僵尸循环可拖到 idle timeout 甚至无限。

**修复**：P0-1 使连接在 close_all 后立即收敛；另加封顶宽限——drain 完成信号后再等一个 `drain_timeout`，
仍未收敛则强制退出（最坏关停时长 ≈ 2×drain_timeout）。

### P2-6 🟢 AUTH 前的 WS 控制帧被误杀

**问题**：`read_auth_frame` 对非 Binary 一律断连；部分客户端库握手后立即发协议层 Ping 会被拒。

**修复**：AUTH 阶段循环跳过 Ping/Pong，整个认证阶段共享同一 deadline（不能用 Ping 拖延 AUTH）。
回归测试 `skips_ws_control_frames_before_auth`。

### P2-7 🟢 MSG_RECV_ACK / RefreshRoute 转发无背压

**问题**：每条 ACK spawn 一个 task+gRPC，恶意 ACK 洪泛可绕过 dispatch 的天然串行背压制造风暴。

**修复**：连接级信号量（`MAX_INFLIGHT_CONN_EVENT_FORWARDS=32`）约束 OnPushAcked/RefreshRoute 在途转发，
超限丢弃（转发本就尽力而为；送达真相靠 SYNC 对齐）。

### 功能补全：全局连接数上限

`IM_GATEWAY_MAX_CONNECTIONS`（默认 50000），握手阶段检查，达到即 503 + `reason="capacity"` 计数。
并发握手下是软上限，对内存保护目标足够。

### 冗余清理

- `ConnKey` 手写 PartialEq/Hash（与 derive 等价）→ `#[derive(PartialEq, Eq, Hash)]`，-17 行；
- `FrameSendResult` 与 `PushSendResult` 同构枚举合并为 `PushSendResult`，删除两段 1:1 映射；
- `send_push` 内与信封层重复的 `cmd > i32::MAX` 检查删除（保留 push.rs 信封层一次）;
- 测试 Config 字面量两处重复 → `Config::for_test()`（config/connection 测试共用）；
- 测试 4 处 deprecated `try_next` → `try_recv`。

## 2. 记录在案、暂不处理

- **TLS 缺位**（部署层）：Flutter 直连宿主机 8082 明文 WS，与 D15"全链路 TLS"矛盾。生产前须前置 LB 终止
  TLS（P2-4 机制已就绪）或网关自挂 rustls——挂 Open Question。
- 重放防护为 5 分钟时间窗、无 nonce 去重（TLS 前提下可接受）。
- dispatch 单连接串行：一次 dispatch 超时（10s）会阻塞同连接 PING 应答；心跳容忍度（30s×3）> 10s，安全，
  调参时须保持该不等式。
- 观测：dispatch 无直方图/分位数、push 消费无 lag 指标——二阶段补。
- `registry.get` 每个推送目标一次 String 分配（构造查找键）——万级扇出微开销，不值得为省它引入 raw-entry。

## 3. 验证

- `cargo test`：25 passed / 0 failed / 0 警告（新增 7 条：client_ip 4 + 回归 2 + metrics 1）。
- 待联调验证（无编译依赖，行为向后兼容）：Web 端经 nginx 握手后 429 观察、drain 关停时长观察。
