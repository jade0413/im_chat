# 微光 Lumo · im-app 架构说明

面向「后续可持续扩展的工程架构」，不是一次性 demo。本文覆盖：分层与依赖规则、目录说明、
消息发送/接收/ACK/重连/同步的完整流程、状态机、Outbox/去重/SyncCursor 设计，末尾附验收标准对照。

---

## 1. 分层与依赖规则（铁律）

```
        ┌────────────────────────────────────────────────┐
  UI    │ features/*  (页面/组件) + shared/widgets         │  只依赖 ↓
        │  · 只通过 Riverpod Provider 读状态                │
        │  · 只通过 Repository 触发动作                      │
        │  · 不出现 WebSocket / DAO / Engine / SQL          │
        └───────────────────────▲────────────────────────┘
                                 │ Provider
        ┌───────────────────────┴────────────────────────┐
  应用   │ app/providers.dart (DI 装配) · Repository         │
        │  MessageRepository / ConversationRepository      │
        └───────────────────────▲────────────────────────┘
                                 │
        ┌───────────────────────┴────────────────────────┐
  数据   │ ImEngine (协调者)                                 │
        │   ├─ 下行帧分发 → 写本地 DB                         │
        │   ├─ 上行发送 → Outbox + ImSocket                  │
        │   └─ seq 对齐 / 去重 / SyncCursor                  │
        │ Local DataSource: drift DAO  │ Remote: ImSocket/REST│
        └──────────────────────────────────────────────────┘
```

**关键不变量**：
1. **UI 不碰网络/DB**。WebSocket 绝不出现在页面里；UI 读取一律 `ref.watch(provider)`，
   动作一律 `ref.read(repositoryProvider).xxx()`。（验收 #3、#4、#5）
2. **写网络 = 写 DB**。ImEngine 把所有下行帧落到 SQLite，UI 订阅 DB 的 Stream 自动刷新。
   于是「离线可读、重连即自动刷新、多端同步」天然成立。
3. **单向依赖**：UI → Repository → Engine → (DAO / Socket / REST)。反向不依赖。

---

## 2. 目录说明

```
lib/
├── app/
│   ├── providers.dart        # Riverpod 全量装配 + AuthController（会话态 + 引擎生命周期）
│   ├── router.dart           # go_router + 鉴权重定向
│   ├── app.dart              # 根组件 + 应用前后台监听（回前台触发重连）
│   └── update_providers.dart
├── core/
│   ├── config/env.dart       # 租户/WS/REST 地址（--dart-define 注入）
│   ├── platform/             # 平台/平台类探测（对齐 proto Platform 数值）
│   ├── proto/codec.dart      # 帧编解码（唯一 proto 导入边界）
│   ├── theme/                # Lumo 设计令牌
│   ├── update/               # Shorebird + 桌面 OTA
│   └── utils/                # id / seq 对齐 / uuid / time
├── data/
│   ├── models/               # 领域模型（Conversation/ChatMessage/MessageContent(sealed)/枚举）
│   ├── local/                # Local DataSource：drift
│   │   ├── tables.dart       #   conversations / messages / outbox / app_kv
│   │   ├── daos/             #   conversation / message / outbox / kv DAO
│   │   └── db_mappers.dart   #   行 ↔ 模型
│   ├── remote/
│   │   ├── rest/             #   dio client / auth / message / file
│   │   ├── ws/               #   ★ im_socket / reconnect / ws_mappers / ws_channel
│   │   └── token_refresh.dart#   刷新三态
│   ├── repositories/         # Message / Conversation 仓储（UI 唯一入口）
│   └── im_engine.dart        # ★ 帧分发 + 发送 + Outbox + 同步 协调者
└── features/                 # splash / auth / home / conversations / chat / contacts / profile
```

---

## 3. 消息模型与状态机（验收 #6、#7）

一条消息携带：`clientMsgId`（客户端 UUID，幂等去重）、`serverMsgId`（Snowflake）、
`convId`、`seq`（会话级单调递增）、`status`。

```
 发送方状态机                          说明
 ───────────                          ──────────────────────────────
   pending ──(连接就绪,写出网关)──► sending
      ▲  │                              │
      │  │(断网/未登录,留在 Outbox)      │(收到 MSG_SEND_ACK code=0)
      │  └──────────────◄───────────┐   ▼
   (重连 drain Outbox)              │  sent ──(送达回执*)──► delivered ──(READ_NOTIFY)──► read
                                    │   │
                                    └───┴──(ACK code≠0 / 超时重试耗尽)──► failed ──(用户重试)──► sending

   任意态 ──(REVOKE_NOTIFY)──► revoked
   * delivered 为预留态：当前协议无发送端送达回执，二阶段补；read 由对端 READ_NOTIFY/peerReadSeq 推导。
```

枚举顺序即 DB 存储 index，**只能向后追加**（兼容旧库）。

---

## 4. 发送链路 + Outbox（验收 #8）

Outbox 是「投递任务」的持久化真相，与展示用的 `messages` 表解耦——断网/杀进程后仍能恢复发送。

```
UI.sendText
   │  MessageRepository.sendText
   ▼
ImEngine._send:
   1. messages 落库 (status=pending, 有 clientMsgId)         ← UI 立即看到气泡
   2. 构造 MSG_SEND body
   3. outbox.enqueue(clientMsgId, convId, body)              ← 持久化投递任务
   4. ok = socket.send(MSG_SEND)
   5. messages.status = ok ? sending : pending
        │
        ├─ 收到 MSG_SEND_ACK(code=0):
        │     outbox.remove(clientMsgId)                      ← 出队
        │     messages: serverMsgId/seq 回填, status=sent, readSeq 同步前进
        │     conversation: maxSeq/lastMsgTime 更新
        │
        ├─ 收到 MSG_SEND_ACK(code≠0):  outbox.remove + status=failed(failCode)
        │
        └─ 重连 AUTH_ACK 后 onAuthenticated → ImEngine._drainOutbox():
              遍历 outbox（按创建序）→ 超 7 天/10 次则 failed，否则 bumpAttempt + 重投
```

「连接未就绪不丢消息」由此保证：消息留在 Outbox（pending），重连后 drain 自动补发。

---

## 5. 接收链路 + ACK（验收 #12）+ 去重（验收 #9）

```
MSG_PUSH ─► ImEngine._handleMsgPush:
   1. 映射为 ChatMessage（clientMsgId 缺省时用确定性 "convId:seq"）
   2. 判断 seq 与本地 syncSeq 是否有缺口
   3. messages.upsert        ← 去重：clientMsgId 主键 + (convId,seq) 唯一索引，
                                重连/重复 SYNC 同一条得到同一主键 → 幂等不重复插入
   4. 重算连续 syncSeq；有缺口 → socket.sendSyncReq() 补齐
   5. conversation 更新（maxSeq/未读摘要/时间）
   6. socket.send(MSG_RECV_ACK, reqId=网关分配)   ← ACK 推送送达（D28）
```

**去重三重保险**：① `clientMsgId` 主键 upsert；② `(conv_id, seq)` 唯一索引；
③ 服务端无 clientMsgId 时用确定性 `convId:seq` 而非随机 UUID（否则重连重复 SYNC 会插重复）。

---

## 6. Sync Cursor + 离线同步（验收 #10）

游标 = **全局分量 `conv_list_version`**（KV 持久化）+ **会话级分量 `conversation.syncSeq`**（每会话已连续同步到的 seq）。

```
上线/重连 AUTH_ACK ─► socket.sendSyncReq()
   body = SyncReq{ conv_list_version, [ {convId, local_max_seq=syncSeq} ... ] }
        ▼
SYNC_RESP ─► ImEngine._handleSyncResp:
   · full_sync=true → 清本地消息 + 会话 syncSeq 归零（服务端判定差异过大）
   · 持久化 conv_list_version 到 KV
   · 每个 delta：upsert 会话（保留本地 draft/已读）+ 批量 upsert 消息 + 推进 syncSeq
   · delta.has_more 且 syncSeq 前进 → 再发一轮 SYNC_REQ（分段补齐）
   · 更早缺口 → 走 REST 历史分页（loadOlder，向上滚动懒加载）
```

`syncSeq` 维护「无缺口连续水位」，不能用服务端 `max_seq` 直接替代（中间可能有未补齐的洞）。

---

## 7. 自有重连 / 鉴权恢复状态机（验收 #11）

> im-web 的重连缺陷：AUTH 失败后用 **bool** 判断刷新结果，网络抖动导致刷新失败被当成「凭证失效」
> 直接登出；与双端互踢叠加会反复弹下线。本实现 **自行设计**，要点如下。

```
            connect()  [并发守卫 _connecting + generation]
                 │ open ws
                 ▼
          authenticating ──(8s 无 AUTH_ACK)──► error ─► 退避重连
                 │ AUTH_ACK
        ┌────────┴─────────┐
     code=0              code≠0
        │                   │ _recoverAuth(三态刷新)
        ▼                   ├─ success      → 用新 token connect()
    connected               ├─ authInvalid  → onAuthExpired()（登出）
   · backoff.reset          └─ networkError → 退避重连（不登出，不计失败）
   · 启动心跳                  · 连续 AUTH 失败 > 2 → 登出（防互踢抖动误杀）
   · onAuthenticated → drainOutbox
   · sendSyncReq（补齐离线）
        │
        ├─(2.5×心跳周期无下行帧)─► 半死链 → 主动断 → 退避重连
        ├─(onClose 非手动)──────► 退避重连：1→2→4…→60s ±30% 抖动
        ├─(网络恢复/前台唤醒)────► 重置退避 + 立即重连；已连则发 PING 探活
        └─(KICK)───────────────► 登出 + 停止重连（有意断开）
```

设计要点：①刷新三态区分登出 vs 重试；②失败计数封顶防误杀；③并发连接守卫；
④退避+抖动+封顶；⑤半死链探测；⑥发送解耦给持久化 Outbox。

---

## 8. 会话列表（验收 #13）

`conversation` 表承载：最新消息摘要/时间、未读数（`max_seq - read_seq`）、置顶 `pinned`、
免打扰 `muted`、草稿 `draft`、对端已读 `peer_read_seq`。列表流按「置顶优先 + 最后消息时间倒序」。
草稿在离开聊天页 `InputBar.dispose` 时落库，进入时恢复。

---

## 9. 预留扩展（验收 #14、#15、#16）

- **富媒体**（图片/语音/文件/视频）：`MessageContent` 是 sealed 类型，`ImageBody/VoiceBody/FileBody/VideoBody`
  与 `sendImage/File/Voice`、proto `ImageContent/VoiceContent/FileContent` 已就绪；缺的是 presign 直传接线（FileApi 已备）。
- **多端登录/多端同步/已读回执**：靠 `client_msg_id` 去重 + MSG_PUSH 多端下发 + `peer_read_seq`/READ_NOTIFY；
  互踢矩阵按平台类（D11）。
- **Shorebird 热更新**：仅移动端补丁，**不替代正式发版**；桌面端走版本清单 OTA。详见 README §5。

---

## 10. 验收标准对照

| # | 要求 | 落点 |
|---|------|------|
| 1 | 可运行 | `make bootstrap`（flutter create + 配置）后 `flutter run` |
| 2 | 四端目录 | `tool/bootstrap.sh` 生成；关键配置见各平台 `SETUP.md` |
| 3 | 结构清晰 | 本文 §1/§2 分层与目录 |
| 4 | WS/协议/缓存/Repository/Riverpod 骨架 | data/remote/ws · core/proto · data/local · data/repositories · app/providers |
| 5 | 发送/接收/ACK/重连/同步结构 | §4/§5/§6/§7 |
| 6 | 不是一次性 demo | 分层 + 仓储 + 引擎 + 持久化，单向依赖可扩展 |
| #6字段 | clientMsgId/serverMsgId/convId/seq/status | `ChatMessage` |
| #7 | 7 态 | `MessageStatus`（§3） |
| #8 | Outbox | `Outbox` 表 + `OutboxDao` + `_drainOutbox`（§4） |
| #9 | 去重 | clientMsgId 主键 + (convId,seq) 唯一索引 + 确定性回退（§5） |
| #10 | Sync Cursor | `conv_list_version`(KV) + `syncSeq`(会话)（§6） |
| #11 | 心跳/重连/鉴权恢复 | `ImSocket`（§7） |
| #12 | 收到 ACK | `MSG_RECV_ACK`（§5） |
| #13 | 会话列表 | 最新/未读/置顶/免打扰/草稿（§8） |
| #14 | 富媒体预留 | sealed MessageContent（§9） |
| #15 | 多端/已读回执预留 | §9 |
| #16 | Shorebird 非发版替代 | README §5 |
| #17 | 架构文档 | 本文 |
```
