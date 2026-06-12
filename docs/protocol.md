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
- 客户端对 MSG_SEND 维护待确认队列：5s 无 MSG_SEND_ACK → 原 client_msg_id 重发（服务端幂等去重）→ 3 次失败标记发送失败
- 服务端推送可靠性：PushEnvelope.need_ack=true 的帧（MSG_PUSH），网关按 `req_id` 跟踪 MSG_RECV_ACK，不解码业务 ack body，不重推——
  对半死链重推 N 次照样全丢，是无效功。改用**死链判定**：
  10s 未收到 ack → 网关判定该连接为半死链 → 主动断开 + 清路由 → 客户端检测到断连立即自动重连 → 重连必发 SYNC_REQ → seq 对齐补回全部缺失。
  效果：在线接收方的最坏空窗 ≈ 10s(ack超时) + 重连耗时，且一次同步补齐所有积压，无重复消息；
  真离线的接收方走下次上线同步 + 离线推送通知（二阶段 APNs/FCM）。
  （对比重推方案：需服务端每消息定时器 + 客户端去重，半死链场景下依然无效，复杂度高收益负）

## 4. 关键设计点

- **MsgSend.target 是 oneof**：首次单聊客户端没有 conv_id，发 to_user_id 由服务端 ResolveConv 解析/建会话，
  MsgSendAck 回带 conv_id——免去"先建会话再发消息"的额外往返
- **MsgPush 复用于实时推送与 SyncResp**：客户端只写一套消息入库逻辑
- **Sender 冗余昵称/头像/蓝V**：避免客户端收推送后再查用户，代价是资料变更有短暂不一致（可接受）
- **MsgRecvAck 批量**：滚动收消息时攒批确认，降低上行帧数
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

## 附录 A：NotificationContent 事件类型注册表

| event_type | payload 字段 | 阶段 |
|---|---|---|
| group.created | {group_id, name, operator} | MVP |
| group.member_added / removed | {group_id, user_ids, operator} | MVP |
| group.name_changed | {group_id, old, new, operator} | MVP |
| cs.assigned | {conv_id, agent_id} | 二阶段 |
| cs.resolved | {conv_id, operator} | 二阶段 |
| user.muted | {user_id, until, reason} | MVP（审核处罚） |
