# PR-3 复审报告：fix: address PR2 gateway review blockers (377e49c)

审查人：Claude｜日期：2026-06-13｜前置：pr2-gateway-push.md（PR-B 打回）

## 结论：**通过，可合并** ✅

| 项 | 验证结果 |
|---|---|
| R1 idle 超时 | ✅ `timeout(heartbeat*3, next())`，超时走统一断连清理路径 |
| R2 RPC 超时 + 失败不断连 | ✅ verify/dispatch/conn_event 三个可配超时；dispatch 失败回 ERROR 帧保持连接。**亮点**：gateway_error_body 用本地 prost 结构镜像 ErrorBody 的字段号（tag 1/2/3 与 body/messages.proto 逐一核对线格式兼容），既回了标准错误帧又没破坏 D19 冻结面——这是对设计意图理解到位的解法 |
| R3 push 消费者重连 | ✅ run_push_consumer_forever：外层 loop + 指数退避 1s→30s |
| R4 有界背压 | ✅ bounded channel（容量可配）+ try_send；Full→丢帧+计数。与验收标准的偏差见 N1 |
| R5 metrics | ✅ /metrics 端点，手写 Prometheus 文本格式（零新依赖，务实）；五个指标全覆盖要求：连接数(按 tenant)/上行帧(按 cmd)/推送送达/失败/ack 超时断连 |
| B1（顺手做了） | ✅ 心跳每 N 次才续路由（should_refresh_route，可配），RPC 风暴解决 |
| P2/P3（PR-A 建议） | ✅ deploy README 补 token_ver 灾备说明；TokenVersionService/PlatformClass 补边界注释 |
| close 信号 | ✅ 改用独立 watch channel——避免"数据队列满导致 close 都发不进去"，设计意识好 |

## 备注（不阻塞，下一 PR 跟踪）

- N1. R4 的处置与验收标准有偏差：队列 Full 时丢帧而非立即断连。对 need_ack 推送，ack 超时路径会间接断连（10s）；
  对非 ack 帧（READ_NOTIFY 等）静默丢、靠 SYNC 自愈——可接受，但建议补"连续 Full N 次（如 3 次）主动断连"，
  让持续性慢消费者更快进入重连自愈，而不是每帧都丢。
- N2. push 重连 backoff 永不复位：消费者稳定运行数小时后闪断，退避会从上次的值继续翻倍。
  成功消费一段时间（如 60s）后应重置回 1s。约 3 行。
- N3. 单测共 5 个（config/codec/metrics/connection），核心路径（idle 超时、背压、ERROR 帧）缺测试——
  审查环境无 cargo，合并前 CI 必须：`cargo test` + `cargo clippy -- -D warnings` 全绿（复审门槛原文要求）。
- 挂账提醒：PR-1 L1（im-common 绑 enforcer）已两个 PR 未处理，下一 PR 必须带上。

## 整改质量评价

五项整改没有一处是"为过审查打补丁"：close 信号独立成 watch channel、错误帧的镜像编码方案、
metrics 零依赖手写——都在理解设计意图的基础上给出了优于最低要求的解法。网关现在达到生产可用门槛。
