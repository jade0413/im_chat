# 好友申请 / 关系链设计文档

> 状态：待实现 | 讨论日期：2026-06-15 | 关联决策：D40、D41（提前 D17 标注的二阶段项）
>
> 实现按本文档执行；如需偏离，先在 CLAUDE.md Open Questions 提出再改文档（协作铁律）。

---

## 1. 定位与边界

给现有 C2C 聊天补上「需对方验证同意才能成为好友」的标准社交流程，并支持「对方开启免验证则直接加成功」。核心诉求拆成四条：

1. 发起申请可带一段**备注信息**，对方可见。
2. 好友验证消息**实时推送**到前端，属于**系统通知**。
3. 联系人页新增「通知」入口，点开看**历史记录**，可在其中**同意**，同意后成为好友即可聊天。
4. 设置里可开启「免验证添加」，**默认关闭**（即默认需要验证）；免验证直接加成功的情况**也要有历史记录**。

### 边界（已定）

- **参与者**：仅 `user_type = member` 之间。访客（visitor）/坐席（agent）**不进好友体系**。
- **租户范围**：MVP **仅租户内**好友。预留 `user.allow_cross_tenant_friend`（默认 0），跨租户实际打通推二阶段独立设计（D41）。
- **黑名单**：复用 D17 已定黑名单能力；申请前经 `UserRpc.CheckRelation` 判定，被对方拉黑 → **静默失败**（不建申请、不通知）。
- **与 D17 tenant 级 `friend_required` 正交**：本设计的 `friend_verify_required` 管「加我要不要验证」；`friend_required` 管「是否必须好友才能发消息」。两者互不耦合，均由 `CheckRelation` 承载。

---

## 2. 为什么不新增「消息主类型」（回答最初的架构疑问）

最初的设想是给消息引入「主类型 / 子类型 / 消息体」三层，以容纳系统通知 / 好友通知 / 普通消息。**结论：该三层结构现有协议已具备，无需新增全局枚举。**

| 层 | 现有承载 | 取值示例 |
|----|----------|----------|
| 容器层 | `ConvType`（enums.proto） | `C2C` / `GROUP` / `CS_SESSION` / **`SYSTEM`（每用户一个通知会话）** |
| 内容主类 | `MsgContent` 的 oneof 分支 | `text/image/voice/file`（聊天）/ **`notification`（系统通知）**/ `custom` |
| 子类型 + 体 | `NotificationContent.event_type` + `payload` | `event_type="friend.request"`，`payload={...}` |

再加一个全局 `msg_main_type` 会与 `frame.cmd`（传输层）/ `ConvType`（容器）/ `oneof`（内容）形成**四个并行类型维度**，互相对齐成本高，反而降低健壮性。因此好友通知**复用 `NotificationContent`，仅新增 event_type 字符串**，content.proto 零改动。

---

## 3. 数据模型

### 3.1 `friend_request`（申请，状态唯一真相）

```sql
CREATE TABLE friend_request (
  id            BIGINT      NOT NULL,            -- Snowflake
  tenant_id     BIGINT      NOT NULL,
  from_user_id  BIGINT      NOT NULL,
  to_user_id    BIGINT      NOT NULL,
  note          VARCHAR(128) NOT NULL DEFAULT '',-- 申请备注，对方可见
  status        TINYINT     NOT NULL DEFAULT 0,  -- 0 pending / 1 accepted / 2 rejected / 3 ignored
  auto_accepted TINYINT(1)  NOT NULL DEFAULT 0,  -- 1=因对方免验证自动通过
  handle_time   DATETIME(3) NULL,                -- 处理时间（accepted/rejected/ignored 时写）
  create_time   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_to_status   (tenant_id, to_user_id, status),   -- 通知列表/待处理计数
  KEY idx_from        (tenant_id, from_user_id, create_time),
  KEY idx_pair        (tenant_id, from_user_id, to_user_id, status)
);
```

- **每次申请插入一行** → 完整历史；拒绝/忽略后再申请就是新一行。
- **同一 `from→to` 至多一条 pending**：DB 层用生成列 `pending_pair`（仅 status=0 时 = `from:to`，终态为 NULL）+ `UNIQUE(tenant_id, pending_pair)` 强制（部分唯一的 MySQL 实现方式）。应用层先 `findPending` 命中则更新 note；并发漏判时 insert 撞唯一键 → 捕获 `DuplicateKeyException` 回退到既有 pending（幂等）。status 流转到终态时生成列自动变 NULL，释放唯一槽，可再次申请。
- `status` 终态（1/2/3）不可逆转。

### 3.2 `friend`（关系，双向行）——**复用 baseline 已有表，不新建**

baseline `01-schema.sql`（V1）已建 `friend` 表（D17 预留），直接复用，**T37 不再创建新表**：

```sql
-- 已存在于 baseline，列出供参考
CREATE TABLE friend (
  tenant_id      BIGINT      NOT NULL,
  user_id        BIGINT      NOT NULL,            -- 关系拥有者
  friend_user_id BIGINT      NOT NULL,            -- 好友
  remark         VARCHAR(64) NOT NULL DEFAULT '', -- owner 给对方的备注名
  status         TINYINT     NOT NULL DEFAULT 1,  -- 1 正常（MVP 删好友直接物理删两行）
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, user_id, friend_user_id)
);
```

- 双向各存一行（A→B、B→A），`我的好友列表` = 按 `(tenant_id, user_id)` 查；每方独立 `remark`。
- 删除好友 = 删两行。黑名单是独立维度（baseline `user_blacklist`，D17），不写本表。
- 实体 `FriendEntity` 映射本表（`@TableName("friend")`）。

### 3.3 用户设置 + 唯一用户名（加列，沿用 D34 在 user 加 flag 的风格）

```sql
ALTER TABLE user ADD COLUMN username                  VARCHAR(32) NULL;              -- D42 对外唯一标识（自填、可分享）
ALTER TABLE user ADD COLUMN friend_verify_required    TINYINT(1)  NOT NULL DEFAULT 1; -- 1=加我需验证（默认）
ALTER TABLE user ADD COLUMN allow_cross_tenant_friend TINYINT(1)  NOT NULL DEFAULT 0; -- D41 预留，MVP 不实现
ALTER TABLE user ADD UNIQUE KEY uk_tenant_username (tenant_id, username);            -- NULL 不参与唯一约束
```

- 「免验证开关」= 前端把 `friend_verify_required` 置 0。默认 1（需验证），与微信一致。
- **`username`（D42）= 对外加好友标识**，独立于登录用 `account`（生产为手机号，`uk_tenant_account`）：
  - 格式：`^[a-z][a-z0-9_]{5,31}$`（字母开头，小写字母/数字/下划线，6–32 位）。
  - 自填、租户内唯一；初始为 NULL（老用户/访客无）；visitor/agent 不分配。
  - 设置后可改，但加频率限制（具体策略实现期定，如每年 N 次）。
  - 设置接口：`PUT /api/user/username {username}`，事务内查重 + 校验格式 + 频率。

---

## 4. 状态机与流程

```
                 ┌──────────── accept ──────────► accepted(1)   [建 friendship 双向]
   pending(0) ───┼──────────── reject ──────────► rejected(2)
                 └──────────── ignore ──────────► ignored(3)
   (对方免验证)  直接 ── accepted(1) + auto_accepted=1 ──► [建 friendship 双向]
```

### 4.0 查找待加用户（加好友入口）

`GET /api/friend/search?keyword=` 返回候选用户公开资料（id/username/昵称/头像/蓝V），供发起申请用。查找规则收紧为**精确定位，杜绝枚举**：

- **按 `username` 精确匹配**（可分享 handle）。
- **按完整手机号（account）精确匹配**（需输入完整号码）。
- **不做昵称匹配、不做任何前缀模糊**：昵称不唯一且前缀可枚举即隐私泄露。

> 已落地：`UserMapper.searchUsers` = `WHERE (username = #{keyword} OR account = #{keyword})`（移除原 `nickname/account LIKE prefix`）。复用 `GET /api/v1/users/search`，前端文案统一为「输入用户名或完整手机号」。

### 4.1 发起申请 `POST /api/friend/requests {to_user_id, note}`

事务/校验顺序：

1. `from != to`；二者均为 member（非 visitor/agent）；同租户（或 `allow_cross_tenant_friend`，MVP 恒同租户）。
2. `UserRpc.CheckRelation(from, to)`：若 `blocked`（to 拉黑 from）→ **静默成功**，不建 row、不通知，对 A 返回与正常一致的结果（不泄露拉黑）。
3. 已是好友（`friendship` 存在）→ 返回 `already_friend`，不建 row。
4. 存在 pending → 更新该行 `note`+时间，重发通知（幂等），返回 `pending`。
5. 读 `to.friend_verify_required`：
   - **=1 需验证**：插入 `friend_request(status=0)` → 给 **B** 发 `friend.request` 通知 → 返回 `pending`。
   - **=0 免验证**：插入 `friend_request(status=1, auto_accepted=1, handle_time=now)` → 建 `friendship` 双向 → 给 **B** 发 `friend.added` 通知（"X 已添加你为好友"，进历史）→ 返回 `accepted`。A 立即可聊。

### 4.2 同意 `POST /api/friend/requests/{id}/accept`（仅 to_user 本人）

事务：校验 row 属于当前用户且 `status=0` → 置 `accepted`+`handle_time` → 建 `friendship` 双向 → 给 **A** 发 `friend.accepted` 通知（A 得知并可开聊）→（可选）给 **B 自己其他端**回流一条 `friend.request.handled` 信令同步按钮态。并发重复 accept 由 `status=0` 行条件幂等。

### 4.3 拒绝 / 忽略 `.../reject`、`.../ignore`

置 `rejected/ignored`+`handle_time`，**不通知申请方**（与微信一致，避免尴尬）；A 端无反馈，可再次申请、无冷却。

### 4.4 删除好友 `DELETE /api/friend/{friend_id}`

删 `friendship` 双向行。历史 `friend_request` 不动。若 tenant `friend_required=ON`，删后 `CheckRelation` 自动判定不可再发消息。

---

## 5. 通知下发（复用消息管道，关键）

好友通知不是独立信道，而是写入接收方 **SYSTEM 会话**的一条 `NotificationContent` 消息，因此**免费获得 seq / 多端同步 / 离线增量同步**（核心约定 3、4）。

- SYSTEM 会话每用户一个，首次通知时由 `ResolveConv(SYSTEM)` 懒创建。
- 写入即走 message → outbox → `MsgSavedEvent` → push 扇出（在线实时 `MSG_PUSH`）；离线用户下次 `SYNC_REQ` 补齐。
- 「通知」入口 = 渲染该用户 SYSTEM 会话的消息列表（即历史记录）。红点 = SYSTEM 会话 `max_seq - read_seq`。

### 5.1 新增 event_type（仅字符串，无 proto 结构改动）

| event_type | 触发 | payload（JSON）关键字段 |
|------------|------|--------------------------|
| `friend.request`         | 收到好友申请（需验证）           | `request_id, from_user_id, from_nickname, from_avatar, note, time` |
| `friend.accepted`        | 自己的申请被对方通过             | `request_id, to_user_id, to_nickname, time` |
| `friend.added`           | 对方免验证，被直接加为好友       | `request_id, from_user_id, from_nickname, note, time` |
| `friend.request.handled` | （可选）本人其他端同步处理结果   | `request_id, result(accepted/rejected/ignored)` |

> payload 结构须登记到 docs/protocol.md 的 NotificationContent 事件附录。

### 5.2 按钮状态如何多端一致（D40 核心裁决）

- **`friend_request` 表是状态唯一真相**；通知 payload **只带 `request_id` 等展示字段，不带可变状态**。
- 客户端渲染通知列表时，按 `request_id` 批量查当前 `status`（见 §6 列表接口）决定按钮："同意 / 已同意 / 已拒绝 / 已失效"。
- 某端处理后回流 `friend.request.handled` 进 SYSTEM 会话，在线端反应式刷新；离线端靠下次拉列表对齐。两条路径都收敛到同一真相，不会改写历史消息。

---

## 6. 接口

### 6.1 REST（im-server，user 模块）

```
POST   /api/friend/requests            {to_user_id, note}        → {result: pending|accepted|already_friend, request_id?}
POST   /api/friend/requests/{id}/accept                          → {code}
POST   /api/friend/requests/{id}/reject                          → {code}
POST   /api/friend/requests/{id}/ignore                          → {code}
GET    /api/friend/requests?role=incoming|outgoing&cursor=&limit= → 申请历史（含当前 status，供通知页渲染按钮）
GET    /api/friend/list?cursor=&limit=                            → 好友列表
PUT    /api/friend/{friend_id}/remark  {remark}                  → 改备注名
DELETE /api/friend/{friend_id}                                   → 删好友
PUT    /api/friend/settings            {friend_verify_required}  → 设置（allow_cross_tenant_friend 预留不开放）
```

### 6.2 新增内部 gRPC（proto 改动，走 §proto 改动流程）

`MessageRpc` 增加：

```proto
// 向指定用户 SYSTEM 会话追加一条系统通知（resolve+append 内部完成，复用消息管道获得 seq/多端/离线）
rpc SendSystemNotification (SendSystemNotificationReq) returns (SendSystemNotificationResp);

message SendSystemNotificationReq {
  int64  tenant_id   = 1;
  int64  to_user_id  = 2;
  string event_type  = 3;   // friend.request / friend.accepted / friend.added / ...
  string payload     = 4;   // JSON
}
message SendSystemNotificationResp { int32 code = 1; int64 seq = 2; }
```

- 复用 `UserRpc.CheckRelation`（已存在）做黑名单/好友校验。
- 好友逻辑归 **user 模块**（与 CheckRelation/黑名单同域），不新建模块；跨模块仅调 `MessageRpc.SendSystemNotification`，遵守模块隔离铁律。

---

## 7. 与现有决策一致性核对

| 关注点 | 结论 |
|--------|------|
| content.proto | **零改动**（复用 NotificationContent + 新 event_type） |
| enums.proto | 复用 `ConvType.SYSTEM`，无改动 |
| proto 新增 | 仅 `MessageRpc.SendSystemNotification`（rpc/internal.proto，加 RPC + 2 message，符合「只增不改」） |
| D3 tenant 贯穿 | friend_request/friendship 首列 tenant_id，拦截器自动注入 |
| D17 黑名单 / friend_required | 复用 CheckRelation；本设计与 tenant 级 friend_required 正交 |
| D18 Outbox / D26 seq | 通知走消息管道，天然复用 outbox 与会话级 seq |
| D11 多端 | 通知走 SYSTEM 会话 seq，多端同步与离线补齐免费获得 |
| D41 跨租户 | 仅留 `allow_cross_tenant_friend` 配置位，MVP 不实现 |
| D42 username | 新增 `user.username`（唯一对外标识）；**`searchUsers` 精确匹配 username 或完整手机号，无昵称/前缀模糊** |

---

## 9. T38 实现规格（message 侧 SendSystemNotification，build-ready）

> 当前 `FriendNotificationPort` 为占位日志实现；好友逻辑已可用（状态唯一真相在表，客户端可拉取）。
> 实时下发按本规格实现，三模块改动如下。

### 9.1 conversation 模块：每用户 SYSTEM 会话「找或建」

- `ConversationCreator` 新增 `createSystem(long userId)`：建 `conversation(type=SYSTEM)` + 单条 `conversation_member(userId)`；`c2c_key` 复用一个稳定唯一键避免重复建，建议 `"sys:" + userId`（c2c_key 列已唯一，借道即可，不新增列）。
- `ConversationService` 新增 `resolveSystemConv(long userId)`：按 `c2c_key="sys:"+userId` 查，命中返回 `toConvInfo`，未命中 `createSystem` 并兜底 `DuplicateKeyException`（与 `resolveC2c` 同模式）。
- 暴露内部 RPC `ConversationRpc.ResolveSystemConv(userId) → ConvInfo`（internal.proto，加 RPC + Req/Resp）。

### 9.2 message 模块：SendSystemNotification 实现

- `MessageGrpcService` override `sendSystemNotification`：
  1. `conversationClient.resolveSystemConv(toUserId)` 拿 SYSTEM ConvInfo；
  2. 构造 `MsgContent`（`setNotification(NotificationContent{event_type, payload})`）；
  3. 走 `MessagePersistService.persist(...)` 同款落库：分配 seq、insert message、更新 conv 进度、写 outbox（`MsgSavedEvent`）。
- **系统发送方约定**：`sender_id = 0`（保留为「系统」）。`MessageAssembler.toPush` 组装 `Sender` 时，sender_id=0 走默认系统资料（昵称如「系统通知」、空头像），不查 user 表。推送扇出 `MsgSavedEventConsumer` 读 `conversation_member` → 单成员（接收方本人）即送达，**无需改推送链路**。
- **幂等**：`client_msg_id = "sys:" + eventType + ":" + requestId`，复用现有 `client_msg_id` 唯一去重（同一事件重发不重复落库）。
- 因 persist 需要 `ConnCtx`，可加一条不依赖连接的内部落库入口（`persistSystem(tenantId, toUserId, convInfo, content, clientMsgId)`），避免伪造 ConnCtx。

### 9.3 user 模块：gRPC 客户端实现 Port

- 仿 `cs/config/CsGrpcClientConfig` 在 user 模块配 `MessageRpcBlockingStub`（in-process channel）。
- 新增 `GrpcFriendNotificationPort implements FriendNotificationPort` 标 `@Primary`：调 `MessageRpc.SendSystemNotification`，覆盖 `LoggingFriendNotificationPort`。
- 失败降级：通知失败仅记日志、不回滚好友事务（状态真相在表，客户端可拉取兜底）。

### 9.4 验收

- A 申请 B（B 需验证）→ B 的 SYSTEM 会话新增一条 `friend.request`，B 多端 SYNC 可见、红点 +1；B accept → A 的 SYSTEM 会话收到 `friend.accepted`。
- 免验证：A 加 B → B 收 `friend.added`。
- 同一申请重复触发不产生重复系统消息（client_msg_id 幂等）。

---

## 8. 待实现期细化（非阻塞）

- 通知红点跨端已读位（复用 SYSTEM 会话 read_seq 即可，确认前端按 conv_type=SYSTEM 单独展示）。
- 申请历史分页游标策略（按 id 倒序）。
- `friend.request.handled` 回流是否 MVP 即做，还是仅靠拉列表对齐（倾向 MVP 先靠拉取，回流二阶段加）。
