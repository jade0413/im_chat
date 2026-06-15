# im-project 架构设计文档 v0.1

> 状态：讨论稿（2026-06-13）。决策编号 D1~D10 见根目录 CLAUDE.md。

## 1. 目标与定位

一套多租户即时通讯系统，双形态输出：

1. **IM 形态**：登录、单聊、群聊、离线消息、会话列表、已读回执、图片/语音
2. **客服形态（SaaS）**：租户接入自己的网站/App，访客发起会话，坐席在工作台接待——复用同一条消息管道，客服会话只是一种会话类型

非目标（明确不做）：联邦/去中心化（Matrix 路线）、端到端加密（暂缓）、百万级在线（架构预留即可）。

规模目标：1~5 万同时在线，日消息百万级，单 MySQL/单 RabbitMQ 可扛（D4）。

## 2. 总体架构

```
┌─────────┐  ┌─────────┐  ┌──────────────┐
│ im-web  │  │ im-app  │  │ 租户网站访客SDK │
└────┬────┘  └────┬────┘  └──────┬───────┘
     │  WebSocket(protobuf 帧)    │            HTTP(REST: 登录/历史/文件凭证)
     ▼            ▼              ▼                      │
┌─────────────────────────────────────┐                 │
│       im-gateway-rust（无状态,可水平扩展）│                 │
│  连接管理 / token校验 / 帧编解码 / 路由   │                 │
└──────┬──────────────────▲──────────┘                 │
       │ gRPC(上行消息)      │ RabbitMQ(下行推送,按网关实例队列)│
       ▼                  │                            ▼
┌──────────────────────────────────────────────────────────┐
│            im-server（Java21 模块化单体, MVP 单进程）           │
│  user │ message │ conversation │ group │ file │ push       │
│  ──────────── im-common(tenant上下文/ID/MQ封装) ──────────── │
└───┬────────┬─────────┬───────────┬──────────────────────┘
    ▼        ▼         ▼           ▼
 MySQL8      Redis7       RabbitMQ     MinIO
 (业务+消息+seq) (路由/在线/缓存) (异步链路)  (图片/语音/文件)
```

要点：

- **网关与业务彻底分离**（D1）：Rust 网关零业务，挂掉/扩容不影响业务状态；业务无长连接负担，可随意重启发版
- **上行走 gRPC 同步调用**：发消息需要立刻返回 seq 给客户端做对齐，同步 RPC 语义最干净；网关->message 模块 gRPC 调用由虚拟线程承接（D2）
- **下行走 RabbitMQ**：push 模块查 Redis 路由表得知目标用户在哪个网关实例，投到该实例专属队列 `push.gw.{instance_id}`，网关消费后写 WS
- **一切带 tenant_id**（D3/核心约定1）：WS 握手、gRPC metadata、MQ header、每张表

### 2.1 为什么 Rust 做网络层 + Java 做业务（回应选型疑问）

| 维度 | Go（OpenIM/Tinode 的选择） | Rust 网关 | Java21 虚拟线程 |
|------|--------------------------|-----------|----------------|
| 长连接内存 | goroutine ~4KB/连接 + GC 压力 | 无 GC，单连接状态可压到 ~KB 级，尾延迟稳定 | 虚拟线程 ~1KB 栈起步，但堆上对象+GC 仍在 |
| 吞吐 | 很好 | 最好 | 足够好 |
| 开发效率 | 高 | 低（借用检查/生态学习曲线） | 最高（团队熟悉+Spring 生态） |
| 适用层 | 全栈皆可 | **只放在收益最大的网关层** | **业务层：阻塞式写法+虚拟线程≈异步吞吐** |

结论：Go 一把梭是开源项目为了维护成本统一技术栈的选择；我们把 Rust 的成本限制在网关这一个"写完就稳定、很少改"的组件上，业务迭代全部留在 Java——这是收益/成本比最优的切分。JDK21 虚拟线程确实解决了"Java 写 IO 密集服务要上 WebFlux"的老问题：MyBatis/JDBC/gRPC 阻塞调用都直接写，由 JVM 调度，不需要响应式改造。但**虚拟线程不解决长连接的内存/GC 问题**，所以网关仍值得用 Rust。

### 2.2 模块化单体（D5）

- Maven 多模块，每个 service 模块 = 未来的微服务边界
- 模块间调用**必须走 im-proto 定义的 gRPC 接口**（进程内用 in-process gRPC channel，零网络开销），禁止直接 import 其他模块内部类
- im-bootstrap 聚合所有模块为单 Spring Boot 进程；二阶段把某模块拆出去时，只需把 in-process channel 换成网络地址，代码零改动

## 3. 多租户设计（IM 兼客服的根基）

### 3.0 主租户模式（D21）：产品是聊天软件，多租户是内建能力

自营聊天 App = **tenant 1**（平台自营租户），SaaS 客服客户 = 其他租户。含义：

- 只做聊天软件时，系统就是单租户在跑——tenant_id 恒为 1 且由拦截器自动注入，开发与运行期**零感知零负担**
- 多租户的真实成本只在 SaaS 商业化层（计费/配额/租户管理后台），不卖 SaaS 就不建
- 明确边界：**租户之间用户不互通**（这是隔离的语义，不是缺陷）；主 App 用户互聊全在 tenant 1 内
- 产品形态（自营+SaaS 客服 vs 白标独立 App）未定，当前按前者设计；白标需要的推送证书/分发/品牌配置中心不在现有范围，但架构无封死点

### 3.1 隔离模型（D3）

共享库 + `tenant_id` 行级隔离：

- 所有业务表含 `tenant_id BIGINT NOT NULL`，联合索引以 tenant_id 打头
- Java 侧 `TenantContext`（普通 ThreadLocal + finally 清理，D25；不使用 JDK21 preview `ScopedValue`）+ MyBatis 拦截器自动注入 where 条件，**应用代码不手写 tenant_id 过滤**，杜绝越权
- 网关在握手鉴权后将 tenant_id 绑定到连接上下文，后续所有上行帧自动携带
- 预留升级路径：DataSource 路由层接口先抽出来，大客户独立库时实现一个按 tenant 路由的 DataSource 即可

### 3.2 租户与用户模型

```
tenant（租户）
 └─ user
     ├─ user_type = MEMBER   普通 IM 用户
     ├─ user_type = AGENT    客服坐席（属于某技能组，二阶段）
     └─ user_type = VISITOR  访客（匿名，设备指纹生成，可升级为 MEMBER）
```

- 同一手机号在不同租户下是不同 user（user 表唯一键 = tenant_id + account）
- 访客账号：客服 SDK 首次连接时由 user-service 颁发临时 user + token，会话历史靠设备指纹找回

### 3.3 会话类型统一（学 Tinode "一切皆 Topic"）

| type | 语义 | 成员 | 第一阶段 |
|------|------|------|---------|
| C2C | 单聊 | 2 人固定 | ✅ |
| GROUP | 群聊 | N 人 | ✅ 基础版 |
| CS_SESSION | 客服会话 | 1 访客 + 1~N 坐席（可转接） | 表结构预留，二阶段实现 |
| SYSTEM | 系统通知（学 Tinode me-topic） | 1 人 | ✅（用于回执/通知下发） |

消息管道、seq、未读、已读、离线拉取对四种类型**完全同构**——这就是"IM 当客服用"的实现方式：客服只是在 CS_SESSION 上叠加"状态机（open/assigned/resolved）+ 坐席分配"两个业务模块。

CS_SESSION 语义精确化（D23）：它是**带生命周期状态机的会话**，不是"用完即删的临时会话"。
"临时"只体现在访客侧——匿名身份可过期、会话会被 resolve 关闭、重开可能是新会话（租户配置，二阶段）；
数据**永不因 resolve 删除**（坐席查历史、质检抽查、客诉举证都依赖），过期清理走统一保留策略（§13.5）。

## 4. Rust 网关设计（im-gateway-rust）

职责清单（也是职责上限，核心约定2）：

1. WS 握手：读取 `token` + `tenant_id` + `device_id` + `platform`，gRPC 调 user 模块校验 token 中的平台类和 `token_ver`，失败即断
2. 连接注册：本地 ConnMap（DashMap<conn_key, conn>），调用 `ConnEvent.OnConnected`；Java push 模块按 D27 负责 KICK 旧连接并写 Redis 路由表 `route:{tenant}:{uid}:{platform_class}`
3. 帧编解码：1 个 WebSocket Binary Message = 1 个 protobuf `Frame`（见 protocol.md §1），心跳 PING/PONG（默认 30s，服务端按 heartbeat * 3 idle timeout 清理）；网关按配置每 N 次 PING 异步调用 `ConnEvent.RefreshRoute` 刷新路由 TTL
4. 上行：业务帧 → gRPC `Uplink.Dispatch(cmd, bytes)` → 把返回的 `cmd/body` 包成同 `req_id` 的 `Frame` 回写
5. 下行：消费 `push.gw.{self}` 队列 → 查本地 ConnMap → 写 WS；`need_ack=true` 时按 D28 分配 `req_id` 等客户端 `MSG_RECV_ACK`，10s 超时主动断连，不重推
6. 断线：清理 ConnMap + 通知 `ConnEvent.OnDisconnected`，Java push 模块用当前连接比较删除 Redis 路由
7. 治理：实例级 + per-IP 握手限流、Origin 白名单、最大帧限制、`/health`/`/ready`、Prometheus `/metrics`、SIGINT/SIGTERM drain（先拒绝新连接，再关闭现有连接并等待 drain 窗口）

技术栈：tokio + axum ws + tonic + prost + lapin(RabbitMQ)。
无状态：路由信息全在 Redis，实例扩缩容只影响其上连接重连（客户端自动重连+增量同步兜底，不丢消息）。

## 5. 消息链路设计（抄 OpenIM，见 docs/research）

### 5.1 核心机制：会话级 seq + 收件箱（D7）

- 每会话一个 seq：MVP 采纳 D26，消息持久化事务内执行 `UPDATE conversation SET max_seq=max_seq+1 WHERE id=?`，随后读取 `conversation.max_seq` 作为本消息 seq；message/conversation/outbox 同事务提交，回滚不消耗 seq。
- 消息表按 (tenant_id, conversation_id, seq) 唯一，客户端按 seq 排序展示；同一会话写入由 conversation 行锁串行化，保证单调递增且无空洞。
- 热会话高吞吐场景预留 Redis INCR + 水位回写作为二阶段优化路径，启用前必须重新评审一致性语义。
- 双 ID（D9）：client_msg_id 幂等去重（Redis SETNX，TTL 24h），server_msg_id 全局唯一（Snowflake）

### 5.2 发送时序（单聊）

```
client          gateway              message模块                  push模块        对端
  │ MsgSend(cmid) │                      │                          │            │
  │──────────────>│ gRPC SendMsg         │                          │            │
  │               │─────────────────────>│ 1.幂等检查(cmid)           │            │
  │               │                      │ 2.风控/好友关系校验          │            │
  │               │                      │ 3.DB事务内分配seq, 写消息表  │            │
  │               │                      │ 4.更新会话 last_msg/未读+1  │            │
  │               │  返回 seq,smid        │ 5.投 MQ(msg.saved)        │            │
  │ MsgSendAck    │<─────────────────────│ ──────────────────────── >│            │
  │<──────────────│                      │                          │ 查路由表     │
  │               │                      │                          │ 投push.gw.x│
  │               │                      │                  gateway-x│───MsgPush──>│
  │               │                      │                          │<─MsgRecvAck─│
```

- 3/4 同事务；返回 ack 在投 MQ 之前不依赖推送结果——**落库即成功**，推送是 best-effort+同步对齐兜底
- 群聊：MVP 写扩散到成员收件箱（更新各成员会话未读），消息体只存一份；大群（>1000）二阶段切读扩散（Open Question）

### 5.3 离线消息 = 增量同步（核心约定4）

不存在独立"离线消息表"。客户端上线流程：

1. 发 `SyncReq{ conv_versions: [{conv_id, local_max_seq}], conv_list_version }`
2. 服务端 diff：返回有新消息的会话 + 每会话 [local+1, server_max] 的消息（分页，每会话先回最新 N 条，更早的懒加载）
3. 推送过程中发现 seq 跳号 → 客户端主动拉缺口区间

同一机制覆盖：离线消息、多端同步、弱网丢推送补偿、新设备登录全量同步（local_max_seq=0）。

### 5.4 已读回执

- conversation_member 表记 `read_seq`；客户端发 `ReadReport{conv_id, seq}`，服务端更新并向**发送方+自己其他端**推 `ReadNotify`
- 未读数 = max_seq - read_seq，天然准确，免维护计数器
- 单聊显示"已读/未读"，群聊 MVP 只显示自己的未读数（群已读列表二阶段）

### 5.5 多端在线与互踢（D11）

平台分三类：`MOBILE`(iOS/Android)、`DESKTOP`(Win/Mac)、`WEB`。默认策略（租户级可配置 `multi_device_policy`）：

- **同类互踢**：每类最多 1 台在线，新登录踢旧设备
- **跨类共存**：手机+PC+Web 可同时在线（客服坐席刚需）

互踢时序：
```
REST 登录/注册 → user 模块按平台类递增 token_ver:{t}:{uid}:{platform_class} 并把版本写入 JWT
  → GatewayAuth.VerifyToken 校验 token 内 platform_class/token_ver 等于 Redis 当前值
  → ConnEvent.OnConnected 查路由表发现同类已有 route:{t}:{uid}:{MOBILE}
  → push 模块向旧连接所在网关投 KICK 帧（原因：被新设备顶替/被管理员下线）
  → 旧网关推 KICK 后主动断开、清路由；若旧网关已死，路由 TTL 兜底过期
  → 新连接写入路由表，登录完成
```

多端共存的连带设计（都已被 seq 机制覆盖，无额外成本）：

- **消息扇出到端**：push 模块按 `route:{t}:{uid}:*` 扫该用户全部在线端逐一投递
- **多端消息同步**：每端独立维护 local_max_seq，增量同步天然各自对齐；自己发的消息也写自己收件箱 seq，其他端靠同步拿到（多端漫游免费获得）
- **已读跨端同步**：任一端上报 ReadReport → ReadNotify 推给自己所有其他端，未读数一致

### 5.6 认证标识（D12）

仅做"蓝 V"：`user.verified_type`（NONE/PERSONAL/ENTERPRISE/OFFICIAL_STAFF）+ `user_certification` 资料表（认证名称、资质材料、审核状态）。
verified_type 随用户资料下发，客户端渲染徽章；对消息链路零侵入。
若未来要公众号式官方号（群发/多人代运营），复用坐席+CS_SESSION 同构模型扩展，群发用读扩散 timeline——表结构无需现在预留，仅在此记录路径。

### 5.7 群规模上限与扩散策略（D13）

MVP 上限 **500 人/群**（租户套餐参数 `max_group_members`，套餐差异化是 SaaS 卖点）。阈值影响分析：

| 成本项 | 我们的模型下的实际开销 | 500 人时 | 若放大到 5000 |
|--------|----------------------|---------|--------------|
| 消息存储 | 每会话只存 1 份（收件箱按 seq 引用） | 无关 | 无关 |
| 未读数 | read_seq 差值计算，**零写扩散** | 无关 | 无关 |
| 在线推送扇出 | 仅对在线成员，按网关实例**批量投递**（1 条 MQ 消息带成员清单） | ~百级/条，可控 | 需降级"脏通知+按需拉取" |
| 会话列表排序 | conversation.last_msg_time 共享 1 行 | 无关 | 行热点，需缓存合并写 |
| 群已读列表 | per-member 矩阵 O(N) 读 | MVP 不做，仅自己未读 | 二阶段也只做"已读人数" |
| @提及 | 需 per-member 标记（唯一真写扩散点） | 量小直接写 | 单独通道异步写 |

结论：seq+read_seq 模型让"读扩散"是默认形态，500 内不存在大群特殊路径；推送扇出是唯一随 N 线性增长的点，已用按网关批量投递压到常数级 MQ 消息数。

### 5.8 消息类型与扩展（预留二阶段）

content 用 protobuf oneof：Text / Image / Voice / File / Custom(JSON) / Notification（系统事件：建群、改名、撤回通知、坐席分配——学 Matrix 把状态变更事件化，自动获得多端同步）。消息表留 `ext` JSON 列做扩展字段（D二阶段需求）。

## 6. 协议设计（im-proto）

```
proto/
├── common/  enums.proto(ConvType,MsgType,Platform...) model.proto(MsgContent oneof...)
├── ws/      frame.proto    # WS 帧：Frame{ req_id, cmd, body }
│            # cmd: AUTH, PING/PONG, MSG_SEND, MSG_SEND_ACK, MSG_PUSH,
│            #      MSG_RECV_ACK, SYNC_REQ, SYNC_RESP, READ_REPORT, READ_NOTIFY, KICK
└── rpc/     user.proto(VerifyToken,GetUser...) message.proto(SendMsg,PullMsgs,SyncDiff)
             conversation.proto(ListConv,MarkRead) push.proto(OnlineStatusChanged,PushToUser)
```

- WS 帧 `req_id` 是**请求级**序号（客户端递增，用于 req/ack 配对；D28 下行 ack 帧由网关分配），与消息 seq 无关，命名上严格区分
- REST 仅用于：登录/注册（颁 JWT）、历史消息分页、文件凭证、管理后台。实时链路全在 WS

## 7. 数据模型（MySQL，首版字段从简）

```sql
tenant(id, name, plan, status, created_at)
user(id, tenant_id, account, password_hash, nickname, avatar,
     user_type ENUM('MEMBER','AGENT','VISITOR'),
     verified_type ENUM('NONE','PERSONAL','ENTERPRISE','OFFICIAL_STAFF') DEFAULT 'NONE',
     device_fp, status, UNIQUE(tenant_id, account))
user_certification(id, tenant_id, user_id, verified_type, cert_name,
     cert_material JSON, audit_status ENUM('PENDING','APPROVED','REJECTED'),
     audited_by, audited_at)
tenant_config(tenant_id, multi_device_policy JSON /*三类各限几台*/,
     max_group_members INT DEFAULT 500, plan_features JSON)
conversation(id, tenant_id, type ENUM('C2C','GROUP','CS_SESSION','SYSTEM'),
     c2c_key /*小uid_大uid 防重*/, group_id, max_seq, last_msg_abstract, last_msg_time,
     cs_status ENUM('OPEN','ASSIGNED','RESOLVED') NULL /*客服预留*/,
     UNIQUE(tenant_id, c2c_key))
user_blacklist(tenant_id, user_id, blocked_user_id, created_at,
     PRIMARY KEY(tenant_id, user_id, blocked_user_id))  /*D17: 拉黑后发送返回 BLOCKED_BY_PEER*/
friend(tenant_id, user_id, friend_user_id, remark, status, created_at)  /*二阶段,租户开关 friend_required*/
outbox(id, tenant_id, event_type, payload, status, created_at)  /*D18: 与业务表同事务写,轮询投MQ*/
conversation_member(conv_id, tenant_id, user_id, read_seq, unread_hint,
     mute, pinned, deleted_at, PRIMARY KEY(conv_id, user_id))
user_conv_version(tenant_id, user_id, conv_list_version,
     PRIMARY KEY(tenant_id, user_id))  /*每用户会话列表版本水位*/
user_conv_event(id, tenant_id, user_id, conv_id, event_version, event_type,
     created_at, UNIQUE(tenant_id, user_id, event_version))  /*E1: 会话列表 diff 流水*/
message(id /*snowflake*/, tenant_id, conversation_id, seq, sender_id,
     client_msg_id, msg_type, content /*pb bytes 或 json*/, ext JSON,
     status ENUM('NORMAL','REVOKED'), created_at,
     UNIQUE(tenant_id, conversation_id, seq), INDEX(tenant_id, client_msg_id))
group_info(id, tenant_id, name, owner_id, avatar, member_count, status)
group_member(group_id, tenant_id, user_id, role, joined_at)
file_meta(id, tenant_id, uploader_id, object_key, mime, size, duration /*语音*/, status)
sensitive_word(id, tenant_id NULL /*NULL=平台级*/, word, category, action ENUM('REVOKE','REPLACE','FLAG'), enabled)
moderation_log(id, tenant_id, message_id, provider, category, score, action_taken,
     original_content /*留证,仅审计可查*/, audit_status ENUM('AUTO','REVIEWING','UPHELD','OVERTURNED'))
```

Redis 键位规划：`route:{t}:{uid}:{platform_class}`、`online:{t}:{uid}`、
`token_ver:{t}:{uid}:{platform_class}`（互踢令牌失效）、`dedup:{t}:{cmid}`、`im:worker:{id}`、
最近消息缓存 `msgs:{t}:{conv}`(ZSET by seq, 容量 100)。

`outbox` 是基础设施全局轮询表，MyBatis 租户拦截器忽略该表；写入方必须显式写入 `tenant_id`，
poller 全表扫描后按行内 `tenant_id` 写 MQ header 和 routing key，避免只投递默认租户事件。

`user_conv_version` 使用 `(tenant_id,user_id)` 行锁为单个用户的会话列表变更分配单调版本；
`user_conv_event` 在同一业务事务中记录 `created/updated/removed` 事件。客户端 `SYNC_REQ.conv_list_version`
小于服务端版本时，服务端返回该版本之后发生变化的 `ConvInfo`；删除会话用 `ConvInfo.deleted=true` 表达。

## 8. 文件/图片/语音（D10）

1. 客户端向 file 模块要预签名 URL（带 tenant 配额校验）→ 直传 MinIO
2. 传完回调/确认 → file_meta 落库 → 客户端再发 Image/Voice 消息（content 带 object_key、宽高/时长、缩略图 key）
3. 下载也走预签名 URL（私有 bucket，按 tenant 做 key 前缀 `{tenant_id}/{yyyymm}/{uuid}`）

语音：客户端录制 AAC/Opus，时长写入 content；MVP 不做服务端转码。

## 9. 内容安全与加密（D15/D16）

### 9.1 审核管道：全部先发后审

发送链路不做任何同步审核（零延迟侵入）。moderation 子模块（挂在 im-message-service 内）订阅 MQ `msg.saved`：

```
msg.saved ──> moderation 消费
               ├─ 文本：内存 DFA/AC 自动机匹配（平台级词库 + 租户级词库，热加载）
               ├─ 图片/语音：调第三方内容安全 API（接口抽象 ContentAuditProvider，MVP 可空实现）
               └─ 判定违规：
                    1. message.status -> REVOKED（复用撤回机制，推 RevokeNotify 各端删除展示）
                    2. moderation_log 落库（原文留证，仅审计可查）
                    3. 处罚动作（按租户策略：警告/禁言/封号）+ 通知租户管理后台
```

- 已知代价（D16 决策时已确认）：违规内容有**秒级曝光窗口**；要求撤回通知机制 MVP 就要有最小实现
- 词库分两级：平台级（全租户生效，色情/暴恐/政治类兜底）+ 租户级（租户自定义业务敏感词），`sensitive_word` 表 + 本地缓存，改词库发 MQ 广播刷新
- 误杀申诉：moderation_log 带 audit_status，管理后台可人工复核回滚（status 改回 NORMAL + 重新下发）

### 9.2 传输与存储加密（所有会话的底线）

- 全链路 TLS：WSS、gRPC TLS、MySQL/Redis 内网 + 凭证管理
- 落库可选字段加密：message.content 支持租户级 AES-GCM（密钥在 KMS/配置中心），对审核模块透明（审核在加密前的内存态完成）

### 9.3 E2EE：不做（D15，2026-06-13 修订）

评估结论：E2EE 与关键词审核/客服质检/服务端历史/多端漫游全部冲突，实现成本（X3DH+Double Ratchet、
设备密钥管理、预密钥补充）≥ 整个 MVP 消息模块。**决定不做，有真实需求再评估。**
不做任何表结构预留（conversation.e2ee_enabled 字段移除）；protobuf `MsgContent` 本身是 oneof，
未来加 `encrypted_payload` 分支即可，天然向后兼容，无需现在占位。

## 10. 部署（deploy/docker-compose）

MVP 一套 compose：mysql8 / redis7 / rabbitmq(management) / minio / im-gateway-rust / im-server(bootstrap) / im-web(nginx)。
中间件加健康检查，服务 depends_on 条件启动；配置走 .env。K8s 与多网关实例放二阶段。

## 11. 阶段规划

### 第一阶段 MVP（全自研）
登录注册(JWT) → WS 长连接+心跳+重连 → 单聊 → 落库+seq → 离线拉取(增量同步) → 会话列表 → 已读回执 → 基础群聊(写扩散) → 图片/语音(MinIO 直传)。
建议实现顺序：im-proto → gateway 骨架+AUTH/PING → user 模块 → message 模块(seq/落库) → 端到端单聊跑通 → sync/会话/已读 → 群聊 → 文件。

### 第二阶段（参考 OpenIM 补完整 + 客服）
多端同步完善(平台互踢策略)、消息撤回(事件化)、消息扩展字段、群已读、大群读扩散、SDK 抽取(im-web 的协议层下沉为 js-sdk)、
**客服上线**：访客 SDK、CS_SESSION 状态机、坐席分配(轮询→技能组)、转接、客服工作台(im-admin+im-web)、第三方离线推送。

## 12. 客户端选型（D14）

- **im-app（IM 主 App）= Flutter**：聊天界面是重交互场景（万级消息长列表复用、语音波形、图片九宫格、输入法联动），Flutter 自绘引擎双端一致、性能上限高；协议层（WS+protobuf+同步引擎）抽成 Dart 包，即未来的 flutter-sdk
- **客服访客端 = uni-app/H5**：本质是嵌入租户网站/App/微信小程序的轻量聊天组件，一套代码覆盖 H5(iframe/JS-SDK 嵌入)+微信小程序+快应用——小程序渠道 Flutter 覆盖不了，而这是客服 SaaS 的关键获客入口
- **im-web = Vue3**：IM Web 端 + 客服坐席工作台 + 租户管理后台，与 uni-app 同栈，协议层下沉为 js-sdk 共享

## 13. 架构补遗：之前没考虑到的横切关注点（2026-06-13 排查）

### 13.1 关系链与反骚扰（D17）
开放式单聊（同租户内可直接发起，客服访客场景刚需）+ `user_blacklist`（拉黑后发送返回 `BLOCKED_BY_PEER`，与 `error.proto` 和当前发送链路实现保持一致）。
"需好友才能聊"= `tenant_config.friend_required` 开关，friend 表与申请流程二阶段。message 模块发送校验顺序：黑名单 → 租户好友开关 → 禁言状态。

### 13.2 落库与 MQ 的一致性：Outbox 模式（D18）
"写消息表成功但投 MQ 失败"会导致推送/审核静默丢失。解法：消息表 + outbox 表**同事务**写入，
后台轮询（虚拟线程，批量 100ms 间隔）投 RabbitMQ，confirm 后删除；MQ 消费侧按 server_msg_id 幂等（重复投递无害）。

### 13.3 重连风暴与过载保护
网关发版/崩溃 → 数万客户端同时重连+增量同步，会打挂 user(鉴权) 和 message(拉取)。
对策：客户端指数退避+随机抖动（SDK 内置）；网关握手限流（令牌桶，按实例）；同步接口优先走 Redis 最近消息缓存；
租户级限流（连接数/发送 QPS = 套餐参数），防单租户拖垮全局——**多租户 SaaS 的公平性是架构责任**。

### 13.4 可观测性（MVP 就要有，排查"消息去哪了"的命脉）
trace_id 在 WS 帧→gRPC metadata→MQ header 全链路透传；关键埋点：收到帧/分配seq/落库/投MQ/推送/客户端ack 六个时间点入结构化日志。
指标：在线连接数(按租户)、消息 QPS、端到端 P99 延迟、MQ 积压、同步请求量。MVP 用 Prometheus + Grafana + Loki（compose 里直接带上）。

### 13.5 消息生命周期与合规
保留策略 = 租户套餐参数（如 90 天/1 年/永久），到期归档冷存储（MinIO parquet）后物理删除；
租户注销 = 全量导出 + 延迟 30 天物理删除。审计日志（moderation_log/登录日志）单独更长保留。

### 13.6 SaaS 商业化基础设施（二阶段，但表结构影响现在评估）
用量统计：MAU、消息量、存储量、坐席数——按天聚合入 `usage_daily` 表（从 MQ 事件流聚合，不查业务表）；
配额强制点：连接数、群上限（已有）、文件存储、坐席数。计费引擎本身二阶段，但**用量事件现在就从 outbox 流里顺手产出**。

### 13.7 协议演进与客户端版本
Frame 带 `protocol_version`；服务端维护最低兼容版本，低于则 KICK(原因=需升级)；protobuf 只增不改不删字段号（标准纪律）。

### 13.8 安全清单（MVP 必做项）
WS 握手校验 Origin(Web 端)；帧带时间戳防重放（±5min 窗口）；上传预签名 URL 限制 content-type/大小/有效期 5min，按租户配额；
JWT 短期(2h)+refresh token；管理后台独立 RBAC；MySQL/Redis/MinIO 不暴露公网；密码 bcrypt。

### 13.9 Redis 故障半径（接受的风险，记录在案）
消息 seq 主路径不依赖 Redis（D26），Redis 故障不会导致已登录用户发送链路因 seq 分配不可用；但幂等 SETNX、路由表、在线状态、token_ver 等能力会受影响。路由表丢失→全员踢线重连（13.3 兜底）。
万级规模接受"Redis 单点+哨兵"，不上 Cluster；二阶段若把 seq 热点迁回 Redis，必须重新补充故障半径与一致性方案。

## 14. 尚未定/待讨论

见 CLAUDE.md「Open Questions」。已定 D1~D18（最新：D15 修订为不做 E2EE、D17 关系链、D18 Outbox）。
下一批待定：第三方内容安全 API 选型、离线推送通道、消息全文搜索（二阶段，候选 ES/本地 SQLite FTS）。
