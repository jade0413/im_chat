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

## 附：PR-4（bfa75c6 batch push route lookups）审查 ✅ 通过

P1 整改：findAllByUsers 对目标用户去重（LinkedHashSet 保序）→ 按 用户×平台类 拼 key → 单次 MGET → 解码分组。
500 人群聊的路由查询从 500 次 Redis 往返降到 1 次，multiGet null 防御、validateRouteOwner 校验、双层测试齐备。
唯一备注：群上限若二阶段抬高（>1000 人 = 单次 MGET 3000+ key），需按 1000 key 分片——现在不用动，
在 Open Questions 的"二阶段抬高群上限"条目里已有对应项。

## 附：PR-5（aca75b3 close gateway hangups）审查 ✅ 通过——挂账清零

- N1 ✅ 慢消费者处置：outbound_full_count 连续满计数（成功发送即复位），达阈值（可配）主动 close，
  返回值升级为三态 Sent/Dropped/Disconnected，推送侧可区分计数。
- N2 ✅ 退避复位：reconnect_backoff 抽成纯函数（稳定运行 ≥60s 复位回 1s），带单测覆盖增长与复位两条路径。
- L1 ✅ im-common 绑定 enforcer：声明 plugin 继承父 pluginManagement 的 execution（与业务模块同模式），
  三个 PR 的挂账终于关闭。

至此 PR-1~PR-5 所有严重项、建议项、挂账项全部闭环。剩余开放项均为排期内功能：
design §6 启动自检、Dockerfile（Q2）、Origin 校验/握手限流（B2/B3）、已读/群聊/文件。

## 附：PR-6（c0d2567 deployment self checks）审查 ✅ 通过——MVP 基础设施收口

- 启动自检（design §6）✅：MySQL(SELECT 1+必需表清单)→Redis(PING)→RabbitMQ→MinIO(bucket)→workerId 租约，
  顺序与设计一致，任一失败抛异常 fail-fast，@ConditionalOnProperty 可关（本地开发友好），91 行测试。
- Dockerfile（Q2）✅：server(maven→temurin21-jre)/gateway(rust1.96→bookworm-slim) 多阶段构建，
  **都带 HEALTHCHECK**——compose 里 im-gateway/im-web 的 service_healthy 依赖因此成立（这个配套关系容易漏，没漏）；
  构建上下文改为仓库根（server/gateway 都要读 im-proto），.dockerignore 已配。
- 握手限流（B2）✅：实例级令牌桶（速率/容量可配，.env 暴露），超限回 429——重连风暴第一道闸到位。
- Origin 白名单（B3）✅：无 Origin 头放行（原生 App 语义正确——浏览器无法伪造省略 Origin，
  非浏览器客户端本不受 CORS 约束）、白名单忽略大小写匹配、支持通配；429 在前 403 在后的检查顺序合理。

**至此 MVP 基础设施全部收口**：协议、消息链路、推送/互踢、网关、部署、自检、可观测性。
剩余为纯功能开发：已读回执、群聊、图片/语音、审核最小版（roadmap §8-6）。

## 附：PR-7（34ba35b 冒烟测试 + cde8814 已读回执）审查 ✅ 通过

已读链路逐项核对：read_seq **条件更新单调不回退**（`.lt` 条件 + 二次读取 effective 值，并发安全）；
**上限钳制**（不得超过 max_seq，防客户端乱报）；NOT_CONV_MEMBER 成员校验；**changed 门控**（无变更不扇出，
防 ReadNotify 风暴）；上报方自己经 responseCmd 同步拿回 effective seq（本地可纠偏）；
推送对象 = 全员排除当前连接（C2C 对端 + 自己其他端，exclude 精确到 conn_id——proto 先行、实现跟进，流程正确）；
跨模块走 PushRpc stub 且 metadata 带 tenant/trace/caller（模块铁律遵守）。
顺手把 SYNC 已知会话路径的 ConvInfo 硬编码改经 GetMemberConv 修正，并扩展了外部冒烟流程。

**前瞻备注（群聊 PR 必须处理）**：pushReadNotify 现在推"会话全员排除自己当前连接"——C2C 语义正确，
但若 GROUP 复用，500 人群一人已读会扇出 499 条无意义通知（MVP 群聊只显示自己未读，checklist §6）。
群聊 PR 时改为：C2C 推对端+自己其他端，GROUP 仅推自己其他端。
次要：exclude 后路由为空的用户被计入 offlineUsers，统计语义略歪，不影响功能。

## 整改质量评价

五项整改没有一处是"为过审查打补丁"：close 信号独立成 watch channel、错误帧的镜像编码方案、
metrics 零依赖手写——都在理解设计意图的基础上给出了优于最低要求的解法。网关现在达到生产可用门槛。
