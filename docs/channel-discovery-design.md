# 频道 + 全局搜索设计文档（im-group-service 扩展）

> 状态：待实现 | 讨论日期：2026-07-03 | 关联决策：D48（本文档）、依赖 D46(WalletRpc.Pay 付费创建)/D13(群上限)/D24(不引 ES)
>
> 实现按本文档执行；如需偏离，先在 CLAUDE.md Open Questions 提出再改文档（协作铁律）。

---

## 1. 定位与边界（Jade 已拍板：Telegram 式频道 + 付费创建）

1. **频道（Channel）= 单向广播会话**：owner/管理员发言，订阅者只读；订阅无需审批、退订自由。
2. **全局搜索（Telegram 式）**：可按 handle/名称搜索公开群与频道并直接加入/订阅；用户搜索维持 D42 精确匹配（防枚举），**不放宽**。
3. **商业化 = 付费创建**：创建频道 / 将群设为公开（进入搜索目录）需消耗 COIN（走 `WalletRpc.Pay`），租户可配价格与免费额度。付费订阅频道（进频道收费+分账）→ 二阶段。
4. 复用原则：频道**不是新消息体系**——复用 conversation/message/seq/推送全链路，仅仅是「一种发言受限、可公开被搜到的会话」。网关/消息管道/同步机制零改动。

### 模块归属

- 频道生命周期、公开目录、全局搜索聚合 → **im-group-service**（频道本质是特化的群，不新建模块）。
- 发言权限校验 → im-message-service 发送链路扩展一个分支。
- 会话解析/成员 → im-conversation-service 少量扩展。

---

## 2. 协议改动

### 2.1 enums.proto

```proto
enum ConvType {
  // ...现有 0~4 不动...
  CHANNEL = 5;   // 单向广播频道（D48）
}
```

### 2.2 error.proto —— 沿用 4xxx 群段扩展

```proto
CHANNEL_POST_DENIED = 4101;       // 非 owner/admin 在频道发言
HANDLE_TAKEN = 4102;              // handle 已被占用
HANDLE_INVALID = 4103;            // 格式不符
CHANNEL_SUBSCRIBER_FULL = 4104;   // 订阅数达套餐上限
DIRECTORY_NOT_PUBLIC = 4105;      // 实体未公开/已下架
CREATE_PAYMENT_REQUIRED = 4106;   // 需付费且余额不足等（附 wallet 8xxx 明细码）
```

### 2.3 internal.proto

```proto
// GroupRpc（若尚无该 service 则新建；已有则追加）
service GroupRpc {
  rpc CheckPostPermission (CheckPostPermissionReq) returns (CheckPostPermissionResp); // message 发送链路调用
}
message CheckPostPermissionReq  { int64 conv_id = 1; int64 group_id = 2; int64 user_id = 3; }
message CheckPostPermissionResp { int32 code = 1; bool can_post = 2; }
```

其余跨模块调用复用现有 `ConversationRpc.ResolveConv/GetMembers`、`WalletRpc.Pay`。

---

## 3. 数据模型（V19__channel_directory.sql）

```sql
-- 群表扩展（频道复用 group_info）
ALTER TABLE group_info ADD COLUMN kind        TINYINT     NOT NULL DEFAULT 0;  -- 0 group / 1 channel
ALTER TABLE group_info ADD COLUMN handle      VARCHAR(32) NULL;                -- 公开标识 @handle，格式同 D42 username
ALTER TABLE group_info ADD COLUMN is_public   TINYINT(1)  NOT NULL DEFAULT 0;  -- 1=进入搜索目录
ALTER TABLE group_info ADD COLUMN description VARCHAR(512) NOT NULL DEFAULT '';
ALTER TABLE group_info ADD COLUMN pending_payment TINYINT(1) NOT NULL DEFAULT 0; -- 付费创建 saga 中间态（§5.3）
ALTER TABLE group_info ADD UNIQUE KEY uk_handle (tenant_id, handle);           -- NULL 不参与唯一

-- 公开目录（搜索数据源，与主表解耦：冗余搜索字段/可独立下架/未来容纳 bot 官方号）
CREATE TABLE public_directory (
  id           BIGINT       NOT NULL,
  tenant_id    BIGINT       NOT NULL,
  entity_type  TINYINT      NOT NULL,              -- 1 group / 2 channel
  entity_id    BIGINT       NOT NULL,              -- group_info.id
  handle       VARCHAR(32)  NOT NULL,
  name         VARCHAR(64)  NOT NULL,
  intro        VARCHAR(512) NOT NULL DEFAULT '',
  avatar       VARCHAR(255) NOT NULL DEFAULT '',
  member_count INT          NOT NULL DEFAULT 0,    -- 定时/事件回填，允许分钟级延迟
  weight       INT          NOT NULL DEFAULT 0,    -- 运营加权
  status       TINYINT      NOT NULL DEFAULT 0,    -- 0 上架 / 1 下架(审核/运营)
  create_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  update_time  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_entity (tenant_id, entity_type, entity_id),
  UNIQUE KEY uk_handle (tenant_id, handle),
  FULLTEXT KEY ft_search (name, intro) WITH PARSER ngram   -- MySQL8 内置中文 ngram，D24 不引 ES
);
```

- group 模块在建/改/下架公开实体时**同事务**维护 public_directory（同库同模块，无分布式问题）。
- `conversation.type` 增用枚举值 CHANNEL；频道创建时同现有群逻辑建 conversation(type=CHANNEL) + group_info(kind=1)。
- 订阅者 = `group_member(role=subscriber)` + `conversation_member`（未读/read_seq 复用现有机制）。角色枚举扩展：owner/admin/member/**subscriber**。
- 套餐参数（tenant_config.plan_features）：`channel_max_subscribers`(默认 5000)、`channel_create_price`(默认 1000_00 分)、`group_publish_price`(默认 500_00)、`free_channel_quota`(每用户免费额度，默认 0)、`channel_fanout_threshold`(默认 1000，§5.4)。

---

## 4. 权限矩阵（实现与 Review 对照表）

| 动作 | owner | admin | subscriber | 非订阅者 |
|------|-------|-------|-----------|---------|
| 发言（MSG_SEND） | ✅ | ✅ | ❌ 4101 | ❌ 3002 |
| 订阅/退订 | — | 退=转让后 | ✅ | 订阅 ✅（公开）|
| 邀请订阅者 | ✅ | ✅ | ✅（分享卡片/链接） | — |
| 改资料/handle | ✅ | ✅ | ❌ | — |
| 设/撤管理员、解散 | ✅ | ❌ | ❌ | — |
| 查看历史 | ✅ | ✅ | ✅ **含订阅前历史**（Telegram 式） | 公开频道预览最近 20 条（REST，未登录态不开放）|

- 发言校验位置：message 模块发送链路，`ResolveConv` 返回 conv_type=CHANNEL 时调 `GroupRpc.CheckPostPermission`（结果 Redis 缓存 60s，角色变更时删缓存 key `chperm:{conv_id}:{uid}`）。
- 群历史可见性维持现状不动（本期不改群语义）。
- 频道内**禁用**：红包（发言者理论可发，MVP 一并禁止，避免「频道红包哄抢」的风控面）、通话、已读回执展示（read_seq 照常记录但 UI 不显示）。

---

## 5. 核心流程

### 5.1 创建频道 `POST /api/v1/channels {name, handle, description, avatar, pin}`

见 §5.3 付费 saga。handle 校验：格式 `^[a-z][a-z0-9_]{5,31}$`（与 D42 同规）+ 唯一 + 词库过滤（同步 DFA，目录内容是公开曝光面，**建/改时同步审**，与消息先发后审不同）。

### 5.2 群转公开 `POST /api/v1/groups/{id}/publish {handle, intro, pin}`

仅 owner；付费同 §5.3（biz=group.publish）；成功后写 public_directory。撤下 `DELETE .../publish` 免费，删目录行+清 handle。

### 5.3 付费创建 saga（跨模块无事务，固定模式）

```
1. group 模块事务：建 group_info(kind=1, pending_payment=1) + conversation + owner 成员行
   （pending 态：不可发言、不进目录、不可被搜索）
2. 调 WalletRpc.Pay(biz_type=channel.create, biz_id=group_id, idem_key="chcreate:"+group_id, pin)
   - 免费额度内（COUNT 本人已创建频道 < free_channel_quota）→ 跳过本步
3. 成功 → 事务：pending_payment=0 + 写 public_directory → 返回客户端
   失败 → 删除步骤 1 产物（group/conversation/member 同事务删）→ 透传 wallet 错误码
4. 崩溃恢复：PendingChannelSweeper（60s）扫 pending_payment=1 且创建 >10min：
   按 idem_key 查 wallet_txn——已扣款 → 补做步骤 3；未扣款 → 删除。幂等键保证不双扣。
```

### 5.4 频道消息推送（唯一需要分叉的链路）

现有 `MsgSavedEventConsumer` 按成员全量扇出 MSG_PUSH（need_ack=true），订阅数大时是 D13 分析过的唯一线性成本。分叉规则：

```
订阅数 <= channel_fanout_threshold(1000)：走现有 MSG_PUSH 扇出，零改动
订阅数 >  threshold：降级「脏通知 + 拉取」——
  对在线订阅者推 CONV_NOTIFY（cmd=25 已有，need_ack=false，body 带 conv_id + 新 max_seq）
  客户端收到后对该会话发起增量拉取（SYNC/PullMsgs 现有机制）
  离线订阅者本就靠 SYNC_REQ，无影响
```

- 实现点：consumer 拿 GetMembers 结果时已知成员数，按阈值选路径；CONV_NOTIFY 的 body 复用现有 ConvNotify 结构（若缺 max_seq 字段则按字段号只增补上）。
- 发言频控：频道消息按 `channel:{conv_id}` 令牌桶（默认 20 条/分钟），防广播风暴。

### 5.5 订阅 / 退订

```
POST   /api/v1/channels/{id}/subscribe   -- 公开频道直接进；写 group_member(subscriber)+conversation_member
DELETE /api/v1/channels/{id}/subscribe
```

订阅数上限校验（4104）；订阅/退订**不发**群通知灰条（频道人来人往，噪音）；member_count 冗余列 `UPDATE +1/-1`，public_directory.member_count 由事件异步回填（分钟级）。

### 5.6 全局搜索 `GET /api/v1/search/global?keyword=&type=all|user|group|channel&cursor=&limit=`

group 模块聚合实现：

1. **user 段**：调用现有 `UserRpc/searchUsers` 精确匹配（D42：username 或完整手机号，**不模糊**）。
2. **group/channel 段**（public_directory，status=0）：
   - handle 精确命中（去 @ 前缀）→ 置顶；
   - `MATCH(name, intro) AGAINST(:kw IN NATURAL LANGUAGE MODE)` ngram 全文；kw 长度 <2 时退化 `name LIKE 'kw%'` 前缀（公开目录本就是公开信息，无枚举顾虑）；
   - 排序：handle 精确 > 相关性得分 > weight > member_count。
3. 分页：type=all 时每段各取前 3 + 「更多」入口；单 type 时 cursor 分页。
4. 限流：每用户 30 次/分钟（Redis 令牌桶），空结果也计数（防扫库）。
5. 点击结果 → `GET /api/v1/channels/{id}/preview`（目录信息+最近 20 条消息只读预览）→ 订阅。

> 搜索升级路径（登记 Open Question）：目录规模 >10 万行或需要拼音/同义词时，再评估 ES/OpenSearch——届时 public_directory 天然就是同步源表，接 binlog/事件流即可，接口不变。

---

## 6. im-app（Flutter）实现要点

- `features/search/` 现有模块扩展：全局搜索页（分段结果：用户/群/频道）、频道预览页。
- `features/channels/`：创建频道流程（含收银台弹层→复用 wallet PIN 键盘）、频道资料页、订阅者列表（仅 owner/admin 可见完整列表）。
- 聊天页按 `conv_type=CHANNEL` 分支：输入栏对 subscriber 隐藏（显示「订阅中/退订」栏）、无已读展示、无红包/通话入口、顶部显示订阅数。
- 会话列表：频道会话正常出现，未读逻辑复用（max_seq - read_seq）。
- 脏通知路径：收到 CONV_NOTIFY 带新 max_seq 且本地落后 → 对该会话触发增量拉取（复用现有 sync 引擎的单会话补齐入口）。
- 分享：频道卡片消息用 `CustomContent(custom_type="channel.card", payload={group_id, handle, name, avatar, member_count})`，点击进预览页。

---

## 7. 与现有决策一致性核对 / 注意事项

| 关注点 | 结论 |
|--------|------|
| D13 群 500 上限 | 不变；频道走独立上限+脏通知降级，正是 D13 预判的「二阶段大群路径」首次落地 |
| D19/D28 | 无新 Cmd；CONV_NOTIFY 复用；need_ack 语义不变（频道消息扇出仍 MSG_PUSH need_ack=true，脏通知 false） |
| D24 | 不引 ES；MySQL ngram fulltext；升级路径已留 |
| D42 | handle 规范复用；用户搜索不放宽 |
| D46 | 付费走 WalletRpc.Pay + saga（§5.3），幂等键=group_id |
| D16 | 频道消息天然过 moderation（走消息管道）；目录资料同步审 |
| D3 | 新表/新列 tenant_id 齐备 |

易错清单：

1. `uk_handle` 在 group_info 与 public_directory 双处，**handle 变更必须同事务改两处**。
2. CheckPostPermission 缓存失效：设/撤管理员、owner 转让时主动 DEL 相关 key，不能只靠 60s TTL（撤权后仍能发言 1 分钟不可接受——**裁决：撤权路径主动失效，授权路径可容忍 TTL**）。
3. 脏通知阈值判断用 GetMembers 返回的成员数，别再发一次 COUNT 查询。
4. 频道 preview 接口必须只读且限流，它是未订阅者可达的最大暴露面。
5. saga 步骤 1 的 pending 频道要在 ListMemberConvs 中过滤（不出现在会话列表）。
6. ConvType 新枚举值对旧客户端 = unknown 枚举：Flutter/Web 收到未知 conv_type 按只读会话兜底渲染，**旧版本先发布兼容代码再放量服务端**（发布顺序写进任务卡）。

## 8. 二阶段 / Open Questions（登记 CLAUDE.md）

- 付费订阅频道（订阅费+平台抽成分账，依赖 MERCHANT 结算语义）
- 频道评论区（Telegram discussion group 联动）
- 搜索 ES 升级阈值；拼音/同义词
- 频道订阅数 >5000 的读扩散推送（脏通知已铺路，剩会话列表行热点）
- 目录人工审核流（管理后台上/下架工作流）
