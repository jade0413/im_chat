# 客服会话（CS Session）设计文档

> 状态：待实现 | 讨论日期：2026-06-13 | 关联决策：D29~D34
>
> 任务拆分见 TASKS.md T30~T33。

---

## 1. 定位

同一个用户账号可以：
- 作为普通 IM 用户，与好友进行 C2C / GROUP 聊天（现有能力）
- 作为坐席，接待从 H5 widget / 小程序嵌入渠道进来的访客

两种模式底层共用同一套消息/推送/outbox 基础设施，通过 `conversation.type = CS` 区分，互不影响。

---

## 2. 访客身份模型

### 2.1 关键决策

访客是 `user.user_type = visitor` 的特殊用户，**不是独立实体**。
- D6 已预留此枚举值，本次激活。
- 消息表 `sender_id` 不变，直接存访客的 user_id。
- 网关鉴权不变，访客持 JWT（与普通用户完全相同的校验流程）。

### 2.2 访客持久化

新增 `visitor_profile` 表，负责 localStorage token ↔ user_id 的映射：

```sql
CREATE TABLE visitor_profile (
  id            BIGINT      NOT NULL,
  tenant_id     BIGINT      NOT NULL,
  visitor_token VARCHAR(64) NOT NULL,      -- localStorage 里存的 UUID
  user_id       BIGINT      NOT NULL,      -- user 表里 user_type=visitor 的记录
  display_name  VARCHAR(64) NOT NULL,      -- "访客XXXX"，四位随机字母数字
  created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_token (tenant_id, visitor_token)
) ENGINE=InnoDB COMMENT='访客 localStorage token 到用户的映射';
```

### 2.3 访客首次 / 再次接入流程

```
首次进入 widget（localStorage 无 token）：

  // 前端首次打开：生成 UUID 存 localStorage
  visitorToken = crypto.randomUUID()   // 前端生成并写入 localStorage
  POST /api/v1/cs/widget/sessions  {visitorToken: "uuid"}
  →  visitor_profile 无记录
  →  生成 displayName = "访客XXXX"
  →  gRPC ProvisionVisitorUser → 创建 user(user_type=VISITOR, account="visitor_"+userId)
  →  写 visitor_profile(tenant_id, visitorToken, userId, displayName)
  →  gRPC IssueVisitorToken → 签发 JWT（无 platform_class）
  →  gRPC FindOrCreateCsConv → 创建 CS conversation（cs_status=1/open）
  →  返回 {accessToken, conversationId, visitorId, displayName, isNewConversation=true}

  // 再次进入（localStorage 有 token）：
  POST /api/v1/cs/widget/sessions  {visitorToken: "same-uuid"}
  →  visitor_profile 有记录 → 取 userId, displayName
  →  gRPC IssueVisitorToken → 签发新 JWT
  →  gRPC FindOrCreateCsConv → 找到 open/assigned 会话返回，或 resolved 后新建
  →  返回 {accessToken, conversationId, ..., isNewConversation=false}
```

### 2.4 续旧规则（基础款）

- 有 `status IN (open, assigned)` 的会话 → 直接续用最近一条
- 全部 `resolved` → 创建新会话（resolved 不续）
- 逻辑简单，不考虑多会话并存

### 2.5 访客显示名

格式：`访客` + 4 位大写字母数字（如 `访客A3K9`），生成时随机，不可修改。

---

## 3. CS 会话状态机

```
  建会话
     │
     ▼
  [open]   ──── 坐席认领 ────►  [assigned]
     │                              │
     │                         坐席结束/访客超时
     │                              │
     └──────────────────────────────▼
                               [resolved]

  resolved 后访客再发消息 → 创建新会话（status=open）
```

状态值约定（`conversation.cs_status` 列，V1 schema 已预留）：

| 值 | 含义 |
|---|---|
| 1 | open（待接待） |
| 2 | assigned（已接待中） |
| 3 | resolved（已结束） |

> ⚠️ 1-based，与 V1 schema DDL 中 `cs_status` 列的注释对齐；CsConstants 中 `CS_STATUS_OPEN=1 / ASSIGNED=2 / RESOLVED=3`。

状态转移约束：
- 只允许正向流转（open→assigned / assigned→resolved / open→resolved）
- resolved 不能回退

---

## 4. API 设计

> 所有接口统一响应结构：`{"code": 0, "message": "ok", "data": {...}, "traceId": "...", "timestamp": 1234567890}`
>
> `code != 0` 时 `data` 为 null，`message` 为错误描述。

---

### 4.1 访客接入（T31 — 已实现）

#### `POST /api/v1/cs/widget/sessions`

访客打开 widget 时调用，**无需任何认证**，仅凭 `X-Tenant-Id` 标识租户。

**请求 Header**

| Header | 说明 |
|--------|------|
| `X-Tenant-Id` | 租户 ID（JS snippet 嵌入时写死，如 `1`） |
| `Content-Type` | `application/json` |

**请求 Body**

```json
{
  "visitorToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `visitorToken` | string（必填，≤64 字符） | 客户端 localStorage 存储的 UUID。**首次访问时由前端生成**（`crypto.randomUUID()`），后续访问读取同一值。 |

**响应 data**

```json
{
  "accessToken":       "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken":      "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType":         "Bearer",
  "expiresIn":         7200,
  "conversationId":    1234567890123456789,
  "visitorId":         9876543210987654321,
  "displayName":       "访客A3K9",
  "isNewConversation": true,
  "csStatus":          1
}
```

| 字段 | 说明 |
|------|------|
| `accessToken` | 用于 WebSocket 连接鉴权，有效期 2h（不存 localStorage） |
| `refreshToken` | 续签用，访客场景通常用不到 |
| `expiresIn` | access token 有效期秒数（默认 7200） |
| `conversationId` | 当前 CS 会话 ID（后续发消息时带上此 ID） |
| `visitorId` | 访客的 user_id（消息 sender_id） |
| `displayName` | 访客昵称，如 "访客A3K9"（固定，不可修改） |
| `isNewConversation` | `true`=新建会话，`false`=续旧 open/assigned 会话 |
| `csStatus` | 会话当前状态：`1`=open（待接待），`2`=assigned（已接待中） |

**前端接入流程**

```javascript
// 1. 读取或生成 visitorToken
let visitorToken = localStorage.getItem('im_visitor_token');
if (!visitorToken) {
  visitorToken = crypto.randomUUID();
  localStorage.setItem('im_visitor_token', visitorToken);
}

// 2. 请求接入
const res = await fetch('/api/v1/cs/widget/sessions', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'X-Tenant-Id': TENANT_ID },
  body: JSON.stringify({ visitorToken })
});
const { data } = await res.json();

// 3. 建立 WS 连接（accessToken 存内存，不存 localStorage）
const ws = new WebSocket(`wss://gateway.example.com/ws?token=${data.accessToken}`);

// 4. 打开对话窗口，使用 data.conversationId 发消息
```

**幂等性说明**
- 同一 `visitorToken` 多次调用：始终返回相同的 `visitorId` + 续旧会话（或新建后的会话）
- Token 过期后重新调用接口即可获取新的 `accessToken`，`conversationId` 不变

**WS 消息收发**

访客连上网关后，消息收发完全复用现有协议（`MSG_SEND / SYNC_REQ / MSG_RECV_ACK`），
`conversation_id` 即上方返回的 `conversationId`。

---

### 4.2 坐席端（T32/T33 — 待实现）

> **鉴权**：所有坐席接口需要 `Authorization: Bearer {jwt}` 且 JWT 对应用户 `is_agent=1`。

#### `POST /api/v1/cs/conversations/{conv_id}/assign`

坐席认领会话（open → assigned）。

```json
// 请求 Body：无（当前 user 即认领人）
// 响应 data：
{ "convId": 123, "csStatus": 2, "agentId": 456 }
```

#### `POST /api/v1/cs/conversations/{conv_id}/resolve`

结束会话（assigned → resolved）。

```json
// 响应 data：
{ "convId": 123, "csStatus": 3 }
```

#### `GET /api/v1/cs/inbox`

坐席 inbox 列表（open 待接待 + 本人 assigned）。

```
Query: status=open|assigned  limit=20  offset=0
```

每条返回 `CsConvItem`，含访客显示名、`visitor_online`（访客是否在线）、`visitor_read_seq`（访客已读位点，用于坐席端"已读"展示）、`last_msg_*`、`max_seq`。
列表对访客资料、访客成员、在线状态做**批量查询**（一次 IN 查 `visitor_profile` + 一次 MGET 查 Redis 路由 + 一次 IN 查 `conversation_member`），避免 limit×(DB+Redis) 的 N+1 放大（D39）。

---

### 4.3 坐席在线状态（T34 — 待实现）

#### `PUT /api/v1/cs/agent/status`

```json
// 请求：{ "status": 1 }   // 0=offline 1=online 2=busy
// 响应 data：{ "agentStatus": 1 }
```

#### `GET /api/v1/cs/widget/availability`（公开，无需鉴权）

Widget 查询租户是否有坐席在线，决定是否显示"在线/离线"提示。

```
Query: tenantId=1
Response data: { "online": true, "agentCount": 3 }
```

---

### 4.4 Widget 配置（T36 — 待实现）

#### `GET /api/v1/cs/widget/config`（公开）

Widget 初始化时拉取展示配置。

```json
{
  "color":       "#1890FF",
  "welcomeMsg":  "有什么可以帮您？",
  "offlineMsg":  "我们现在不在线，留言我们会尽快回复",
  "displayName": "在线客服",
  "position":    "bottom-right",
  "poweredBy":   true
}
```

---

### 4.5 权限矩阵

| 接口 | 鉴权要求 |
|------|---------|
| `POST /widget/sessions` | `X-Tenant-Id` header，无需登录 |
| `GET /widget/config` | 无需鉴权（公开接口） |
| `GET /widget/availability` | 无需鉴权（公开接口） |
| `PUT /agent/status` | JWT（用户 `is_agent=1`） |
| `GET /inbox` | JWT（`is_agent=1`） |
| `POST /conversations/{id}/assign` | JWT（`is_agent=1` + conv 属于当前 tenant） |
| `POST /conversations/{id}/resolve` | JWT（`is_agent=1` + `agent_id` = 当前 user） |
| `GET /conversations/{id}/notes` | JWT（`is_agent=1` + `agent_id` = 当前 user；非 open，结单后本人仍可看） |
| `POST /conversations/{id}/notes` | JWT（`is_agent=1` + `agent_id` = 当前 user；非 open，结单后本人仍可写） |

---

## 5. 推送路由扩展

消息推送模块订阅 `msg.saved.*`，判断 `conversation.type = CS` 后按状态路由：

| 会话状态 | 推送目标 |
|---|---|
| open（待接待） | 当前 tenant 所有在岗坐席（`agent_status IN(1,2)`，online+busy）+ 访客自身（D35/D39） |
| assigned（接待中） | 仅 `conversation.agent_id` 对应的坐席 + 访客自身 |
| resolved（已结束） | 仅访客（坐席侧依赖 inbox 查看历史） |

坐席收到的推送帧与普通消息相同（`MsgPush`），前端通过 `conv_type = CS` 渲染不同铃声。

---

## 6. Schema 变动汇总

| 变动 | 位置 | 说明 |
|---|---|---|
| `user.user_type` 枚举值激活 `visitor` | `user` 表 | 已在 DDL 预留 |
| `user.is_agent TINYINT(1) DEFAULT 0` | `user` 表 | 新增，标识坐席身份 |
| `conversation.cs_status`（已在 V1 预留） | `conversation` 表 | 1=open/2=assigned/3=resolved，V6 不再 ALTER |
| `conversation.agent_id BIGINT NULL` | `conversation` 表 | 新增，认领后写入；**resolve 不清空**，保留为"处理坐席"记录（D38） |
| 新增 `visitor_profile` 表 | 新增 | 见 §2.2 |
| 新增 `cs_internal_note` 表 | 新增 | 坐席内部备注，仅坐席可见，不进 message/outbox |

Flyway 迁移：`V6__cs.sql`、`V7__cs_internal_note.sql`

---

## 7. 模块设计

### 7.1 新模块：`im-cs-service`

依赖：`im-common` + `im-proto-java`，不依赖其他业务模块。

包结构（`com.im.cs`）：

```
visitor/        -- visitor_profile 实体/mapper/服务
                   VisitorService（创建访客、续旧判断、JWT 签发）
widget/         -- 访客接入 REST
                   WidgetSessionController（POST /widget/sessions）
inbox/          -- 坐席工作台 REST
                   CsInboxController（GET /inbox、POST assign/resolve）
push/           -- MQ 消费：msg.saved → CS 推送路由
                   CsMsgSavedConsumer
config/         -- MQ / Rabbit 配置
```

### 7.2 跨模块交互

```
im-cs-service → im-user-service（gRPC UserRpc）  创建访客用户
im-cs-service → im-conversation-service（gRPC ConversationRpc）  创建/查询 CS 会话
im-cs-service → im-push-service（gRPC PushRpc）  推送到坐席/访客
im-cs-service 订阅 MQ msg.saved.*   判断 CS 消息路由
```

### 7.3 访客 JWT 签发

复用 `JwtService`（已在 im-user-service 中），由 `im-cs-service` 调用 `UserRpc.IssueVisitorToken`（新增 gRPC 方法），统一由 user-service 完成 JWT 签发，不在 cs-service 中直接操作 JWT 密钥。

---

## 8. 双模式隔离保证

Flutter App 端：
- "聊天" tab：`conversation.type IN (C2C, GROUP)` — 现有逻辑不动
- "客服" tab：`conversation.type = CS AND agent_id = current_user` 或 `status = open` — 新增视图

访客端 JWT claims：
- `user_type = visitor` → 访客只能发消息到自己的 CS 会话
- REST 接口校验 user_type，visitor 账号无法调用 inbox / assign / resolve 接口

---

## 9. 实现约束

1. `im-cs-service` 不直接依赖 `im-user-service` / `im-conversation-service` / `im-push-service` 编译产物，跨模块只走 gRPC。
2. 所有 SQL 不手写 `tenant_id` 条件，依赖 MyBatis 租户拦截器（visitor_profile 和 conversation 均有 tenant_id 列）。
3. 访客 user 记录 `user_type = visitor`，普通用户 `user_type = member`；坐席标识用 `is_agent = true`，与 user_type 正交（坐席仍是 member）。
4. CS 会话消息的 Outbox 流程与普通消息完全相同，不新增消息链路。
5. 访客 JWT 有效期 7 天（长于普通用户 access token），减少无感知重认证频率。

---

---

## 11. 坐席在线状态

### 11.1 状态值

`user` 表新增 `agent_status TINYINT DEFAULT 0`：

| 值 | 含义 | 行为 |
|---|---|---|
| 0 | offline | 不接收 CS 推送，widget 显示"离线"，不可认领 |
| 1 | online | 正常接收推送，可认领会话 |
| 2 | busy | 仍接收 open 待接待推送、仍可认领（D35/D39：online+busy 均为"在岗"）；语义是"手头较忙"，由前端/分配策略弱化派单，但不阻断认领 |

### 11.2 API

```
PUT /api/v1/cs/agent/status  {status: 0|1|2}
  → 更新 user.agent_status（需要 is_agent=true）

GET /api/v1/cs/widget/availability?tenant_id=xxx  （公开接口，无需 auth）
  → {available: true/false, hint: "通常 5 分钟内回复"}
```

### 11.3 对推送路由的影响

T33 推送路由扩展：open 会话推送时，推 `agent_status IN(1,2)` 的在岗坐席（online+busy，D35/D39），offline 坐席不推（但消息仍存库，上线后可从 inbox 看到）。

### 11.4 Widget 展示

- 有任一坐席 online 或 busy（在岗）→ 显示"我们在线，发消息给我们"
- 全部 offline → 显示"我们现在不在线，留言我们会回复您"，并切换为离线留言模式

---

## 12. 离线留言策略

### 12.1 核心原则

**消息链路不变**：访客无论坐席是否在线都可以发消息，消息正常存库，不做特殊处理。"离线留言"是展示层概念，不是独立消息类型。

### 12.2 widget 侧行为

```
坐席全部 offline：
  GET /widget/availability → {available: false}
  widget 显示："留言给我们，我们会尽快回复"
  访客继续正常发消息（走同一 WS / HTTP 通道）
  会话 status 保持 open，等待坐席上线后认领
```

### 12.3 坐席上线通知

坐席将 `agent_status` 切为 online 时：
- 系统查询当前 tenant 下 `status=open` 且 `created_at/last_msg_at > now()-24h` 的未认领会话数
- 若有未处理会话 → 下发系统消息通知：「您有 N 个待处理客服会话」
- 坐席端在 inbox tab 显示角标数量

### 12.4 邮件通知（MVP 简化版）

MVP 阶段：坐席长时间离线期间有新访客留言时，不做实时邮件推送（成本高、依赖邮件服务）。
二阶段：接入邮件通道，按租户配置通知频率（如每小时汇总一次）。

---

## 13. Widget 嵌入配置 + JS Snippet

### 13.1 新增表 `widget_config`

```sql
CREATE TABLE widget_config (
  id              BIGINT      NOT NULL,
  tenant_id       BIGINT      NOT NULL,
  color           VARCHAR(16) NOT NULL DEFAULT '#1890FF',   -- 品牌色
  welcome_msg     VARCHAR(128) NOT NULL DEFAULT '有什么可以帮您？',
  offline_msg     VARCHAR(128) NOT NULL DEFAULT '我们现在不在线，留言我们会尽快回复',
  display_name    VARCHAR(64) NOT NULL DEFAULT '在线客服',  -- widget 头部名称
  position        VARCHAR(16) NOT NULL DEFAULT 'bottom-right',  -- bottom-right|bottom-left
  powered_by      TINYINT(1)  NOT NULL DEFAULT 1,           -- 是否显示"由XX提供支持"徽标
  created_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='Widget 配置';
```

### 13.2 API

```
GET  /api/v1/cs/widget/config          （公开接口，按 tenant_id 查询）
PUT  /api/v1/cs/widget/config  {color, welcome_msg, ...}  （租户管理员）
```

### 13.3 JS Snippet

企业完成注册后，管理后台展示以下代码，复制粘贴即可使用：

```html
<!-- 嵌入到网站 </body> 前 -->
<script>
  window.IMCSConfig = { tenantId: "YOUR_TENANT_ID" };
</script>
<script src="https://yourplatform.com/widget.js" async></script>
```

`widget.js` 加载后：
1. 调用 `GET /api/v1/cs/widget/config?tenant_id=xxx` 读取配置
2. 调用 `GET /api/v1/cs/widget/availability?tenant_id=xxx` 判断在线状态
3. 渲染 widget 气泡，点击后展开聊天窗口
4. 调用 `POST /api/v1/cs/widget/sessions` 创建/续旧会话
5. 建立 WS 连接（使用 visitor JWT）

`widget.js` 是前端工程（uni-app/H5，D14），后端只提供配置和会话接口。

### 13.4 「由 XX 提供支持」徽标

`widget_config.powered_by = 1`（默认开启）时，widget 底部展示链接徽标。
这是免费版的病毒传播机制，未来付费版可关闭。

---

## 14. 客服系统借鉴能力与当前边界

参考 Chatwoot / Intercom / Zendesk 的通用工作台模型，客服能力按优先级分层：

| 能力 | 作用 | 当前阶段 |
|---|---|---|
| 队列与认领 | 多坐席共享待接待池，避免访客消息丢失 | 已落地：open 队列 + 在岗坐席（online/busy）认领 |
| 内部备注 | 坐席交接、主管质检、复盘时沉淀上下文 | 已落地：`cs_internal_note`，本人认领后可见，结单后仍可补充（D38） |
| 访客状态与已读 | 坐席判断访客是否还在线、消息是否已看 | 已落地：visitor online + visitor read_seq |
| 转接 | 当前坐席把会话交给另一个坐席/团队 | 二阶段：需定义原坐席历史访问权 |
| 标签/优先级 | 队列筛选、紧急问题前置 | 二阶段：可先加 conversation 级标签/priority |
| SLA | 首响/结单倒计时和超时统计 | 二阶段：需租户配置和报表口径 |
| 技能组/自动分配 | 规模化团队的路由策略 | 二阶段：依赖坐席组和容量模型 |

权限边界：未认领会话只展示队列摘要和最新消息摘要；完整聊天记录、内部备注、发送回复必须在认领后开放。转接后新坐席应获得完整历史；原坐席是否继续保留访问权需要按审计策略单独决定。

---

## 15. 未纳入 MVP 的能力（二阶段）

- 坐席自动分配（轮询/最少会话/技能组）
- resolved 后访客再发消息续旧（当前固定为新建）
- 坐席转接
- 会话标签与优先级
- 客服统计报表
- 访客身份跨渠道合并（同一客户从 H5 和小程序两个渠道来）
