# CLAUDE.md — im-project 项目记忆与约定

> 本文件是 Claude 与 Jade 协作的"项目大脑"：记录已定决策、技术约定、讨论结论。
> 每次架构讨论有结论后更新本文件。详细设计在 `docs/architecture.md`。

## 协作模式（2026-06-13 起）

设计文档由 Claude 与 Jade 讨论产出；**编码由其他开发者按文档实现，Claude 负责代码审查**。
审查依据：本文件决策日志 + docs/ 设计文档 + docs/review-checklist.md。
实现者如需偏离文档，必须先在本文件 Open Questions 区提出，经讨论改文档后再写码——禁止代码先行。

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
| D27 | 2026-06-13 | `token_ver` 精确语义：REST 登录/注册按平台类递增 `token_ver` 并写入 JWT；`GatewayAuth.VerifyToken` 校验 token 内版本等于 Redis 当前版本；`ConnEvent.OnConnected` 只负责 KICK 旧连接并替换路由 | 避免 OnConnected 递增版本后把新连接 token 一并失效；仍满足同类互踢时旧 token 立即失效 |
| D28 | 2026-06-13 | `need_ack` 下行确认使用 `Frame.req_id`：网关为每个目标连接分配非 0 `req_id`，客户端 `MSG_RECV_ACK` 回带同 `req_id`；业务 ack body 仍原样转发 Java | 网关不编译业务 body proto，仍能精确跟踪下行送达；10s 未 ack 主动断连并走 SYNC 补齐，不做服务端重推 |
| D29 | 2026-06-13 | 访客身份 = **`user.user_type=visitor` 的特殊用户**：visitor_profile 表存 localStorage visitor_token → user_id 映射；续旧规则：只续 open/assigned，resolved 建新会话 | D6 已预留 visitor 枚举值；访客持 JWT（与普通用户相同的网关校验流程），消息 sender_id 不变，不引入新的身份体系 |
| D30 | 2026-06-13 | 新增模块 **`im-cs-service`** 承载客服领域逻辑（访客接入、坐席 inbox、会话状态机、CS 推送路由） | CS 有独立生命周期状态机和访客管理，与 im-conversation-service 职责分离；模块隔离铁律仍适用，跨模块走 gRPC |
| D31 | 2026-06-13 | CS 会话状态机 **open(0) → assigned(1) → resolved(2)**；坐席用 `conversation.agent_id` 记录，只允许正向流转；resolved 后访客重新发消息开新会话 | D23 已定此三态；resolved 不续是最简且无歧义的 MVP 策略 |
| D32 | 2026-06-13 | 访客显示名 = **"访客" + 4 位随机大写字母数字**（如"访客A3K9"），生成后不可修改 | 访客不需要填昵称，降低进入门槛；随机后缀在坐席侧可区分多个访客 |
| D33 | 2026-06-13 | CS 消息推送路由：**open 状态推所有在线坐席**，**assigned 状态只推绑定 agent_id**；铃声区分由前端按 `conv_type=CS` 处理，不影响推送链路 | open 会话需要让所有坐席知晓有待接待任务；assigned 后缩小推送范围避免打扰其他坐席 |
| D34 | 2026-06-13 | 坐席标识 = `user.is_agent TINYINT(1) DEFAULT 0`，与 `user_type=member` 正交（坐席仍是普通用户，额外开启坐席权限） | 同一账号可同时是 IM 用户和坐席，Flutter App 按 is_agent 决定是否显示"客服"tab，无需双账号 |
| D35 | 2026-06-13 | 坐席在线状态 = **`user.agent_status` DB 列**（0=offline/1=online/2=busy），坐席手动切换；推送路由只推 online/busy 坐席；widget 通过公开接口查询租户是否有坐席在线 | MVP 用 DB 列足够，避免引入实时 presence 复杂度；自动 offline（WS 断连联动）列为二阶段 |
| D36 | 2026-06-13 | **离线留言 = 消息链路不变**，访客无论坐席是否在线均可发消息正常存库；坐席上线后查 open 未认领会话并 App 内通知；MVP 不做实时邮件通知 | 最简且可靠：不引入新消息类型，不依赖邮件服务；坐席上线主动拉取比服务端主动推更稳定 |
| D37 | 2026-06-13 | Widget 配置存 `widget_config` 表（颜色/欢迎语/位置/powered_by 徽标）；企业获得一段 JS snippet 嵌入网站；`powered_by=1` 默认开启作为免费版病毒传播机制 | JS snippet 是 B 端 SaaS 标配嵌入方式；徽标是 Crisp/Intercom 早期核心增长手段 |
| D38 | 2026-06-14 | **客服内部备注**：新增 `cs_internal_note` 表，仅"处理该会话的坐席本人"可读写（open 未认领不可、非本人坐席不可）；备注不进 message/outbox、不推访客。**修订 D31**：resolve 不再清空 `agent_id`，保留为"处理坐席"记录，使结单后本人仍可补充质检/交接备注（`listAgentCsConvs` 仍按 `cs_status IN(1,2)` 过滤，resolved 不污染工作台） | 质检/交接发生在结单后，需要知道处理坐席；保留 agent_id 是最小改动且无副作用（resolved 推送本就只发访客） |
| D39 | 2026-06-14 | **重申 D35**：CS 推送扇出 + widget 可用性坐席范围 = **online+busy**（`agent_status IN(1,2)`，非仅 online）；**认领会话**要求坐席 active（online 或 busy），offline 不可认领。坐席工作台列表对访客资料/访客成员/在线状态做**批量查询**（消除 N+1） | busy 坐席仍需感知 open 待接待并可认领；批量化避免 limit×(DB+Redis) 放大 |
| D40 | 2026-06-15 | **好友申请验证流程**（提前 D17 标注的二阶段项，详见 docs/friend-service-design.md）：新增 `friend_request`（态机 pending→accepted/rejected/ignored，带 `note` 备注、`auto_accepted` 标记；每次申请留历史、同一 from→to 至多一条 pending）+ **复用 baseline 已有 `friend` 双向关系表（user_id/friend_user_id/remark，非新建 friendship）**；用户级设置 `user.friend_verify_required`（默认 1=需验证，**免验证开关默认关**）。对方免验证则申请直接 accepted+auto_accepted=1（仍留 row 与 `friend.added` 通知作历史）。通知**复用现有消息管道**：以 `NotificationContent`（新增 event_type `friend.request`/`friend.accepted`/`friend.added`）写入接收方 **SYSTEM 会话**，免费获得 seq/多端同步/离线增量；联系人"通知"入口 = 渲染 SYSTEM 会话历史。**状态唯一真相在 `friend_request` 表，通知只带 request_id，客户端按 id 查当前态渲染按钮**。黑名单→静默失败（不建 row、不通知）；拒绝/忽略不通知申请方、可再次申请、无冷却。好友/关系逻辑归 **user 模块**（与 `UserRpc.CheckRelation`/黑名单同域，不新建模块）。proto 仅新增内部 RPC `MessageRpc.SendSystemNotification`，**content.proto 零改动** | 现有 `ConvType.SYSTEM` + `NotificationContent.event_type` 三层（ConvType/oneof/event_type）已满足"主类型/子类型/体"，无需再加全局消息大类型（会与 cmd/ConvType/oneof 四维打架）；状态表与不可变消息流解耦，避免改写历史消息；复用消息管道直接拿到多端同步与离线补齐 |
| D41 | 2026-06-15 | **跨租户好友只留配置位、不实现**：新增 `user.allow_cross_tenant_friend TINYINT(1) DEFAULT 0`（默认仅租户内），MVP 仅做租户内好友。跨租户实际打通（关系读取绕过 tenant 拦截器、跨租户会话 seq 归属、MQ/审核策略归属）与 D3/D21 隔离模型冲突，等同联邦，**推二阶段独立设计** | 不在加好友小功能里顺手改动整个多租户隔离模型，风险不成比例；自营 App 用户全在租户 1，租户内已覆盖 C 端社交图谱 |
| D42 | 2026-06-15 | **新增唯一用户名 `user.username`**（自填、租户内唯一、可分享的"加我"标识，类 Telegram @username / 微信号）：`VARCHAR(32) NULL`，`UNIQUE(tenant_id, username)`，格式 `^[a-z][a-z0-9_]{5,31}$`（字母开头，小写字母/数字/下划线，6–32 位），设置后可改但加频率限制（具体频率实现期定）；visitor/agent 不分配。**加好友查找改为精确匹配 `username` 或完整手机号，收紧原 `account` 前缀模糊搜索**（`account`=登录凭证/手机号，前缀可枚举即隐私泄露）。`account`（登录/手机号）与 `username`（对外标识）职责分离 | 现有 `account` 唯一但等于登录凭证/手机号，不宜作对外加好友标识；昵称不唯一无法定位；标准 IM 均用独立可分享 handle |
| D43 | 2026-07-02 | **网关结构性优化四项**（源自 docs/reviews/2026-07-02-structure-review-gateway-server.md，Jade 拍板直接实施）：**R3** `UPSTREAM_GRPC` 支持逗号分隔多地址，多地址走 tonic `Channel::balance_list`（tower p2c），单地址保持启动即连 fail-fast——打通 Java 多实例扩容路径；**R2** 删除全局 `PendingAcks` 表，pending ack 移入 `ConnectionHandle`（req_id→deadline 局部 DashMap），writer select 内 1s sweeper 判超时（精度 ±1s，语义仍"约 10s 未 ack 断连"），断连清理 O(1) 随 handle 消亡，不再 per-push spawn 定时任务；**R1** axum 0.7→0.8 + tonic-build `.bytes(["."])`，proto bytes 字段全为 `bytes::Bytes`；广播（need_ack=false）整只信封预编码一次、目标间引用计数共享（`Outbound::Encoded`），uplink 解码改从 `Bytes` 零拷贝切 body；Java 侧 J1：`PushDispatchService.publishGrouped` `copyFrom`→`UnsafeByteOperations.unsafeWrap`；**R5** 新增 `Upstream` trait（async_trait，`AppState.rpc: Arc<dyn Upstream>`），`run_connection` 收发端泛型化（任意 Sink/Stream），新增 3 条连接状态机测试（认证成功+dispatch+清理 / 重放拒绝 / token 失败）与 2 条 pending ack 单测。附带修正：writer select 加 `biased`（出队帧先于 close 刷出，保证 KICK 在断连前送达）。⚠️ **待在有 Rust 工具链的环境跑 `cargo test`（axum 0.8 升级需重拉依赖）+ Java 侧编译**，沙箱无 rustc/maven 未能本地验证，已做逐文件静态自审 | 均为审查报告 P0/P1 项：R3 是唯一与"预留水平扩展点"（D4）冲突的实现；R2 消除万级在线下的 timer 任务风暴与断连全表扫描；R1 使 500 人群广播从 300 次编码+拷贝降为 1 次编码+引用计数；R5 补上网关核心状态机的测试空白 |
| D44 | 2026-07-02 | **im-app 网络层审查修复**（docs/reviews/2026-07-02-im-app-network-review.md）：**A1 跨端 P0** REVOKE_NOTIFY 误传 `needAck=true`（协议 §3 规定 need_ack 仅 MSG_PUSH）而客户端只 ack MSG_PUSH → 在线端收撤回 10s 后被网关判半死链踢断；修复=Java `MsgRevokedEventConsumer` 改 false（漏收由 SYNC/历史 status=REVOKED 兜底）+ Flutter `_ackPushIfNeeded` 防御性泛化（任何推送帧 req_id≠0 一律回 MSG_RECV_ACK 空 items，响应帧 MSG_SEND_ACK/SYNC_RESP/ERROR 不 ack）。**A2 P0** ImSocket `cancelOnError:true` 下 onError 后 onDone 不触发且 `_onError` 不调度重连 → 认证后 socket error 留下最长 ~75s"假在线"黑洞；修复=`_onError` 复用 onDone 清理+重连路径。**B1** 删除 32 个零引用死文件（废弃 clean-architecture 脚手架：domain/、core/network/ 旧自定义 packet 协议层、data/local/db/ 平行 DB 层、repositories/*_impl、proto/generated 占位）——其中 `core/network/packet_codec` 是与 im-proto Frame 冲突的旧协议，误导风险高。附带：收帧路径 2 处逐帧整帧拷贝消除。⚠️ 待本机 `flutter analyze/test` + `mvn -pl im-push-service test`，沙箱无工具链 | 撤回踢线是"D28 网关按 req_id 跟踪 ack"与"Java 推送 needAck 参数"跨三端的隐性契约断裂，文档为真相源；客户端泛化 ack 使未来新增 need_ack 推送类型对旧客户端前向兼容 |
| D45 | 2026-07-02 | **1v1 实时语音通话 MVP**（Jade 拍板三项：实时通话 / WebRTC P2P+自建 coturn / 仅 1v1 语音，详见 docs/call-service-design.md）：媒体 P2P+TURN 中继（服务端不碰媒体），信令走现有 WS 帧（D19 网关零改动）。proto：frame.proto 新增 CALL_INVITE(40)/ANSWER(41)/SIGNAL(42)/HANGUP(43)/CALL_NOTIFY(45)/CALL_ACK(49)，新建 body/call.proto（SDP/ICE 不透明透传、client_call_id 幂等、media 预留 VIDEO），error.proto 新增 7xxx 通话段。**协议 §3 修订：CALL_NOTIFY 是 MSG_PUSH 之外唯一 need_ack=true 帧**（振铃丢失不可接受；D44 客户端泛化 ack 保证前向安全）。Java：新模块 im-call-service——Redis 状态机（INVITING→ACTIVE→删除，Lua 原子 CAS 多端先接者赢；busy/idem/deadline 键同生共死）、4 个 CmdHandler、TURN REST 凭证（HMAC-SHA1，与 coturn use-auth-secret 共享密钥）、超时 sweeper（全局 deadline ZSET+原子 claim，多实例安全，与 OutboxPoller 同构）、call_record CDR（V13 迁移）。规则：忙线服务端代答不打扰被叫；全端振铃先接者赢其余端 ANSWERED_ELSEWHERE；60s 超时；被叫全端离线 INVITE 直接代答 CALL_PEER_OFFLINE（MVP 无离线推送）。Flutter：flutter_webrtc + data/call/CallEngine（信令+PC 状态机，trickle ICE 候选缓存，主叫收 ACCEPTED 才 createOffer）+ features/call/CallPage + LumoApp 全局来电路由 + 聊天页 C2C 拨打入口 + 四端麦克风权限。部署：compose 新增 coturn（host 网络，UDP 3478+49160-49200）。⚠️ 待：proto 三端重生成（tool/generate_proto.sh + maven + cargo）、全量编译测试、双人真机联调；沙箱无工具链未验证。二阶段挂 Open Questions：离线被叫推送唤醒/CallKit、通话记录气泡入会话、断连联动挂断、视频/群通话（SFU） | 客服/社交 IM 的标配能力；P2P+coturn 是 D24 自装路线下零第三方成本的最短路径；信令复用 WS 使网关与推送链路零新增组件 |

## 设计文档索引

- docs/architecture.md（总体架构）/ docs/protocol.md（协议）/ docs/im-server-design.md（Java 侧模块设计，2026-06-13 待 Jade 评审）
- docs/cs-service-design.md（客服会话设计，2026-06-13 确定，待实现）
- docs/friend-service-design.md（好友申请/关系设计，2026-06-15 确定，待实现，关联 D40/D41）
- docs/call-service-design.md（1v1 语音通话设计，2026-07-02 确定并实现，关联 D45）
- Java 根包名 `com.im`；模块依赖铁律：业务模块互相禁止依赖，跨模块走 in-process gRPC（enforcer 固化）

## 技术栈

- 网关：Rust stable, tokio, axum ws, tonic(gRPC client), prost(protobuf), lapin(RabbitMQ)；MVP 路由表由 Java push 模块通过 ConnEvent 维护
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
- [x] 客服会话 resolved 后访客重开：**已定——新建会话**（D31，MVP 基础款，二阶段可改为租户配置）

- [ ] 协议演进候选 E1~E6（对比 OpenIM/Tinode/Matrix 得出，见 docs/protocol.md §7）：E1 conv_list_version 语义补全（群聊后立即）、E2 typing/presence（客服前）、E3 引用回复、E4 reactions、E5 压缩缓、E6 编辑不排期
- [ ] 推送第三方通道选型（APNs/FCM/厂商通道）放第二阶段
- [ ] 第三方内容安全 API 选型（阿里云内容安全/网易易盾/数美）——接口先抽象，MVP 可只接文本词库
- [ ] 消息全文搜索（ES vs 客户端 SQLite FTS）——二阶段
- [x] 好友申请流程：**已定 D40**（per-user `friend_verify_required` 验证开关与 D17 tenant 级 `friend_required` 正交——前者管"加我要不要验证"、后者管"是否必须好友才能发消息"，由 `UserRpc.CheckRelation` 承载）
- [ ] 跨租户好友打通（D41 已留配置位 `allow_cross_tenant_friend`）——二阶段独立设计：关系读取绕过拦截器、跨租户会话 seq 归属、MQ/审核策略归属
- [ ] 好友列表/申请历史的分页与多端已读位（通知红点跨端同步）细节——实现期定
- [ ] 客服坐席分配策略（轮询/最少会话/技能组）——第二阶段细化
- [ ] 二阶段若括高群上限（>500）：推送降级为"脏通知+拉取"、@提及单独写扩散、成员列表懒加载
- [ ] 认证审核流程（人工审核 or 对接企业资质 API）——管理后台二阶段
- [x] **发送方自回显 MSG_PUSH 去重**（2026-06-16 提出，**同日实现**：im-proto `MsgSavedEvent` 新增 `sender_conn_id=7`；`MessagePersistService` 传 `ctx.getConnId()` → `MsgSavedEventFactory.create/toProto`（新增重载，保留旧 2 参）；`MsgSavedEventConsumer` 调 `pushToUsers(...,excludeUserId=senderId,excludeConnId=senderConnId)` 复用 `isExcluded` 仅排发起连接。系统通知 connId 为空串自动不排除。⚠️ **待在 JDK21+maven 环境跑 proto 重生成 + 全量编译/测试**，沙箱无 maven 未能本地验证）。原始分析：C2C 消息 `MsgSavedEventConsumer.buildRecipients` 返回的成员含发送者本人，`PushDispatchService.pushToUsers` 未排除发起连接，导致发送方**发起连接**收到自己刚发的消息回显（need_ack=true，多 1 下行 + 1 上行 MSG_RECV_ACK/条）。客户端按 `MsgPush.client_msg_id` 已去重，**无重复气泡**，仅浪费帧。修复**不能**简单排除发送者 user（会断发送方其他端实时多端同步——本设计多端靠 MSG_PUSH，无独立 self-sync 通道）。正确方案：im-proto `MsgSavedEvent` 新增 `sender_conn_id`（字段号只增），从 gateway 上行 ConnCtx 串到 message-service → outbox event → push 消费者，调用 `pushToUsers` 时传 `excludeConnId`（复用现有 `isExcluded` 仅排发起连接、保留其他端）。跨 proto+message-service+push-service 三处，需 §5 proto 重新生成（Rust/Java 同步）。注：read-notify 扇出已正确用 `excludeConnId` 去重发起连接（GrpcReadReceiptPusher:38 / PushDispatchService:147），无需改动。优先级低（规模 1~5 万可接受）
