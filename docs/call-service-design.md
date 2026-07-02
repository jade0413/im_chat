# 实时语音通话设计（D45，2026-07-02 确定）

> 范围：**1v1 语音 MVP**。媒体 = WebRTC P2P + 自建 coturn 中继；信令 = 现有 WS 帧通道（网关 D19 透传，Rust 零改动）。视频/群通话仅留协议扩展位。
> 关联：ws/frame.proto CALL_*（40~45,49）、body/call.proto、im-server/im-call-service、im-app lib/data/call/、deploy coturn。

## 1. 总体链路

```
Flutter A ──WS 信令──> gateway ──gRPC Dispatch──> im-call-service（状态机/Redis）
                                                      │ PushRpc
Flutter B <──WS CALL_NOTIFY── gateway <──RabbitMQ─── im-push-service
Flutter A <═══════════ WebRTC SRTP 音频（P2P 直连，失败走 coturn UDP 中继）═══════════> Flutter B
```

- 服务端**不接触媒体**，只做：呼叫状态机、信令中转（SDP/ICE 不解析）、TURN 凭证签发、超时代答、忙线代答、通话记录（CDR）。
- 网关零改动：CALL_* 上行走 `Uplink.Dispatch`，下行 CALL_NOTIFY 走既有 PushEnvelope。

## 2. 信令时序（正常接通）

```
A                    server                        B(全端)
│ CALL_INVITE ──────────>│  busy检查/建状态(INVITING)   │
│<── CALL_ACK(ice_servers)│──── CALL_NOTIFY(INVITE, ice_servers) ──>│ 振铃
│                        │<──────────── CALL_ANSWER(accept) ────────│
│<─ CALL_NOTIFY(ACCEPTED)│──── CALL_NOTIFY(ANSWERED_ELSEWHERE) ──> B其余端停铃
│                        │        （ACK 给接听端，带 ice_servers）
│ CALL_SIGNAL(SDP_OFFER) ─>│ 校验参与者 → CALL_NOTIFY(SIGNAL) ──────>│
│<─ CALL_NOTIFY(SIGNAL) ──│<─────────── CALL_SIGNAL(SDP_ANSWER) ────│
│ …ICE candidate 双向透传（trickle）…                    │
│═══════════════ P2P/TURN 音频 ═══════════════════════│
│ CALL_HANGUP ──────────>│ 写CDR → CALL_NOTIFY(HANGUP, duration) ──>│
```

**接通后媒体协商顺序**：主叫收到 ACCEPTED 才 createOffer（被叫接听时机由人决定，offer 早发会撞上未初始化的被叫）。ICE 用 trickle，双向随到随发。

## 3. 状态机（服务端权威，Redis）

```
INVITING ──answer(accept)──> ACTIVE ──hangup/断连──> ENDED(写CDR)
    │──answer(reject)──> ENDED(rejected)
    │──caller hangup──> ENDED(canceled)
    │──60s 超时──────> ENDED(timeout)
    │──callee 忙线────> 不建状态，INVITE 直接代答 BUSY
```

Redis 结构（tenant 前缀走 RedisKeys 约定）：
- `call:{tenantId}:{callId}` HASH：caller_id / callee_id / media / state / client_call_id / invite_at / answer_at / answer_conn_id，TTL：INVITING 90s → ACTIVE 4h（每次 SIGNAL 不续期，4h 为单通话硬上限）
- `call_user:{tenantId}:{userId}` STRING=callId：占线标记，TTL 与主键同步；INVITE 时对主叫、被叫都 SETNX——任一失败=忙线
- `call_idem:{tenantId}:{clientCallId}` STRING=callId，TTL 90s：INVITE 幂等（弱网重发返回同 call）
- `call_deadline:{tenantId}` ZSET member=callId score=振铃截止时间戳：超时 sweeper 每秒 `ZRANGEBYSCORE` + 逐个原子摘除（多实例安全，谁摘到谁处理）

状态迁移用 Lua 保证原子（多端同时接听只有一个赢：`state==INVITING 才置 ACTIVE 并记 answer_conn_id`）。

## 4. 规则明细

| 场景 | 行为 |
|---|---|
| 忙线 | 主叫或被叫已有占线标记 → CALL_ACK code=CALL_BUSY，**不打扰被叫**；不写 CDR（可选二阶段记未接来电） |
| 多端振铃 | CALL_NOTIFY(INVITE) 推被叫全端（need_ack=true）；先 accept 者赢，其余端收 ANSWERED_ELSEWHERE 停铃 |
| 拒接 | 任一端 reject 即整体拒接（推主叫 REJECTED，其余端 ANSWERED_ELSEWHERE 停铃）|
| 超时 | 振铃 60s 无人应答 → 双方推 TIMEOUT，CDR result=timeout |
| 主叫取消 | INVITING 期 CALL_HANGUP → 被叫全端推 CANCELED |
| 通话中断连 | MVP 不做服务端断连联动挂断（WebRTC 侧 ICE disconnected 自行感知）；ACTIVE TTL 4h 兜底清状态。**二阶段**：订阅 ConnEvent.OnDisconnected 联动 |
| SIGNAL 路由 | 校验 sender ∈ {caller,callee} 且 state=ACTIVE（或 INVITING 允许提前 candidates？MVP：仅 ACTIVE）→ 推对端 user 全端，非通话端按未知 call_id 忽略 |
| need_ack | CALL_NOTIFY 是 MSG_PUSH 之外唯一 need_ack=true 帧（协议 §3 修订）：振铃/接通丢帧不可接受，宁可断连触发重连+状态自愈；D44 客户端已泛化 ack，前向安全 |
| 黑名单/好友限制 | MVP 不查（与 D17 开放单聊一致）；黑名单代答 REJECTED 挂 Open Question |
| 通话中来电 | 即忙线（见上）；不做呼叫等待 |

错误码（common/error.proto 追加）：`CALL_BUSY=1201`、`CALL_NOT_FOUND=1202`（已结束/不存在）、`CALL_STATE_INVALID=1203`（如对 ACTIVE 再 answer）。

## 5. TURN 凭证（coturn REST 机制，RFC "TURN REST API"）

- coturn 配置 `use-auth-secret` + `static-auth-secret=<secret>`；
- 服务端签发：`username = {unixExpiry}:{tenantId}-{userId}`，`credential = base64(HMAC-SHA1(secret, username))`，时效 1h；
- 在 CALL_ACK(INVITE) 与 CALL_NOTIFY(INVITE)/ACK(accept) 中下发 `ice_servers`（stun + turn udp/tcp）；
- 配置项：`im.call.turn.urls`（逗号分隔）、`im.call.turn.secret`、`im.call.stun.urls`、`im.call.ring-timeout-sec=60`。

## 6. 数据（MySQL，V13 迁移）

```sql
CREATE TABLE call_record (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id      BIGINT       NOT NULL,
  call_id        VARCHAR(64)  NOT NULL,
  caller_user_id BIGINT       NOT NULL,
  callee_user_id BIGINT       NOT NULL,
  media_type     TINYINT      NOT NULL DEFAULT 1,
  result         TINYINT      NOT NULL, -- 1=completed 2=rejected 3=canceled 4=timeout
  connected_at   DATETIME(3)  NULL,
  ended_at       DATETIME(3)  NULL,
  duration_sec   INT          NOT NULL DEFAULT 0,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_tenant_call (tenant_id, call_id),
  KEY idx_caller (tenant_id, caller_user_id, created_at),
  KEY idx_callee (tenant_id, callee_user_id, created_at)
);
```

会话内"通话结束/未接听"气泡：**二阶段**（需要 message 模块提供向 C2C 会话插系统消息的内部 RPC，MVP 只落 CDR）。

## 7. 模块与代码位置

- **im-call-service**（新模块，D5 铁律：跨模块只走 gRPC）：
  `handler/`（4 个 CmdHandler）、`service/CallSessionService`（状态机）、`service/CallPushService`（PushRpc 封装）、`service/TurnCredentialService`、`service/CallTimeoutSweeper`、`config/`（属性 + RPC 客户端）
- **im-app** `lib/data/call/call_engine.dart`（信令+WebRTC 状态机）、`lib/features/call/call_page.dart`；`ImEngine` 分发 CALL_NOTIFY/CALL_ACK → CallEngine
- **deploy**：docker-compose 增 coturn；生产需开放 UDP 3478 + 中继端口段 49160-49200

## 8. Flutter 端状态机

```
idle ─invite→ outgoing(响铃音) ─ACCEPTED→ connecting(createOffer/ICE) ─ICE connected→ active
idle ─NOTIFY(INVITE)→ incoming(振铃) ─用户接→ connecting(等 offer→createAnswer) → active
任意态 ─HANGUP/CANCELED/TIMEOUT/BUSY/REJECTED/挂断→ ended → idle
```
依赖 `flutter_webrtc`；权限：Android RECORD_AUDIO、iOS NSMicrophoneUsageDescription、macOS mic entitlement。CallKit/ConnectionService 原生来电界面与离线推送唤醒 = 二阶段（依赖推送通道选型）。

## 9. Open Questions（通话相关）

- [ ] 离线被叫：当前仅在线可达（无第三方推送通道，D 项待选型后接 VoIP push/FCM）
- [ ] 黑名单来电代答策略；通话记录气泡入会话
- [ ] 通话中 WS 断连的服务端联动挂断（订阅 OnDisconnected）
- [ ] 视频（协议已留 media 位）与群通话（需 SFU，另立设计）
