# AGENTS.md — im-project 项目记忆与约定

> 本文件是 Codex 与 Jade 协作的"项目大脑"：记录已定决策、技术约定、讨论结论。
> 每次架构讨论有结论后更新本文件。详细设计在 `docs/architecture.md`。

## 项目定位

多租户 IM 系统，同一套系统可作为 SaaS 客服系统输出给其他企业使用（类 Chatwoot/Intercom + 自有 IM）。

## 已定决策（Decision Log）

| # | 日期 | 决策 | 理由 |
|---|------|------|------|
| D1 | 2026-06-13 | 网关用 **Rust**（tokio + axum/tokio-tungstenite），业务用 **Java 21 + Spring Boot 3.2** | 网关只做 IO 密集的连接管理，Rust 内存占用低、无 GC 抖动；业务逻辑迭代快用 Java |
| D2 | 2026-06-13 | Java 使用 **JDK 21 虚拟线程**，gRPC/DB 调用按阻塞风格写 | 虚拟线程使阻塞式代码具备异步吞吐，省去响应式编程复杂度 |
| D3 | 2026-06-13 | 多租户隔离：**共享库 + tenant_id 列** | 万级规模下成本最低；MyBatis 拦截器统一注入 tenant_id；预留大客户独立库升级路径 |
| D4 | 2026-06-13 | 规模目标：**1~5 万同时在线**，单机可扛 | 单 MySQL + RabbitMQ 足够；架构预留水平扩展点（网关无状态、会话级 seq 可按 D26 演进） |
| D5 | 2026-06-13 | 服务粒度：**模块化单体起步**（Maven 多模块 + im-bootstrap 单进程） | gRPC 接口先定义好，模块间禁止直接调内部类，后期可无痛拆分 |
| D6 | 2026-06-13 | 客服能力：**第一阶段只做 IM，但协议/表结构现在就预留** | conversation 带 type（C2C/GROUP/CS），用户带 user_type（member/agent/visitor） |
| D7 | 2026-06-13 | 消息模型：借鉴 OpenIM 的 **收件箱模型 + 会话级 seq** | seq 单调递增做消息对齐/增量同步/去重，比 Matrix DAG 简单得多，够用 |
| D8 | 2026-06-13 | MQ 用 **RabbitMQ**（非 Kafka） | 万级规模吞吐足够，运维简单；消息链路抽象 MQ 接口，百万级时可换 Kafka |
| D9 | 2026-06-13 | 消息 ID：客户端生成 client_msg_id（UUID，幂等去重）+ 服务端生成 server_msg_id（Snowflake） | 双 ID 模式，OpenIM 同款做法 |
| D10 | 2026-06-13 | 文件：客户端走 MinIO **预签名 URL 直传**，im-file-service 只发凭证和落元数据 | 文件流量不过业务服务器 |
| D11 | 2026-06-13 | 多端策略：**Mobile/Desktop/Web 三类各限 1 台在线，同类新登踢旧，跨类共存**；做成租户级可配置项 | 微信模式体验最佳；路由表本就按 platform 分 key，改动最小；客服坐席需要 PC+手机共存 |
| D12 | 2026-06-13 | 官方认证 = **仅认证标识（蓝 V）**：user.verified_type 字段 + user_certification 认证资料表 | 不做公众号子系统；若未来要官方号/群发，按坐席+CS_SESSION 模型扩展（已同构） |
| D13 | 2026-06-13 | 群人数上限 MVP = **500**（租户套餐参数 max_group_members） | 万级在线下全量推送扇出可控；未读靠 read_seq 计算本就是读扩散，500 内无需大群特殊路径 |
| D14 | 2026-06-13 | im-app：**Flutter 做 IM 主 App，uni-app/H5 做客服访客端**（覆盖微信小程序+H5 嵌入渠道） | 重交互聊天界面要 Flutter 的性能与双端一致；访客端是嵌入租户站点/小程序的轻组件，uni-app 一套出多端 |
| D15 | 2026-06-13 | E2EE：**不做**（2026-06-13 修订：原定私聊可选，现改为有需求再评估）。全链路 TLS + 可选落库字段加密兜底 | 与关键词审核/质检/多端漫游冲突，实现成本≥整个消息模块；协议仅留 content oneof 扩展点，不预留其他 |
| D16 | 2026-06-13 | 内容安全：**全部先发后审**（含文本）。moderation 模块订阅 msg.saved 异步审：文本走本地 DFA/AC 词库，图片/语音调第三方内容安全 API；违规→系统撤回+审核日志+处罚 | 发送链路零延迟侵入；⚠️ 代价是违规内容有秒级曝光窗口；依赖消息撤回机制（status=REVOKED 已预留，撤回通知事件需 MVP 实现最小版） |
| D17 | 2026-06-13 | 关系链：**开放式单聊 + 黑名单**（MVP 必做），"需好友才能聊"做成租户开关，好友申请流程二阶段 | 客服访客场景天然要求陌生人可发起会话；黑名单是骚扰自救与内容安全闭环的必要一环 |
| D18 | 2026-06-13 | 落库与投 MQ 一致性用 **Outbox 模式**：消息表+outbox 表同事务写入，后台线程轮询 outbox 投 RabbitMQ，投成删除；消费侧按 server_msg_id 幂等 | RabbitMQ 不支持与 MySQL 的分布式事务；"落库成功但 MQ 没投出去"会导致推送/审核静默丢失，Outbox 是最朴素可靠的解法 |
| D19 | 2026-06-13 | WS 帧 = **cmd + bytes 透传式**（Jade 拍板）：网关只编译 ws/frame.proto + rpc/gateway.proto，业务帧 body 经 Uplink.Dispatch(cmd, bytes) 透传 Java 路由器 | 网关彻底不依赖业务 proto，新增业务帧 Rust 零改动；代价：cmd/body 匹配是运行时约定，Java 入口做解码防御 |
| D20 | 2026-06-13 | MsgContent = **protobuf oneof 强类型** + CustomContent(JSON) 逃生口；落库存 pb bytes + 冗余文本摘要列 | 强类型省流量、双端解析一致；租户自定义消息走 Custom 不动协议 |
| D21 | 2026-06-13 | **主租户模式**：自营聊天 App = tenant 1（平台自营租户），SaaS 客服客户 = 其他租户。产品定位以 IM 聊天软件为主，多租户是内建能力非负担 | 单租户运行时 tenant_id 恒为 1、拦截器自动注入，运行期零感知；产品形态（自营+SaaS客服 vs 白标）Jade 尚未定，按前者设计但不封死白标路 |
| D22 | 2026-06-13 | 账号体系：**手机号+验证码为主（生产形态），MVP 先账号密码联调**；user.account 列两者兼容 | 国内聊天 App 标配；短信通道接入前不阻塞开发 |
| D23 | 2026-06-13 | 客服会话语义精确化：**带生命周期状态机的会话**（open→assigned→resolved），"临时"只体现在访客身份可过期、会话可关闭重开；**数据不删**（质检/审计/举证依赖） | 修正"临时会话=用完即删"的直觉；resolved 后访客再来开新会话还是续旧会话→租户配置，二阶段定 |
| D24 | 2026-06-13 | 生产部署 = **云服务器自装（compose）**；中间件选型复审后**全部维持**：MySQL8/Redis7/RabbitMQ/MinIO，明确不引入 ES/ClickHouse/Mongo/注册中心；升级路径：消息表 分区→分表/TiDB，MQ 百万级换 Kafka，存储上云换 OSS（S3 兼容抽象） | 自装环境运维成本优先；逐层对比与 HA 底线见 docs/middleware-selection.md |
| D25 | 2026-06-13 | TenantContext = **普通 ThreadLocal**（finally 清理），**禁用 --enable-preview**（PR-1 审查 S3 的裁决） | ScopedValue 在 JDK21 是预览特性，测试加参数而生产没加 → 启动即崩；虚拟线程不复用，ThreadLocal 无泄漏风险 |
| D26 | 2026-06-13 | seq 方案采纳 **MySQL conversation 行锁自增**：`UPDATE conversation SET max_seq=max_seq+1` 与 message/conversation/outbox 同事务；Redis seq 降级为高吞吐预留路径 | PR-1 审查 S7 补流程：实现偏离文档但技术方向更稳，同事务无空洞、回滚一致、少一个 Redis 故障依赖；流程教训记录在案：后续偏离文档必须先提 Open Question |

## 设计文档索引

- docs/architecture.md（总体架构）/ docs/protocol.md（协议）/ docs/im-server-design.md（Java 侧模块设计，2026-06-13 待 Jade 评审）
- Java 根包名 `com.im`；模块依赖铁律：业务模块互相禁止依赖，跨模块走 in-process gRPC（enforcer 固化）

## 技术栈

- 网关：Rust stable, tokio, tonic(gRPC client), prost(protobuf), redis-rs
- 业务：JDK 21（虚拟线程开启）, Spring Boot 3.2, MyBatis-Plus, grpc-java
- 协议：Protobuf3（im-proto 是唯一事实来源，Rust 用 prost 生成，Java 用 protoc 插件生成）
- 存储：MySQL 8（消息/业务/会话级 seq）、Redis 7（路由表/在线状态/缓存/幂等）、MinIO（对象存储）
- MQ：RabbitMQ（topic exchange，按 tenant+conversation 路由）
- 部署：Docker Compose（MVP）→ K8s（二阶段）

## 核心约定

1. **tenant_id 贯穿一切**：所有表第一业务列是 tenant_id；所有 RPC metadata、WS 连接上下文、MQ 消息头都带 tenant_id；Java 侧用 ThreadLocal(TenantContext) + MyBatis 拦截器强制注入。
2. **网关零业务**：im-gateway-rust 只做连接生命周期、token 校验（调用 user-service gRPC）、protobuf 帧编解码、上行投递 MQ/gRPC、下行按路由表推送。任何业务判断都在 Java 侧。
3. **消息可靠性三段 ACK**：客户端→网关（WS ack）、网关→消息服务（gRPC 同步返回 seq）、服务端→接收端（推送后等客户端 ack）。推送 ack 超时**不重推**，而是判定半死链→主动断连→客户端重连+SYNC_REQ 补齐（最坏空窗 ≈ 10s+重连耗时，详见 docs/protocol.md §3）。
4. **离线消息 = 增量同步**：客户端上线带 per-conversation local max seq，服务端返回 [local+1, server_max] 区间消息（OpenIM 模式），不维护单独"离线消息表"。
5. **proto 改动流程**：改 im-proto → 跑生成脚本 → Rust/Java 同步编译过才能提交。字段号只增不删改、枚举首值 UNSPECIFIED=0、不兼容变更开 v2 包（详见 docs/protocol.md §6）。
   proto 已落地（2026-06-13，已过 protoc 校验）：ws/frame.proto、rpc/gateway.proto（网关侧）+ common/、body/messages.proto、rpc/internal.proto、events/events.proto（Java 侧）。
6. 文档语言：中文；代码注释：中文可；标识符：英文。

## 开源参考结论（详见 docs/research/）

- **OpenIM**：抄消息模型（seq/收件箱/双ID/增量同步）、msggateway-msgtransfer-push 三段流水线思想
- **Tinode**：抄 Topic 抽象（把单聊/群聊/客服统一成 topic/conversation 路由）、轻量 server 设计
- **Matrix**：只借鉴概念（事件化消息、state event 与 message event 分离的思想），不采用 DAG/联邦——复杂度对本项目不划算

## 待讨论（Open Questions）

- [ ] 产品形态终局：自营主 App + SaaS 客服（当前默认）vs 白标租户独立 App——Jade 想清楚后定，白标涉及推送证书/分发/品牌配置中心
- [ ] 客服会话 resolved 后访客重开：新会话 vs 续旧会话（租户配置）——二阶段

- [ ] 推送第三方通道选型（APNs/FCM/厂商通道）放第二阶段
- [ ] 第三方内容安全 API 选型（阿里云内容安全/网易易盾/数美）——接口先抽象，MVP 可只接文本词库
- [ ] 消息全文搜索（ES vs 客户端 SQLite FTS）——二阶段
- [ ] 好友申请流程 + friend_required 租户开关的交互细节——二阶段
- [ ] 客服坐席分配策略（轮询/最少会话/技能组）——第二阶段细化
- [ ] 二阶段若括高群上限（>500）：推送降级为"脏通知+拉取"、@提及单独写扩散、成员列表懒加载
- [ ] 认证审核流程（人工审核 or 对接企业资质 API）——管理后台二阶段

## Imported Claude Cowork project instructions
