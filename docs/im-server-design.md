# im-server 设计文档（Maven 多模块骨架）v0.1

> 状态：设计稿，写代码前最后评审。依据：D2/D5（虚拟线程、模块化单体）、核心约定 1/2/5。
> 评审通过后按本文档生成工程骨架，业务逻辑后续按模块逐个实现。

## 1. 技术基线

| 项 | 选型 | 说明 |
|---|---|---|
| JDK | 21（LTS） | 全局开虚拟线程：`spring.threads.virtual.enabled=true`；gRPC server executor 也用虚拟线程 |
| 框架 | Spring Boot 3.2.x | 父 POM 统一 BOM |
| ORM | MyBatis-Plus 3.5.x | 配租户拦截器（核心约定 1）；禁用其逻辑删除插件（删除语义各表自定义） |
| gRPC | grpc-java 1.6x + protobuf 3.25.x | in-process server/channel（D5 拆分关键） |
| 构建 | Maven 3.9 多模块 | 根 pom 只做版本管理与模块聚合 |
| 根包名 | `com.im` | 与 im-proto 的 java_package（com.im.proto.*）一致 |
| 其他 | lombok、micrometer-prometheus、flyway（暂禁用，schema 演进期启用） | dev 快速起步用 compose 的 init SQL |

## 2. 模块划分与依赖规则

```
im-server (parent pom)
├── im-proto-java            # 唯一执行 protobuf-maven-plugin 的模块，源 = ../im-proto/proto
├── im-common                # 横切组件，禁止任何业务逻辑
├── im-user-service          #
├── im-message-service       #  六个业务模块：
├── im-conversation-service  #  只依赖 im-common + im-proto-java，
├── im-group-service         #  ★ 互相之间禁止依赖（编译期强制隔离）
├── im-file-service          #
├── im-push-service          #
└── im-bootstrap             # 唯一可执行模块：依赖全部，组装单进程
```

**依赖铁律**（用 maven-enforcer 规则固化）：

1. 业务模块 POM 中不得出现其他业务模块坐标——跨模块调用一律走 im-proto-java 的 gRPC stub + in-process channel
2. im-common 不依赖任何业务模块（防倒置）
3. 只有 im-bootstrap 有 spring-boot-maven-plugin（可执行 jar）

拆分路径（D5 兑现方式）：把某模块从 bootstrap 依赖中移除 → 给它自己的启动类 → 把 in-process channel 地址换成网络地址（配置项，零代码改动）。

## 3. im-common 内容清单

| 包 | 内容 |
|---|---|
| `com.im.common.tenant` | TenantContext（**普通 ThreadLocal + finally 清理**，D25 修订：不用 ScopedValue——JDK21 预览特性，生产运行需 --enable-preview，不值）、MyBatis 租户拦截器（自动注入 tenant_id 条件与插入列）、gRPC ServerInterceptor/ClientInterceptor（metadata 透传 tenant_id/trace_id，核心约定 1） |
| `com.im.common.id` | Snowflake 生成器（workerId 取自实例配置）+ Redis SETNX workerId 租约（冲突 fail-fast，TTL 续期，D6/S6） |
| `com.im.common.error` | ImException + ErrorCode 映射（对齐 common/error.proto 分段） |
| `com.im.common.outbox` | OutboxWriter（同事务写入）+ OutboxPoller（虚拟线程轮询 100ms，批量投 RabbitMQ confirm 后删，D18） |
| `com.im.common.mq` | RabbitMQ 封装：exchange/queue 声明（im.events topic）、pb 序列化、消费幂等模板（按 event_id） |
| `com.im.common.redis` | 键位常量类（route:/online:/token_ver:/dedup:/im:worker:，对齐架构 §7）；seq 主路径按 D26 走 MySQL conversation 行锁自增 |
| `com.im.common.device` | PlatformClass 映射（iOS/Android→MOBILE，Win/Mac→DESKTOP，Web/小程序→WEB），供 token_ver 和路由表统一按平台类限流/互踢 |
| `com.im.common.uplink` | **CmdHandler SPI**：`interface CmdHandler { int cmd(); byte[] handle(ConnCtx, byte[]); }`——业务模块实现并注册为 Spring Bean，路由器收集成表 |
| `com.im.common.trace` | trace_id 生成/透传工具（WS帧→gRPC metadata→MQ header，§13.4） |

## 4. 业务模块统一内部结构

```
com.im.{module}/
├── grpcapi/    # gRPC service 实现（实现 im-proto rpc/internal.proto + rpc/gateway.proto 中属于本模块的 service）
├── handler/    # CmdHandler 实现（处理属于本模块的 WS 业务帧，如 message 模块的 MSG_SEND/SYNC_REQ）
├── rest/       # REST controller（protocol.md §5 中属于本模块的接口）
├── service/    # 业务逻辑
├── dao/        # MyBatis-Plus mapper + entity
└── config/     # 模块自身装配
```

各模块职责与归属接口：

| 模块 | gRPC service | CmdHandler | REST |
|---|---|---|---|
| user | GatewayAuth、UserRpc | — | /auth/login、/auth/register、/auth/refresh、/users/me、黑名单 CRUD |
| message | MessageRpc | MSG_SEND、SYNC_REQ | /convs/{id}/messages（历史分页）、撤回 |
| conversation | ConversationRpc | READ_REPORT | 会话置顶/免打扰/删除 |
| group | — | — | 建群/加人/踢人/改名（产生 NotificationContent 系统消息） |
| file | — | — | /files/presign、上传确认 |
| push | PushRpc、ConnEvent | MSG_RECV_ACK 缺失上报处理 | — |

moderation（D16）作为 message 模块内子包 `com.im.message.moderation` 起步（消费 msg.saved），二阶段需要独立扩缩时再抽模块。

**T26 群聊实现约定（2026-06-13 补充）**：

- group-service 拥有群资料与群成员的 REST 写入口；建群/加人/踢人/改名必须与 `GROUP conversation`、`conversation_member`、`NotificationContent` 系统消息和 outbox 写入处于同一个 MySQL 事务。
- 为避免业务模块编译期互依赖，group-service 可定义本模块私有 DAO model 操作 `conversation`、`conversation_member`、`message`、`outbox` 共享表；禁止依赖 conversation/message 模块内部类。
- 普通群文本消息仍走 message-service `MSG_SEND`，由 ConversationRpc 解析 `group_id/conv_id` 并校验当前用户是 active conversation member。
- 群成员变更事件统一落为 `NotificationContent`，事件名登记在 `docs/protocol.md` 附录 A；不新增旁路通知通道。

## 5. im-bootstrap 组装

- `ImApplication`：`@SpringBootApplication(scanBasePackages = "com.im")`
- **UplinkRouter**：实现 rpc/gateway.proto 的 Uplink service——注入全部 CmdHandler Bean 建 cmd→handler 表；
  入口防御（D19 代价的兜底）：cmd 未注册→ERROR(ErrorBody)；body 解码失败→ERROR + WARN 日志带 trace_id
- gRPC server 双形态：对网关的 NettyServer(:9091, 虚拟线程 executor) + in-process server（模块间用）；
  客户端 stub 统一经 `ChannelProvider`：配置 `im.rpc.{service}.address=inprocess|host:port` 决定走向（拆分开关）
- REST :8081（含 /actuator/prometheus）
- 配置文件：application.yml（公共）+ application-local.yml（连 compose 中间件）+ application-docker.yml（容器内，对齐 compose 环境变量）

## 6. 启动期自检（fail-fast）

依次校验并打印：MySQL 连通+schema 版本、Redis 连通、RabbitMQ 连通+声明 exchange/队列、MinIO bucket 存在、
Snowflake workerId 无冲突（Redis SETNX 租约）。任一失败直接退出——容器编排的 healthcheck/depends_on 配合重启。

## 7. 测试约定

- 单测：service 层 JUnit5 + Mockito，不起 Spring
- 集成测试：Testcontainers（mysql/redis/rabbitmq）跑 dao/outbox/幂等等关键路径；CI 必跑
- 端到端：二阶段配合网关做（WS 模拟客户端脚本）

## 8. MVP 实现顺序（对齐 protocol.md）

1. 骨架：parent pom + 9 模块空工程 + im-proto-java 生成通过 + bootstrap 空跑
2. im-common：TenantContext/拦截器/ID/错误码 + 启动自检
3. user：注册登录(JWT) + GatewayAuth.VerifyToken
4. message + conversation：MSG_SEND 全链路（seq/落库/outbox）+ SYNC_REQ
5. push：路由表查询 + PushEnvelope 投递 + 互踢/token_ver（D27）
6. 已读/群/文件/审核最小版
