# 朋友圈设计文档（im-moment-service）

> 状态：待实现 | 讨论日期：2026-07-03 | 关联决策：D47（本文档）、依赖 D40(好友关系)/D16(先发后审)/D10(文件直传)
>
> 实现按本文档执行；如需偏离，先在 CLAUDE.md Open Questions 提出再改文档（协作铁律）。

---

## 1. 定位与边界

微信式朋友圈：好友可见的图文动态 + 点赞/评论。**开源参考结论：OpenIM 核心不含朋友圈**（其生态里是独立业务层），Tinode/Matrix 亦无对应物——本设计为自研，采用业界标准 Feed 模型并按本项目规模裁剪。

边界（已定）：

- 参与者仅 `user_type=member`；visitor/agent 无朋友圈。
- **仅好友可见、仅好友可互动**；非好友访问他人主页不可见动态（陌生人可见 N 条为租户配置 `stranger_moment_count`，默认 0，MVP 不实现展示逻辑只留配置位）。
- 可见性 MVP：好友可见 / 仅自己；**分组可见、提醒谁看、不看他/不让他看 → 二阶段**（表结构预留见 §3 注）。
- 朋友圈**不是消息**：不进 message/conversation/seq 体系，独立域独立表。互动红点走轻量通知（§6），离线一致性靠拉取对齐（真相在表，D40 同款哲学）。
- 视频动态：MVP 支持单视频（复用 file-service 转码链路），九宫格图最多 9 张。

### Feed 模型裁决：读扩散（拉模式）

| 方案 | 微信（写扩散+相册） | 本项目（读扩散） |
|------|--------------------|-----------------|
| 发布 | 写 N 份 timeline | 写 1 行 moment |
| 拉 feed | 读自己 timeline | `user_id IN (好友集) ORDER BY id DESC` |
| 适用 | 亿级用户 | D4 规模（万级在线、好友数百）绰绰有余 |

理由：好友上限数百 + MySQL `(tenant_id, user_id, id)` 联合索引，IN 几百个值的倒序 limit 查询代价完全可控；省去 timeline 写扩散的存储与失效维护。**好友数 >2000 或 feed P99 超标时再引入 timeline 写扩散**（预留升级路径，不建表）。

### 模块

新模块 **`im-moment-service`**。依赖：UserRpc（好友集/资料）、file-service（媒体直传，走现有 REST 不加 RPC）、PushRpc（在线红点推送）、moderation（经 MQ 事件，见 §7）。好友集读取走 `UserRpc` 新增接口（§5.3），禁止直读 friend 表（模块铁律）。

---

## 2. 协议改动

### 2.1 frame.proto —— 新增一个 Cmd（业务段 10-29 内取 27）

```proto
MOMENT_NOTIFY = 27;  // S->C body=MomentNotify（body/moments.proto）
                     // need_ack=false，纯 best-effort 红点信令；离线/丢失靠进页拉取对齐
```

新建 `body/moments.proto`（Java+客户端编译，网关照常透传零改动）：

```proto
syntax = "proto3";
package im.body.v1;
option java_package = "com.im.proto.body";
option java_multiple_files = true;

message MomentNotify {
  int32 kind = 1;             // 1=好友发了新动态(feed 入口红点) 2=收到互动(赞/评论，我的消息红点)
  int64 moment_id = 2;        // kind=2 时有效
  int64 actor_user_id = 3;    // 触发者（kind=1 发布者 / kind=2 互动者）
  int64 unread_interactions = 4; // kind=2 时服务端当前未读互动数（客户端直接显示，避免自己累计出错）
}
```

### 2.2 error.proto —— 新增 3 个，取 31xx（挂 3xxx 内容/会话域子段；朋友圈非资金不入 8xxx。error.proto 首行分段注释同步更新）

```proto
MOMENT_NOT_FOUND = 3101; MOMENT_NOT_FRIEND = 3102;   // 非好友不可见/不可互动
MOMENT_COMMENT_CLOSED = 3103;                        // 动态已删除/被审核撤下时互动
```

### 2.3 internal.proto

```proto
// UserRpc 增加（好友集是朋友圈可见性判定的高频依赖）
rpc GetFriendIds (GetFriendIdsReq) returns (GetFriendIdsResp);
message GetFriendIdsReq  { int64 user_id = 1; }
message GetFriendIdsResp { repeated int64 friend_ids = 1; }

// 新增 MomentRpc（moderation 违规撤下用）
service MomentRpc {
  rpc RevokeMoment (RevokeMomentReq) returns (RevokeMomentResp);
  rpc RevokeMomentComment (RevokeMomentCommentReq) returns (RevokeMomentResp);
}
message RevokeMomentReq { int64 tenant_id = 1; int64 moment_id = 2; int32 reason = 3; }
message RevokeMomentCommentReq { int64 tenant_id = 1; int64 comment_id = 2; int32 reason = 3; }
message RevokeMomentResp { int32 code = 1; }
```

### 2.4 events/events.proto（MQ）

```proto
message MomentCreatedEvent {   // outbox → moderation 消费（审文本+图）
  int64 tenant_id = 1; int64 moment_id = 2; int64 user_id = 3;
  string text = 4; repeated string media_keys = 5;
}
message MomentCommentCreatedEvent {
  int64 tenant_id = 1; int64 comment_id = 2; int64 moment_id = 3;
  int64 user_id = 4; string content = 5;
}
```

---

## 3. 数据模型（V18__moment.sql）

```sql
CREATE TABLE moment (
  id          BIGINT       NOT NULL,              -- Snowflake（天然按时间有序，feed cursor 直接用 id）
  tenant_id   BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  text        VARCHAR(2000) NOT NULL DEFAULT '',
  media       JSON         NULL,   -- [{type:1图/2视频, object_key, thumb_key, w, h, duration_ms}] 最多 9
  location    VARCHAR(128) NOT NULL DEFAULT '',
  visibility  TINYINT      NOT NULL DEFAULT 0,    -- 0 好友可见 / 1 仅自己；2+ 预留分组可见（二阶段）
  like_count  INT          NOT NULL DEFAULT 0,    -- 冗余计数，与互动同事务维护
  comment_count INT        NOT NULL DEFAULT 0,
  status      TINYINT      NOT NULL DEFAULT 0,    -- 0 normal / 1 deleted(用户删) / 2 revoked(审核撤)
  create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_author (tenant_id, user_id, id DESC)    -- 个人相册 + feed IN 查询共用
);

CREATE TABLE moment_like (
  tenant_id   BIGINT NOT NULL,
  moment_id   BIGINT NOT NULL,
  user_id     BIGINT NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (moment_id, user_id)                -- 取消点赞物理删（微信式不留痕）
);

CREATE TABLE moment_comment (
  id          BIGINT       NOT NULL,
  tenant_id   BIGINT       NOT NULL,
  moment_id   BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  reply_to_user_id BIGINT  NOT NULL DEFAULT 0,    -- 0=直接评论；否则「A 回复 B」
  reply_to_comment_id BIGINT NOT NULL DEFAULT 0,
  content     VARCHAR(1000) NOT NULL,
  status      TINYINT      NOT NULL DEFAULT 0,    -- 0 normal / 1 deleted / 2 revoked
  create_time DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_moment (moment_id, id)
);

CREATE TABLE moment_interaction (                  -- 「我的消息」列表（谁赞/评了我）
  id          BIGINT NOT NULL,
  tenant_id   BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,                   -- 被通知人 = moment 作者 或 被回复人
  actor_user_id BIGINT NOT NULL,
  moment_id   BIGINT NOT NULL,
  type        TINYINT NOT NULL,                    -- 1 like / 2 comment / 3 reply
  comment_id  BIGINT NOT NULL DEFAULT 0,
  read_flag   TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_owner (tenant_id, owner_user_id, read_flag, id DESC)
);

ALTER TABLE user ADD COLUMN moment_visible_days INT NOT NULL DEFAULT 0;      -- 0 全部 / 3 / 30 / 180
ALTER TABLE user ADD COLUMN moment_cover VARCHAR(255) NOT NULL DEFAULT '';   -- 主页封面 object_key
```

> 分组可见预留说明：`visibility` 枚举留 2=白名单/3=黑名单，配套 `moment_visibility_scope(moment_id, user_id, mode)` 表**二阶段再建**，本期不建空表。

Redis：`moment:feed_ts:{t}:{uid}` = 好友圈最新动态毫秒时间戳（发布时对好友集逐个 SET，用于 feed 入口红点比较）；`moment:view_ts:{t}:{uid}` = 用户最后浏览 feed 时间。均可丢失（丢失=红点少亮一次，可接受）。

---

## 4. 核心流程

### 4.1 发布 `POST /api/v1/moments {text, media, location, visibility, client_id}`

1. 校验：text/media 至少其一；media ≤9 且 object_key 归属校验（file_meta.uploader=本人，防盗链他人文件）；client_id 幂等（Redis SETNX 24h）。
2. 事务：INSERT moment + outbox(MomentCreatedEvent)。
3. 提交后异步（虚拟线程，失败仅记日志）：
   - `GetFriendIds` → 更新好友的 `moment:feed_ts` → `PushRpc.PushToUsers(cmd=MOMENT_NOTIFY, kind=1, need_ack=false)` 只发在线端。
   - visibility=仅自己 时跳过本步。

### 4.2 拉 Feed `GET /api/v1/moments/feed?cursor=&limit=20`

1. `ids = GetFriendIds(me) + me`；好友集结果进程内缓存 30s（好友变更容忍 30s 延迟）。
2. `SELECT * FROM moment WHERE tenant_id=? AND user_id IN (:ids) AND status=0 AND visibility=0 AND id<:cursor ORDER BY id DESC LIMIT 40`（超拉一倍再过滤）。自己的动态含 visibility=1。
3. 应用层过滤各作者 `moment_visible_days` 窗口（作者设置批量查询带缓存）；不足 limit 继续用最后 id 补页，直到满页或扫穿窗口下界。
4. 批量装配：作者资料（UserRpc.GetUsers）、每条的点赞列表+评论列表（§4.4 可见性过滤）、`liked_by_me`。
5. 返回 `{items, next_cursor}`；`PUT /api/v1/moments/feed/viewed` 更新 view_ts 熄红点。

### 4.3 互动

```
点赞   POST   /api/v1/moments/{id}/like      -- INSERT moment_like（撞 PK=幂等）+ like_count+1
取消赞 DELETE /api/v1/moments/{id}/like      -- 物理删 + like_count-1（条件 count>0）
评论   POST   /api/v1/moments/{id}/comments {content, reply_to_comment_id?}
删评论 DELETE /api/v1/moments/comments/{cid} -- 仅评论者本人或 moment 作者
删动态 DELETE /api/v1/moments/{id}           -- 仅作者；status=1 软删，互动一并不可见
```

互动前置校验（每个写接口都要）：moment 存在且 status=0（否则 3103）→ **actor 与作者是好友或 actor==作者**（否则 3102）→ reply 时被回复评论存在且未删。
互动事务内：写 moment_interaction（owner=作者；reply 时额外一行 owner=被回复人，排重 owner==actor 不写）→ 提交后 PushRpc 推 MOMENT_NOTIFY(kind=2) 给 owner 在线端。评论另写 outbox(MomentCommentCreatedEvent) 供审核。

### 4.4 互动可见性（微信核心规则，容易做错）

viewer 看 moment M（作者 A）时，点赞/评论条目（actor=C）可见当且仅当：
`viewer==A || viewer==C || C ∈ friends(viewer)`（**共同好友规则**）。

- 实现：服务端装配时按 viewer 好友集过滤，**绝不下发全量让客户端过滤**（否则泄露非好友昵称/关系）。
- `like_count/comment_count` 冗余列是全量计数，仅作者视角直接展示；他人视角展示过滤后条数（列表已过滤，前端 count=列表长度即可，不用冗余列）。
- 「A 回复 B」双方都必须对 viewer 可见才展示该条，否则整条隐藏。

### 4.5 个人主页 `GET /api/v1/moments/user/{uid}?cursor=`

- uid==me：全量（含仅自己）。
- 好友：status=0 且 visibility=0，且套用作者 visible_days。
- 非好友：返回空列表 + `restricted=true`（配置位 `stranger_moment_count` 留待二阶段）。

### 4.6 我的消息 `GET /api/v1/moments/interactions?cursor=` + `PUT .../interactions/read`

moment_interaction 倒序分页，进入页置 read_flag=1（`UPDATE ... WHERE owner=me AND read_flag=0`）。

---

## 5. 审核（复用 D16 管道）

- moderation（挂 message 模块）新增消费 `MomentCreatedEvent`/`MomentCommentCreatedEvent`：文本走 DFA 词库；图片调 ContentAuditProvider（MVP 空实现）。
- 判违规 → 调 `MomentRpc.RevokeMoment/RevokeMomentComment`（status=2）+ moderation_log 落库（复用现有表，message_id 列语义扩展为 biz_id，加 `biz_type` 列区分 message/moment/comment——**随本期迁移加列**）。
- 撤下后各端表现：feed/主页自然消失（查询过滤 status），无需推送；作者收到 SYSTEM 会话通知（复用 `SendSystemNotification`，event_type=`moment.revoked`）。

---

## 6. im-app（Flutter）实现要点

- `features/moments/`：feed 页（SliverList + 九宫格 `GridView`、视频封面播放）、发布页（图片选择/压缩复用现有 media_preprocessor、九图排序）、个人相册页（封面+时间轴）、我的消息页、可见范围设置页。
- `data/moments/`：MomentApi/MomentRepository/models；红点状态 `MomentBadgeNotifier` 监听 WS `MOMENT_NOTIFY`（在 im_engine 帧路由器注册 cmd=27，**响应帧不需要 ack**）+ App 启动/回前台时拉 `GET /api/v1/moments/unread-hint`（返回 {new_feed:bool, unread_interactions:int}，服务端由 feed_ts/view_ts + interaction 未读数算出——此接口补上 WS 丢失的对齐路径）。
- 媒体上传：复用 file-service presign 直传；发布接口只传 object_key。
- 列表状态：点赞本地乐观更新，失败回滚并 toast。

---

## 7. 与现有决策一致性核对 / 注意事项

| 关注点 | 结论 |
|--------|------|
| D19 网关 | 新增 Cmd=27 仅枚举值，网关透传零改动 |
| 消息体系 | 完全隔离：不占 conversation/seq/outbox 推送链路（仅红点 best-effort 复用 PushRpc） |
| D40 | 红点/互动真相在表，WS 信令可丢，拉取对齐 |
| D16 | 先发后审同哲学；moderation_log 加 biz_type 列 |
| D3 | 全表 tenant_id；拦截器注入 |
| 模块铁律 | 好友集走 UserRpc.GetFriendIds，新 RPC 而非直读 friend 表 |

易错清单：

1. 共同好友过滤（§4.4）必须在服务端做，且「回复对」两端都校验。
2. feed IN 查询必须带 `tenant_id` 打头索引；friendIds 为空直接返回空页（别发 `IN ()`）。
3. 软删动态的媒体文件不删（保留策略随 §13.5 统一治理）；用户注销时清理。
4. like_count 并发：`UPDATE moment SET like_count=like_count+1`（行级原子），禁止读改写。
5. MOMENT_NOTIFY 推送失败/离线**不补推**——unread-hint 拉取是唯一对齐真相。
6. 发布幂等 client_id 必须校验，弱网下用户连点 = 重复发布事故高发点。
7. 视频动态走 file_transcode_job 现有链路，发布时允许 transcoding 状态占位（客户端显示处理中）。

## 8. 二阶段 / Open Questions（登记 CLAUDE.md）

- 分组可见/提醒谁看/不看他/不让他看（visibility 枚举+scope 表已预留语义）
- 陌生人可见 N 条（stranger_moment_count 配置位）
- 写扩散 timeline 升级阈值与方案（好友 >2000 或 feed P99 超标触发）
- 朋友圈广告位/租户运营位（商业化，视产品）
