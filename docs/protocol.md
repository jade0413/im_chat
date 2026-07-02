# im-proto 协议设计说明 v0.1

> proto 文件本身是唯一事实来源，本文记录设计理由与不写在 proto 里的约定。

## 1. 总体分层（D19：透传式网关）

```
im-proto/proto/
├── ws/frame.proto        ┐ 网关编译的全部（Rust prost/tonic）
├── rpc/gateway.proto     ┘
├── common/  enums / content / error    ┐
├── body/    messages.proto             │ Java + 客户端 SDK 编译
├── rpc/     internal.proto             │ （网关不编译）
└── events/  events.proto（MQ）          ┘
```

核心机制：`Frame{version, req_id, cmd, body: bytes}`。网关只解码连接层帧（AUTH/PING/KICK），
业务帧 body 原样 bytes 经 `Uplink.Dispatch(ConnCtx, cmd, bytes)` 进 Java 路由器，响应 bytes 原样包帧回写；
下行同理（`PushEnvelope{cmd, body}`）。**新增业务帧 = 加 Cmd 枚举值 + body 定义 + Java handler，Rust 零改动。**

代价（已接受）：cmd 与 body 类型匹配是运行时约定，Java 路由器入口做一次解码失败防御；
网关无法做按消息类型的精细化处理（也不应该做——网关零业务，核心约定 2）。

## 2. 连接生命周期

```
WSS 连接 → 5s 内必须发 AUTH（否则断）→ 网关 gRPC VerifyToken（校验 JWT 内 platform_class/token_ver）→ AUTH_ACK
→ 客户端发 SYNC_REQ 增量同步 → 进入收发循环（PING 每 30s，2 次无 PONG 判死）
→ 服务端 KICK（互踢/封号/强升级）或客户端主动断 → 网关清路由 + OnDisconnected
```

- 重连：指数退避 1s/2s/4s/.../60s + ±30% 随机抖动（§13.3 防重连风暴），重连成功必发 SYNC_REQ
- `Frame.version` 低于服务端最低兼容版本 → KICK(PROTO_TOO_OLD)，客户端引导升级
- AuthReq.timestamp 防重放（±5min）；Web 端网关校验 Origin

## 3. req/ack 配对与可靠性

- `req_id`：客户端连接内自增；同步语义帧（MSG_SEND/SYNC_REQ/READ_REPORT）的响应帧回带同 req_id；不需 ack 的服务端主动推送 req_id=0；`PushEnvelope.need_ack=true` 的下行帧由网关分配非 0 req_id（D28），客户端 `MSG_RECV_ACK` 必须回带同 req_id
- 客户端对 MSG_SEND 维护待确认队列（pendingMap：clientMsgId → {convId, body, sentAt}），收到 MSG_SEND_ACK 即出队；客户端生成 client_msg_id 保证服务端幂等去重。
  - **im-web 当前实现（与早期"5s 定时重试 3 次"设计不同，以实现为准）**：单连接内**不做定时重发**——MSG_SEND 只在发送时投递一次；仅在 ① **重连成功（AUTH_ACK 后）** `drainPending()` 补发仍在队列中的消息，或 ② **用户手动重试** failed 消息时再次发送。pending 消息超过 `PENDING_TTL_MS = 60s`（按 sentAt 计）在 drain 时直接标记 failed。
  - 影响：连接未断但服务端漏回 ACK 时，消息会停在 `sending` 直到下次重连或手动重试才补发（不会 5s 自动补）。若需"连接内发送超时自动补发"，须另加定时器，属待办。
- 服务端推送可靠性：PushEnvelope.need_ack=true 的帧（**MSG_PUSH 与 CALL_NOTIFY**，后者为 D45 修订新增——通话振铃/接通信令丢失不可接受；其余推送 need_ack=false），网关按 `req_id` 跟踪 MSG_RECV_ACK，不解码业务 ack body，不重推——
  客户端侧规则（D44 泛化）：任何服务端主动推送帧只要 req_id≠0 一律回 MSG_RECV_ACK（同 req_id，items 可为空）；响应帧（MSG_SEND_ACK/SYNC_RESP/CALL_ACK/ERROR）不 ack。
  对半死链重推 N 次照样全丢，是无效功。改用**死链判定**：
  10s 未收到 ack → 网关判定该连接为半死链 → 主动断开 + 清路由 → 客户端检测到断连立即自动重连 → 重连必发 SYNC_REQ → seq 对齐补回全部缺失。
  效果：在线接收方的最坏空窗 ≈ 10s(ack超时) + 重连耗时，且一次同步补齐所有积压，无重复消息；
  真离线的接收方走下次上线同步 + 离线推送通知（二阶段 APNs/FCM）。
- 已知冗余（待优化，见 CLAUDE.md Open Questions「发送方自回显 MSG_PUSH 去重」）：C2C 推送成员含发送者本人且未排除发起连接，发送方发起连接会收到自己消息的 MSG_PUSH 回显（多 1 下行 + 1 上行 ack/条）。客户端按 `client_msg_id` 去重，无重复气泡，仅浪费帧。正确修复需 `MsgSavedEvent` 增 `sender_conn_id` 并在 push 时 `excludeConnId`（保留发送方其他端的实时多端同步）。read-notify 扇出已正确去重发起连接，无此问题。
  （对比重推方案：需服务端每消息定时器 + 客户端去重，半死链场景下依然无效，复杂度高收益负）

## 4. 关键设计点

- **MsgSend.target 是 oneof**：首次单聊客户端没有 conv_id，发 to_user_id 由服务端 ResolveConv 解析/建会话，
  MsgSendAck 回带 conv_id——免去"先建会话再发消息"的额外往返
- **MsgPush 复用于实时推送与 SyncResp**：客户端只写一套消息入库逻辑
- **Sender 冗余昵称/头像/蓝V**：避免客户端收推送后再查用户，代价是资料变更有短暂不一致（可接受）
- **MsgRecvAck 批量**：滚动收消息时攒批确认，降低上行帧数
- **READ_REPORT/READ_NOTIFY**：`READ_REPORT` 成功的同步响应也使用 `READ_NOTIFY` body 回带最终 read_seq；
  服务端再以 `READ_NOTIFY` 轻量推送给会话其他成员和自己的其他端，内部 push 会排除当前连接，且 `need_ack=false`
- **NotificationContent 事件化**（学 Matrix）：建群/进群/坐席分配走消息管道拿 seq，多端同步免费获得；
  事件类型注册表见附录 A
- **ext map 字段**：MsgSend/MsgPush 预留，二阶段"消息扩展字段"零协议改动

## 5. REST API（非实时链路，JSON，独立于本协议）

`POST /api/v1/auth/login|register|refresh`、`GET /api/v1/convs/{id}/messages?end_seq=&limit=`（历史分页）、
`POST /api/v1/files/presign`（上传凭证）、`GET /api/v1/users/me`、管理后台 `/admin/v1/*`。
鉴权：`Authorization: Bearer {jwt}` + `X-Tenant-Id`。JWT 2h + refresh 30d。
登录/注册请求可带 `platform`（common.Platform 数值）；服务端按平台类递增 `token_ver` 并写入 access/refresh token。

## 6. 演进纪律（核心约定 5 的细化）

1. 字段号只增不改不删；删除字段用 `reserved` 占位
2. 枚举首值必须 `*_UNSPECIFIED = 0`；Cmd 分段：1-9 连接层，10-29 业务，30+ 预留，99 ERROR
3. 包名带 `v1`；不兼容变更 = 新建 v2 包并行，禁止原地破坏
4. 改 proto 流程：im-proto 改动 → `./generate.sh`（待建）→ Rust + Java 同时编译通过才可提交
5. body 与 Cmd 的对应关系以 frame.proto 注释为准，新增帧必须同步更新注释

## 7. 协议演进候选（2026-06-13 与 OpenIM/Tinode/Matrix 对比得出，按优先级）

| # | 能力 | 对标 | 设计要点 | 阶段 |
|---|------|------|---------|------|
| E1 | **conv_list_version 语义补全**（当前最实质的洞） | OpenIM 会话级同步水位 | 每用户一个会话列表变更流水号（置顶/免打扰/删除/新会话都 +1，记 user_conv_event 流水表或 Redis+落库）；SYNC_REQ 已带此字段，服务端 diff 返回变更的 ConvInfo——补齐后置顶等操作获得与消息同级的"丢推送可对齐"保障 | 群聊 PR 后立即做 |
| E2 | **typing / presence 轻信令** | Tinode pres/typing | 新增 Cmd TYPING(C→S→对端在线连接)：不落库、不走 outbox、不需要 ack，纯 best-effort 推送；presence 订阅放客服工作台需求来时设计（坐席看访客在线）| 二阶段（客服前） |
| E3 | **引用回复结构化** | Matrix relations / 微信 | MsgSend/MsgPush 加 optional `QuoteRef{server_msg_id, seq, abstract, sender_id}` 字段（proto 加字段向后兼容）；被引用消息撤回时客户端按 abstract 降级展示 | 群聊 PR 后 |
| E4 | **表情回应 reactions** | Matrix annotations | 事件化：NotificationContent("msg.reaction") 走消息管道拿 seq（多端同步免费），服务端聚合存 message_reaction 表，MsgPush.ext 带聚合计数 | 二阶段后 |
| E5 | 包体压缩 | OpenIM gzip | AUTH 协商 compression 能力 + Frame 加标志；万级规模流量不痛 | 按流量数据驱动，暂缓 |
| E6 | 消息编辑 | Matrix/Slack | 产品决策未做（微信无此功能）；若做走 REVOKE 同款事件化路径 | 不排期 |

明确不抄：Matrix DAG/联邦（中心化 seq 全序已满足）、Tinode 的 topic 订阅状态机（我们的 conversation 模型更简单够用）、XMPP/MQTT 形态。

## 附录 A：NotificationContent 事件类型注册表

| event_type | payload 字段 | 阶段 |
|---|---|---|
| group.created | {group_id, name, operator} | MVP |
| group.member_added / removed | {group_id, user_ids, operator} | MVP |
| group.name_changed | {group_id, old, new, operator} | MVP |
| cs.assigned | {conv_id, agent_id} | 二阶段 |
| cs.resolved | {conv_id, operator} | 二阶段 |
| user.muted | {user_id, until, reason} | MVP（审核处罚） |
