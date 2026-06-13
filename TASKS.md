# TASKS.md — im-server MVP 开发任务拆分

> 生成日期：2026-06-13
> 依据：`AGENTS.md`、`README.md`、`docs/architecture.md`、`docs/im-server-design.md`、`docs/review-checklist.md`。
> 当前规则：每次只执行本文档中的一个任务；开始前说明任务计划，完成后总结改动、启动方式、测试方式与未完成事项。

## 0. 当前项目结构检查

当前仓库已完成初始化提交，工作区干净。

已存在：

- `AGENTS.md`：项目决策、技术约定、开放问题。
- `README.md`：项目目录和阶段说明。
- `docs/architecture.md`：总体架构。
- `docs/im-server-design.md`：Java 侧模块设计。
- `docs/review-checklist.md`：实现和审查拒收线。
- `im-proto/proto/**`：已落地 proto 定义。
- `deploy/docker-compose/**`：中间件编排、初始 schema、观测配置。
- `im-server/*`：已有 Maven 多模块骨架；业务源码尚未开始。

本次新增：

- `TASKS.md`：后续开发任务队列。

## 1. MVP 范围约束

### 1.1 当前阶段优先做

- `im-server` 可编译、可启动、可测试。
- Java proto 生成链路。
- 统一工程结构、模块边界、配置和健康检查。
- 用户注册、登录、刷新令牌、查询当前用户。
- 网关鉴权所需的 `GatewayAuth.VerifyToken`。
- 单聊 C2C 会话解析和创建。
- 文本消息发送：幂等、分配会话级 seq、落库、写 outbox。
- 增量同步和历史分页：客户端可通过 seq 拉取消息。
- 基础测试：单测、关键路径集成测试、可复现启动命令。

### 1.2 当前阶段暂不做

- Rust 网关完整实现。
- Web/App 客户端。
- 群聊。
- 已读回执。
- 多端同步和同类互踢。
- 离线第三方推送。
- 文件上传和 MinIO 预签名。
- 内容安全审核和复杂风控。
- 客服会话状态机。
- 大规模分布式网关和 K8s。

说明：这些能力仍保留在架构文档中，但不进入当前 im-server MVP 任务队列，避免过早复杂化。

## 2. 执行规则

1. 每次只领取一个 `PENDING` 任务。
2. 任务开始前先说明：
   - 本次任务编号和目标。
   - 预计修改文件。
   - 验收和测试计划。
3. 任务完成后必须总结：
   - 改了哪些文件。
   - 实现了哪些功能。
   - 如何启动。
   - 如何测试。
   - 是否有未完成事项。
4. 能运行测试就必须运行；不能运行时说明原因。
5. 不删除已有代码，不做无关重构。
6. 新增表结构不直接改 `deploy/docker-compose/init/mysql/01-schema.sql`，后续演进走 Flyway migration；当前已有初始 schema 作为 dev 启动基线。
7. proto 改动必须同步保证 Java 生成通过；涉及 Rust 网关编译时再补 Rust 生成验证。
8. 业务模块之间禁止直接依赖其他业务模块，跨模块只走 proto/gRPC 契约。

## 3. 开发任务队列

### T00 — 任务拆分文档

状态：DONE

目标：

- 根据当前项目结构生成本文件，形成后续开发任务队列。

涉及模块：

- 根目录文档。

需要修改的文件：

- `TASKS.md`

验收标准：

- 文档包含 MVP 范围、执行规则和独立任务拆分。
- 每个后续任务包含目标、涉及模块、需要修改的文件、验收标准、测试方式。

测试方式：

- `git status --short`
- 人工审阅 `TASKS.md`。

---

### T01 — im-server Maven 多模块骨架与 proto Java 生成

状态：DONE

目标：

- 建立 `im-server` Maven 父工程和 9 个子模块。
- 新增 `im-proto-java`，作为唯一执行 protobuf 生成的模块。
- 保证 proto 文件可以生成 Java 类和 gRPC stub。

涉及模块：

- `im-server`
- `im-server/im-proto-java`
- `im-server/im-common`
- `im-server/im-user-service`
- `im-server/im-message-service`
- `im-server/im-conversation-service`
- `im-server/im-group-service`
- `im-server/im-file-service`
- `im-server/im-push-service`
- `im-server/im-bootstrap`

需要修改的文件：

- `im-server/pom.xml`
- `im-server/im-proto-java/pom.xml`
- `im-server/im-common/pom.xml`
- `im-server/im-user-service/pom.xml`
- `im-server/im-message-service/pom.xml`
- `im-server/im-conversation-service/pom.xml`
- `im-server/im-group-service/pom.xml`
- `im-server/im-file-service/pom.xml`
- `im-server/im-push-service/pom.xml`
- `im-server/im-bootstrap/pom.xml`
- 必要时更新 `im-server/README.md`

验收标准：

- Maven 模块结构与 `docs/im-server-design.md` 一致。
- 只有 `im-proto-java` 配置 protobuf 生成插件。
- 业务模块只依赖 `im-common` 和 `im-proto-java`，不互相依赖。
- `mvn verify` 至少能完成编译阶段，生成 proto Java 文件。

测试方式：

- 在 `im-server` 目录执行 `mvn -q verify`。
- 检查 `im-server/im-proto-java/target/generated-sources` 下存在 proto 生成结果。

完成记录：

- 已新增 Maven 父工程和 9 个子模块 POM。
- 已将 protobuf 生成限制在 `im-proto-java` 模块。
- 已用 `xmllint --noout` 校验所有 POM XML 可解析。
- 已使用 JDK 21 和 IDEA 自带 Maven 执行 `mvn -q verify`，验证通过。

---

### T02 — im-bootstrap 空应用、健康检查与基础配置

状态：DONE

目标：

- 让 `im-bootstrap` 成为唯一可执行 Spring Boot 应用。
- 提供基础配置文件、虚拟线程开关、Actuator health endpoint。
- 为后续模块装配提供稳定入口。

涉及模块：

- `im-server/im-bootstrap`
- `im-server/im-common`

需要修改的文件：

- `im-server/im-bootstrap/pom.xml`
- `im-server/im-bootstrap/src/main/java/com/im/bootstrap/ImApplication.java`
- `im-server/im-bootstrap/src/main/resources/application.yml`
- `im-server/im-bootstrap/src/main/resources/application-local.yml`
- `im-server/im-bootstrap/src/main/resources/application-docker.yml`
- `im-server/im-bootstrap/src/test/java/com/im/bootstrap/ImApplicationTest.java`

验收标准：

- 应用可启动。
- `spring.threads.virtual.enabled=true` 生效在配置中。
- `/actuator/health` 返回 `UP`。
- 只有 `im-bootstrap` 使用 `spring-boot-maven-plugin`。

测试方式：

- `mvn -q -pl im-bootstrap -am test`
- `mvn -q -pl im-bootstrap -am spring-boot:run`
- 访问 `http://localhost:8081/actuator/health`。

完成记录：

- 已新增 `ImApplication`，扫描根包 `com.im`。
- 已新增 `application.yml`、`application-local.yml`、`application-docker.yml`。
- 已在 `im-bootstrap` 绑定 `spring-boot-maven-plugin:repackage`，生成唯一可执行 Boot jar。
- 已新增 `ImApplicationTest` 并通过 Spring context 启动测试。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`。
- 注：普通沙箱下 Maven 写 `~/.m2`、Mockito agent attach、Java 绑定 8081 会受限，验证时使用授权环境执行。

---

### T03 — 公共错误、统一 REST 响应、trace 与基础工具

状态：DONE

目标：

- 建立可维护的公共层，不掺杂业务逻辑。
- 提供 `ImException`、错误码映射、统一 REST 响应结构、trace id 工具。
- 后续 REST/gRPC/MQ 日志统一携带 `tenant_id` 和 `trace_id`。

涉及模块：

- `im-server/im-common`
- `im-server/im-bootstrap`

需要修改的文件：

- `im-server/im-common/src/main/java/com/im/common/error/ErrorCode.java`
- `im-server/im-common/src/main/java/com/im/common/error/ImException.java`
- `im-server/im-common/src/main/java/com/im/common/web/ApiResponse.java`
- `im-server/im-common/src/main/java/com/im/common/web/GlobalExceptionHandler.java`
- `im-server/im-common/src/main/java/com/im/common/trace/TraceContext.java`
- `im-server/im-common/src/main/java/com/im/common/trace/TraceIdGenerator.java`
- `im-server/im-common/src/test/java/com/im/common/**`
- 必要时更新 `im-server/im-bootstrap` 装配类。

验收标准：

- REST 异常统一返回结构化 JSON。
- 错误码与 `im-proto/proto/common/error.proto` 分段保持一致。
- 未捕获异常不直接暴露堆栈给客户端。
- trace id 可生成、可透传、可在日志中读取。

测试方式：

- `mvn -q -pl im-common -am test`
- 增加单测覆盖错误响应和 trace id 生成。

完成记录：

- 已新增 `ErrorCode`，覆盖现有 proto 错误码数值，并补充 REST 层通用 `VALIDATION_FAILED`、`INTERNAL_ERROR`。
- 已新增 `ImException`，业务异常统一携带 `ErrorCode`。
- 已新增 `ApiResponse`，统一 REST 响应结构为 `code/message/data/traceId/timestamp`。
- 已新增 `GlobalExceptionHandler`，统一处理 `ImException`、参数校验异常、非法参数和未知异常。
- 已新增 `TraceIdGenerator` 和 `TraceContext`，使用 SLF4J MDC 的 `trace_id` 作为框架级 trace 上下文。
- 已新增单测覆盖错误码映射、异常默认消息、trace 上下文恢复、响应结构和异常处理 JSON。
- 已执行 `mvn -q -pl im-common -am test`，通过。
- 已执行完整 `mvn -q verify`，通过。

---

### T04 — TenantContext、MyBatis-Plus 与数据库访问基线

状态：DONE

目标：

- 建立租户上下文和数据库访问基线。
- 用普通 `ThreadLocal` + finally 清理承载 tenant（D25，不依赖 JDK preview）。
- 配置 MyBatis-Plus、数据源和基础 mapper。

涉及模块：

- `im-server/im-common`
- `im-server/im-bootstrap`

需要修改的文件：

- `im-server/im-common/src/main/java/com/im/common/tenant/TenantContext.java`
- `im-server/im-common/src/main/java/com/im/common/tenant/TenantRequiredException.java`
- `im-server/im-common/src/main/java/com/im/common/mybatis/MybatisPlusConfig.java`
- `im-server/im-common/src/main/java/com/im/common/mybatis/TenantLineHandlerConfig.java`
- `im-server/im-common/src/test/java/com/im/common/tenant/**`
- `im-server/im-common/src/test/java/com/im/common/mybatis/**`
- `im-server/im-bootstrap/src/main/resources/application-local.yml`
- `im-server/im-bootstrap/src/main/resources/application-docker.yml`

验收标准：

- 缺失 tenant 上下文时 fail-closed。
- SELECT/UPDATE/DELETE 自动注入 tenant 条件。
- INSERT 自动写入 `tenant_id`。
- 应用代码不手写 tenant 过滤条件。
- 本任务不新增业务 REST 接口。

测试方式：

- `mvn -q -pl im-common -am test`
- 使用 MyBatis SQL parser 相关单测覆盖 SELECT、UPDATE、DELETE、INSERT。
- 如需集成测试，用 Testcontainers MySQL 验证 mapper 行为。

完成记录：

- 已新增 `TenantContext`，使用普通 `ThreadLocal` 承载租户上下文并在 finally 中恢复/清理，缺失 tenant 时通过 `TenantRequiredException` fail-closed。
- 已新增 MyBatis-Plus `TenantLineInnerInterceptor` 装配，统一处理 SELECT、UPDATE、DELETE 和 INSERT 的 `tenant_id` 注入。
- 已将 `tenant`、`flyway_schema_history` 加入租户拦截忽略表。
- 已为 local/docker profile 补充 MySQL datasource 基线配置，供后续 mapper 和集成测试复用。
- 已在 PR1 复审修复中移除全部 `--enable-preview`，README 记录裸 `java -jar` 启动。
- 已新增单测覆盖租户上下文绑定/恢复、缺失租户失败、SELECT/UPDATE/DELETE/INSERT SQL 改写和显式 `tenant_id` 不重复注入。
- 已执行 `mvn -q -pl im-common -am test`，通过。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`。

---

### T05 — 基础 Entity、Mapper 与数据库集成测试框架

状态：DONE

目标：

- 为 im-server MVP 所需核心表建立 Entity/Mapper。
- 建立 Testcontainers 集成测试基线，后续任务复用。

涉及模块：

- `im-server/im-common`
- `im-server/im-user-service`
- `im-server/im-conversation-service`
- `im-server/im-message-service`

需要修改的文件：

- `im-server/im-user-service/src/main/java/com/im/user/dao/entity/UserEntity.java`
- `im-server/im-user-service/src/main/java/com/im/user/dao/mapper/UserMapper.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/dao/entity/ConversationEntity.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/dao/entity/ConversationMemberEntity.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/dao/mapper/ConversationMapper.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/dao/mapper/ConversationMemberMapper.java`
- `im-server/im-message-service/src/main/java/com/im/message/dao/entity/MessageEntity.java`
- `im-server/im-message-service/src/main/java/com/im/message/dao/entity/OutboxEntity.java`
- `im-server/im-message-service/src/main/java/com/im/message/dao/mapper/MessageMapper.java`
- `im-server/im-message-service/src/main/java/com/im/message/dao/mapper/OutboxMapper.java`
- `im-server/im-common/src/test/java/com/im/common/test/IntegrationTestSupport.java`

验收标准：

- Entity 字段与 `deploy/docker-compose/init/mysql/01-schema.sql` 当前核心表一致。
- Mapper 基础 CRUD 可用。
- 集成测试可以启动 MySQL 并初始化 schema。
- 不新增业务行为。

测试方式：

- `mvn -q -pl im-user-service,im-conversation-service,im-message-service -am test`
- Testcontainers MySQL 集成测试验证核心 mapper 插入/查询。

完成记录：

- 已新增 `UserEntity/UserMapper`，覆盖 `user` 核心字段。
- 已新增 `ConversationEntity/ConversationMemberEntity` 及对应 mapper，覆盖 `conversation`、`conversation_member`。
- 已新增 `MessageEntity/OutboxEntity` 及对应 mapper，覆盖 `message`、`outbox`。
- 已新增 `IntegrationTestSupport`，使用 Testcontainers MySQL 8.4，并复用 `deploy/docker-compose/init/mysql/01-schema.sql` 初始化 schema。
- 已为 user/conversation/message 模块补充 MyBatis-Plus core 编译依赖和测试期 MyBatis-Plus Boot/Testcontainers/MySQL 依赖；业务模块之间仍无互相依赖。
- 已将 `MybatisPlusConfig` 改为仅在 MyBatis-Plus Boot 自动配置存在时启用，避免 bootstrap 默认 profile 无数据源时注册 mapper。
- 已新增 mapper 集成测试，覆盖插入、查询、部分更新及租户隔离语义；当前环境没有 Docker socket，Testcontainers 测试被跳过：user 1 个、conversation 2 个、message 2 个。
- 已执行 `mvn -q -pl im-user-service,im-conversation-service,im-message-service -am test`，命令通过；容器集成测试因 Docker 不可用跳过。
- 已执行完整 `mvn -q verify`，通过。

---

### T06 — 用户注册、登录、刷新令牌与当前用户接口

状态：DONE

目标：

- 实现 MVP 账号密码联调能力。
- 生产方向保留手机号验证码，但本任务只做账号密码。
- 提供 JWT access token 和 refresh token。

涉及模块：

- `im-server/im-user-service`
- `im-server/im-common`
- `im-server/im-bootstrap`

需要修改的文件：

- `im-server/im-user-service/src/main/java/com/im/user/rest/AuthController.java`
- `im-server/im-user-service/src/main/java/com/im/user/rest/UserController.java`
- `im-server/im-user-service/src/main/java/com/im/user/service/AuthService.java`
- `im-server/im-user-service/src/main/java/com/im/user/service/JwtService.java`
- `im-server/im-user-service/src/main/java/com/im/user/service/PasswordService.java`
- `im-server/im-user-service/src/main/java/com/im/user/dto/RegisterRequest.java`
- `im-server/im-user-service/src/main/java/com/im/user/dto/LoginRequest.java`
- `im-server/im-user-service/src/main/java/com/im/user/dto/TokenResponse.java`
- `im-server/im-user-service/src/main/java/com/im/user/dto/UserProfileResponse.java`
- `im-server/im-user-service/src/test/java/com/im/user/**`

验收标准：

- `POST /api/v1/auth/register` 可创建 tenant 1 下用户。
- `POST /api/v1/auth/login` 可返回 access token 和 refresh token。
- `POST /api/v1/auth/refresh` 可刷新 token。
- `GET /api/v1/users/me` 可读取当前用户。
- 密码使用 bcrypt。
- 登录失败不区分账号不存在和密码错误。
- JWT 密钥来自配置，不写死在代码。

测试方式：

- `mvn -q -pl im-user-service,im-bootstrap -am test`
- MockMvc 测试注册、登录、刷新、查询当前用户。

完成记录：

- 已新增 `AuthController`，提供 `POST /api/v1/auth/register`、`POST /api/v1/auth/login`、`POST /api/v1/auth/refresh`。
- 已新增 `UserController`，提供 `GET /api/v1/users/me`。
- 已新增 `RegisterRequest`、`LoginRequest`、`RefreshRequest`、`TokenResponse`、`UserProfileResponse`。
- 已新增 `AuthService`，完成账号密码注册、登录、refresh token 换发、当前用户查询；登录失败统一返回 `TOKEN_INVALID`，不区分账号不存在和密码错误。
- 已新增 `PasswordService`，密码使用 BCrypt 哈希和校验。
- 已新增 `JwtService`，使用 HMAC-SHA256 生成/校验 access token 与 refresh token；JWT secret、issuer、TTL 均来自 `im.auth.jwt.*` 配置。
- 已新增 `TenantContextFilter`，REST `/api/**` 请求必须携带 `X-Tenant-Id`，并用 `TenantContext` 绑定到请求执行范围。
- 已新增 `SnowflakeIdGenerator`，用于注册用户时生成 BIGINT 用户 ID。
- 已在 bootstrap 引入 MyBatis-Plus Boot starter，并补充默认 datasource 配置；默认关闭 DB health，避免未启动本地 MySQL 时健康检查被判 DOWN。
- 已补充全局异常处理：缺少必需请求头返回 `VALIDATION_FAILED` 400。
- 已新增 MockMvc 测试覆盖注册、登录、刷新、当前用户、缺租户头和参数校验；新增 service 测试覆盖注册入库字段、重复账号、登录成功、登录失败泛化、封禁用户、刷新、当前用户和跨租户 token 拒绝。
- 已执行 `mvn -q -pl im-user-service,im-bootstrap -am test`，通过；T05 的 UserMapper Testcontainers 测试仍因当前环境无 Docker socket 跳过 1 个。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`。

---

### T07 — GatewayAuth.VerifyToken gRPC 鉴权接口

状态：DONE

目标：

- 实现 Rust 网关后续连接鉴权需要的 gRPC 服务。
- 校验 token、tenant、用户状态，并返回 user_id 和心跳间隔。

涉及模块：

- `im-server/im-user-service`
- `im-server/im-bootstrap`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-user-service/src/main/java/com/im/user/grpcapi/GatewayAuthGrpcService.java`
- `im-server/im-user-service/src/main/java/com/im/user/service/TokenVerifier.java`
- `im-server/im-bootstrap/src/main/java/com/im/bootstrap/grpc/GrpcServerConfig.java`
- `im-server/im-bootstrap/src/main/resources/application.yml`
- `im-server/im-user-service/src/test/java/com/im/user/grpcapi/**`

验收标准：

- `GatewayAuth.VerifyToken` 可校验 T06 生成的 access token。
- 无效、过期、封禁用户返回对应错误码。
- gRPC 服务端口默认 `9091`。
- 响应包含 `user_id` 和 `heartbeat_interval_sec`。
- 暂不实现同类互踢和 token_ver 递增，仅保留接口字段。

测试方式：

- `mvn -q -pl im-user-service,im-bootstrap -am test`
- gRPC 集成测试覆盖有效 token 和无效 token。

完成记录：

- 已新增 `TokenVerifier`，复用 T06 `JwtService` 校验 access token，并校验请求 tenant 与 token tenant 一致。
- 已新增 `GatewayAuthGrpcService`，实现 `GatewayAuth.VerifyToken`；业务错误通过 `VerifyTokenResp.code/message` 返回，不走 gRPC transport error。
- 已新增 `GatewayAuthProperties`，默认 `heartbeat_interval_sec=30`，可通过 `IM_GATEWAY_AUTH_HEARTBEAT_INTERVAL_SEC` 覆盖。
- 已新增 bootstrap `GrpcServerConfig`，启动 Netty gRPC server，默认端口 `9091`，executor 使用 JDK 21 虚拟线程。
- 已在 README 记录默认 REST 端口 `8081` 和 gRPC 端口 `9091`。
- 已新增 gRPC in-process 测试，覆盖有效 token、非法 token、跨租户 token、封禁用户。
- 已执行 `mvn -q -pl im-user-service,im-bootstrap -am test`，通过；T05 的 UserMapper Testcontainers 测试仍因当前环境无 Docker socket 跳过 1 个。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`，`localhost:9091` 端口监听成功。
- 暂未实现 token_ver、同类互踢和 kick_old=true，符合本任务验收范围。

---

### T08 — UplinkRouter 与 CmdHandler SPI

状态：DONE

目标：

- 建立业务帧入口，不实现具体消息业务。
- Java 侧按 `cmd` 路由到模块内 `CmdHandler`。
- 处理未知 cmd 和 body 解码异常，返回 ERROR body。

涉及模块：

- `im-server/im-common`
- `im-server/im-bootstrap`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-common/src/main/java/com/im/common/uplink/CmdHandler.java`
- `im-server/im-common/src/main/java/com/im/common/uplink/CmdHandlerRegistry.java`
- `im-server/im-bootstrap/src/main/java/com/im/bootstrap/grpc/UplinkGrpcService.java`
- `im-server/im-bootstrap/src/test/java/com/im/bootstrap/grpc/UplinkGrpcServiceTest.java`

验收标准：

- `Uplink.Dispatch` 可接收 `cmd + bytes`。
- 未注册 cmd 返回 `ERROR`。
- handler 抛出 `ImException` 时返回结构化 ERROR。
- 未知异常被记录并返回通用 ERROR。
- 不引入业务模块互相依赖。

测试方式：

- `mvn -q -pl im-bootstrap -am test`
- 单测覆盖未知 cmd、成功 handler、异常 handler。

完成记录：

- 已新增 `CmdHandler` SPI，按 `cmd` 接收 `ConnCtx + body bytes` 并返回响应 body bytes。
- 已新增 `CmdHandlerRegistry`，启动时收集 Spring Bean handler，重复 cmd 会直接拒绝启动。
- 已新增 `UplinkGrpcService`，实现 `Uplink.Dispatch` 的统一入口和 tenant 绑定。
- 已统一未知 cmd、`ImException`、未捕获异常、缺失连接上下文的 ERROR body 返回。
- 已新增 `UplinkGrpcServiceTest`，覆盖成功路由、未知 cmd、业务异常、未知异常、缺失 ctx。
- 已执行 `mvn -q -pl im-bootstrap -am test`，通过；T05 的 UserMapper Testcontainers 测试仍因当前环境无 Docker socket 跳过 1 个。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`，`localhost:9091` 端口监听成功。
- 本任务未实现具体业务 handler，符合任务边界。

---

### T09 — C2C 会话解析与创建

状态：DONE

目标：

- 实现单聊会话解析：首次发送时根据 `to_user_id` 创建 C2C conversation 和双方 member。
- 已存在会话时复用，保证并发下不重复创建。

涉及模块：

- `im-server/im-conversation-service`
- `im-server/im-user-service`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-conversation-service/pom.xml`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/grpcapi/ConversationGrpcService.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/service/ConversationService.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/service/ConversationCreator.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/service/C2cKeyGenerator.java`
- `im-server/im-conversation-service/src/test/java/com/im/conversation/**`
- 必要时补充 user 查询接口或 mapper 方法。

验收标准：

- `ResolveConv(to_user_id, from_user_id)` 创建或返回 C2C 会话。
- `c2c_key` 使用小 uid + 大 uid 生成，唯一。
- 并发创建同一 C2C 会话时只有一条 conversation。
- 创建 conversation 时同时创建双方 `conversation_member`。
- 暂不支持 GROUP、CS_SESSION 的业务逻辑。

测试方式：

- `mvn -q -pl im-conversation-service -am test`
- 并发集成测试验证唯一约束和幂等返回。

完成记录：

- 已新增 `C2cKeyGenerator`，`c2c_key` 固定为小 uid + `_` + 大 uid，并拒绝非法 uid 和自聊。
- 已新增 `ConversationCreator`，在一个事务内创建 C2C conversation，并同时插入双方 `conversation_member`。
- 已新增 `ConversationService`，支持 `ResolveConv(to_user_id, from_user_id)` 创建/复用 C2C 会话；重复键并发冲突会回读已有会话。
- 已补充 `ResolveConv(conv_id, from_user_id)` 的 C2C 已有会话路径，用于后续消息发送按 `conv_id` 投递。
- 已新增 `ConversationGrpcService`，实现 `ConversationRpc.ResolveConv`，业务错误通过 `ResolveConvResp.code` 返回；同时补了最小 `GetMembers` 查询。
- 已在 `im-conversation-service` 增加 `spring-tx` 依赖，用于 conversation + member 同事务写入。
- 未直接依赖 `im-user-service`，避免破坏业务模块隔离；用户资料展示和用户存在/关系链校验后续通过 `UserRpc`/关系链任务承接。
- 已新增单元测试覆盖 key 生成、创建字段、复用、创建、重复键回读、按 conv_id 解析、非成员、gRPC 响应码。
- 已新增 Testcontainers 并发集成测试，验证同一 C2C 并发解析只生成一条 conversation 和双方成员；当前环境无 `/var/run/docker.sock`，该测试自动跳过。
- 已执行 `mvn -q -pl im-conversation-service -am test`，通过；conversation 模块单元测试 13 个通过，Testcontainers 测试跳过 3 个。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`，`localhost:9091` 端口监听成功。

---

### T10 — 文本消息发送：seq、幂等、落库、outbox

状态：DONE

目标：

- 实现 C2C 文本消息发送主链路。
- 通过 `CmdHandler` 处理 `MSG_SEND`。
- 返回 `MSG_SEND_ACK`。

涉及模块：

- `im-server/im-message-service`
- `im-server/im-conversation-service`
- `im-server/im-common`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-bootstrap/src/main/java/com/im/bootstrap/grpc/GrpcServerConfig.java`
- `im-server/im-bootstrap/src/main/resources/application.yml`
- `im-server/im-common/src/main/java/com/im/common/grpc/GrpcMetadataKeys.java`
- `im-server/im-common/src/main/java/com/im/common/grpc/TenantContextServerInterceptor.java`
- `im-server/im-common/src/main/java/com/im/common/redis/RedisKeys.java`
- `im-server/im-message-service/pom.xml`
- `im-server/im-message-service/src/main/java/com/im/message/config/MessageRpcClientConfig.java`
- `im-server/im-message-service/src/main/java/com/im/message/handler/MsgSendHandler.java`
- `im-server/im-message-service/src/main/java/com/im/message/dao/mapper/ConversationProgressMapper.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/ConversationResolver.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/GrpcConversationResolver.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessageSendService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessageIdempotencyService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/SequenceService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessageAssembler.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessagePersistService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessageSendResult.java`
- `im-server/im-message-service/src/test/java/com/im/message/**`

验收标准：

- 支持 `MsgSend` 的 `to_user_id` 和 `conv_id` 两种 C2C 路径。
- 只处理 `TextContent`；其他内容类型返回明确错误。
- `client_msg_id` 幂等：重复请求返回原 `server_msg_id/conv_id/seq`。
- seq 使用 DB 事务内 `conversation.max_seq = max_seq + 1` 行锁自增（D26）。
- 同一事务写入 `message`、更新 `conversation.max_seq/last_msg_*`、写入 `outbox`。
- 并发同会话发送后 seq 连续无空洞。
- 本任务不实现 RabbitMQ 投递，只写 outbox。

测试方式：

- `mvn -q -pl im-message-service -am test`
- 集成测试覆盖首次单聊、已有会话、重复 client_msg_id、并发 seq。

完成记录：

- 已新增 `MsgSendHandler`，注册 `Cmd.MSG_SEND`，成功和业务错误均返回 `MSG_SEND_ACK`，protobuf 解码失败交给 Uplink ERROR 兜底。
- 已新增 `MessageSendService`，链路顺序为参数校验 → DB 幂等查询 → Redis SETNX 去重锁 → 关系校验 → ConversationRpc 解析会话 → DB 事务内分配 seq → 同事务写 DB。
- 已新增 `GrpcConversationResolver` 和 `MessageRpcClientConfig`，message 模块通过 proto/gRPC 调 conversation，不引入业务模块编译依赖。
- 已新增 `GrpcMetadataKeys` 和 `TenantContextServerInterceptor`，内部 gRPC 调用通过 metadata 透传 tenant/trace，服务端自动绑定 `TenantContext`。
- 已新增 `SequenceService`，通过 `conversation.max_seq = max_seq + 1` 在 DB 事务内分配会话级 seq。
- 已新增 `MessageIdempotencyService`，使用 Redis key `dedup:{tenant}:{client_msg_id}` 做并发重复请求保护，并用 `message.uk_client_msg` 做 DB 双保险。
- 已新增 `MessagePersistService`，在一个事务中写 `message`、条件更新 `conversation.max_seq/last_msg_*`、写 `outbox`。
- 已新增 `MessageAssembler`，负责 message/entity、`MsgPush`、`MsgSavedEvent` outbox payload、ACK 结果组装。
- 已新增 Docker 环境下的并发集成测试：20 并发同会话发送，验证 seq 为 1..20、message/outbox 写入、conversation.max_seq 更新；当前环境无 `/var/run/docker.sock`，该集成测试自动跳过。
- PR1 复审修复已接入黑名单关系校验；好友开关仍保留为二阶段租户配置。
- 已执行 `mvn -q -pl im-message-service -am test`，通过；message 模块单元测试 17 个通过，Testcontainers 测试跳过 3 个。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`，`localhost:9091` 端口监听成功。

---

### T11 — 消息同步与历史分页

状态：DONE

目标：

- 实现客户端缺口拉取和历史分页能力。
- 支持 `SYNC_REQ` handler 和 REST 历史查询。

涉及模块：

- `im-server/im-message-service`
- `im-server/im-conversation-service`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-common/src/main/java/com/im/common/auth/AuthTokenClaims.java`
- `im-server/im-common/src/main/java/com/im/common/auth/BearerTokenExtractor.java`
- `im-server/im-common/src/main/java/com/im/common/auth/JwtAccessTokenVerifier.java`
- `im-server/im-message-service/src/main/java/com/im/message/handler/SyncReqHandler.java`
- `im-server/im-message-service/src/main/java/com/im/message/grpcapi/MessageGrpcService.java`
- `im-server/im-message-service/src/main/java/com/im/message/rest/MessageHistoryController.java`
- `im-server/im-message-service/src/main/java/com/im/message/dto/MessageHistoryResponse.java`
- `im-server/im-message-service/src/main/java/com/im/message/dto/MessageItemResponse.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/ConversationMemberClient.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/GrpcConversationMemberClient.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessagePage.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessageQueryService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessageAssembler.java`
- `im-server/im-message-service/src/test/java/com/im/message/**`
- `im-server/im-common/src/test/java/com/im/common/auth/**`

验收标准：

- `SYNC_REQ` 根据 `conv_id + local_max_seq` 返回缺口消息。
- `SyncResp.ConvDelta.msgs` 复用 `MsgPush`。
- REST `GET /api/v1/convs/{id}/messages?end_seq=&limit=` 可分页拉历史。
- 只允许会话成员读取消息。
- 分页 `has_more` 语义正确。

测试方式：

- `mvn -q -pl im-message-service,im-bootstrap -am test`
- 集成测试：用户 A/B 注册、A 发消息、B sync 拉取、历史分页拉取。

完成记录：

- 已新增 `SyncReqHandler`，注册 `Cmd.SYNC_REQ`，返回 `SYNC_RESP` body。
- 已新增 `MessageQueryService`，统一实现 sync 缺口拉取、gRPC PullMsgs 区间拉取、REST 历史分页。
- 已新增 `MessageGrpcService`，实现 `MessageRpc.PullMsgs`；`RevokeMsg` 仍返回未支持错误码，等待撤回任务。
- 已新增 `MessageHistoryController`，实现 `GET /api/v1/convs/{id}/messages?end_seq=&limit=`。
- REST 历史接口按 `docs/protocol.md` 使用 `Authorization: Bearer` + `X-Tenant-Id`；message 模块通过 common `JwtAccessTokenVerifier` 校验 access token 签名、issuer、typ、exp 和 tenant，并提取 user_id。
- 已新增 `ConversationMemberClient`，通过 `ConversationRpc.GetMembers` 校验会话成员；非成员读取返回 `NOT_CONV_MEMBER`。
- `SyncResp.ConvDelta.msgs` 和 `MessageRpc.PullMsgsResp.msgs` 均复用 `MsgPush`。
- REST 分页按 `end_seq` 倒序取 `limit + 1` 条判断 `has_more`；SYNC 按 `local_max_seq + 1` 到 `server_max_seq` 正序返回缺口。
- 已新增单测覆盖 JWT access 校验、SYNC handler、MessageRpc.PullMsgs、REST history、成员校验、has_more 语义。
- 已执行 `mvn -q -pl im-message-service,im-bootstrap -am test`，通过；message 模块单元测试 25 个通过，Testcontainers 测试跳过 3 个；common 新增 JWT 测试 4 个通过。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`，`localhost:9091` 端口监听成功。

---

### T12 — Outbox 投递 RabbitMQ 与 msg.saved 事件

状态：DONE

目标：

- 实现 Outbox Poller，把 T10 写入的 outbox 投递到 RabbitMQ。
- 事件使用 `events.proto` 的 `MsgSavedEvent`。
- 投递成功后删除 outbox，失败重试并记录日志。

涉及模块：

- `im-server/im-common`
- `im-server/im-message-service`
- `im-server/im-bootstrap`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-common/src/main/java/com/im/common/outbox/OutboxWriter.java`
- `im-server/im-common/src/main/java/com/im/common/outbox/OutboxPoller.java`
- `im-server/im-common/src/main/java/com/im/common/mq/RabbitMqConfig.java`
- `im-server/im-common/src/main/java/com/im/common/mq/RabbitMqPublisher.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MsgSavedEventFactory.java`
- `im-server/im-common/src/test/java/com/im/common/outbox/**`
- `im-server/im-common/src/test/java/com/im/common/mq/**`

验收标准：

- 启动时声明 `im.events` topic exchange。
- Outbox 批量扫描待投事件。
- RabbitMQ confirm 成功后删除 outbox。
- 失败时增加重试次数，不丢数据。
- 消费侧幂等模板可以后续复用，但本任务不实现 push 消费。

测试方式：

- `mvn -q -pl im-common,im-message-service -am test`
- Testcontainers RabbitMQ 集成测试验证投递和删除。

完成记录：

- 已新增 `OutboxWriter`、`OutboxPoller`、公共 outbox entity/mapper；PR1 复审修复后 outbox 作为基础设施表由 poller 全租户扫描，写入侧必须显式写入 `tenant_id`。
- 已新增 `RabbitMqConfig`、`RabbitMqPublisher`，启动声明 `im.events` topic exchange，发布时携带 `tenant_id`、`trace_id`、`event_id`、`event_type` headers，并等待 publisher confirm。
- 已新增 `MsgSavedEventFactory`，message 持久化链路改为同事务调用公共 `OutboxWriter` 写入 `msg.saved.{tenant_id}` outbox。
- 默认 profile 下 `im.outbox.enabled=false`，避免本地未启动 RabbitMQ 时影响开发；docker profile 下默认启用 outbox poller。
- 已补充单测覆盖 outbox 写入校验、poller 成功删除、失败 retry、RabbitMQ headers 和 nack；RabbitMQ Testcontainers 集成测试在无 `/var/run/docker.sock` 环境自动跳过。
- 已执行 `mvn -q -pl im-common,im-message-service -am test`，通过。
- 已执行完整 `mvn -q verify`，通过。
- 已用 `java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar` 启动，`/actuator/health` 返回 `{"status":"UP","groups":["liveness","readiness"]}`。

---

### T13 — im-server MVP 端到端集成测试

状态：DONE

目标：

- 用自动化测试串起 im-server MVP 核心闭环。
- 证明无需 Rust 网关也能通过 gRPC Uplink 和 REST 验证业务正确性。

涉及模块：

- `im-server/im-bootstrap`
- `im-server/im-user-service`
- `im-server/im-conversation-service`
- `im-server/im-message-service`
- `im-server/im-common`

需要修改的文件：

- `im-server/im-bootstrap/src/test/java/com/im/bootstrap/e2e/ImServerMvpE2eTest.java`
- `im-server/im-bootstrap/src/test/resources/application-test.yml`
- 必要时新增测试工具类。

验收标准：

- 测试启动完整 Spring Boot context。
- 自动完成：注册用户 A/B → 登录 → VerifyToken → A 发送 C2C 文本消息 → B 执行 SYNC_REQ → B 查询历史消息。
- 验证消息 seq、server_msg_id、conversation、message、outbox 基本状态。
- 所有测试可一条命令运行。

测试方式：

- `mvn -q verify`
- 如本地未启动 Docker，说明 Testcontainers 依赖 Docker。

完成记录：

- 已新增 `ImServerMvpE2eTest`，使用完整 Spring Boot context + MockMvc + gRPC Netty channel。
- E2E 流程覆盖：注册 A/B、登录、`GatewayAuth.VerifyToken`、`Uplink.Dispatch(MSG_SEND)`、`Uplink.Dispatch(SYNC_REQ)`、REST 历史消息查询。
- E2E 断言覆盖：`seq`、`server_msg_id`、`conv_id`、conversation/member/message/outbox 基本状态，以及 outbox 中 `MsgSavedEvent` payload。
- 已新增 `application-test.yml`，测试 profile 关闭 outbox poller 和 RabbitMQ/DB/Redis health，避免测试环境连接无关服务。
- 已为 `im-bootstrap` 增加 Testcontainers 测试依赖和 `im-common` test-jar 依赖，以复用 MySQL schema 初始化工具。
- 已执行 `mvn -q -pl im-bootstrap -am test`，通过；当前环境无 `/var/run/docker.sock`，E2E 用例自动跳过。
- 已执行完整 `mvn -q verify`，通过；当前环境无 `/var/run/docker.sock`，所有 Testcontainers 集成/E2E 用例自动跳过。

---

### T14 — 修复消息 seq 分配一致性

状态：DONE

目标：

- 把消息会话级 seq 从 Redis 预分配改为 DB 事务内分配。
- 避免 DB 写失败后 Redis seq 已消耗造成空洞。
- 避免 Redis 丢失或重启后与 DB `conversation.max_seq` 不一致。

涉及模块：

- `im-server/im-message-service`
- `im-server/im-common`

需要修改的文件：

- `im-server/im-message-service/src/main/java/com/im/message/service/MessageSendService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessagePersistService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/SequenceService.java`
- `im-server/im-message-service/src/main/java/com/im/message/dao/mapper/ConversationProgressMapper.java`
- `im-server/im-message-service/src/test/java/com/im/message/service/**`
- 必要时更新集成测试。

验收标准：

- seq 分配和 message/conversation/outbox 写入处于同一 DB 事务。
- 事务回滚不会消耗 DB seq。
- 幂等重复请求仍返回原结果。
- 并发同会话发送后 `max(seq)=count(*)`，无空洞。
- 不新增手写 `tenant_id` 条件，仍依赖 MyBatis 租户拦截器。

测试方式：

- 在 `im-server/` 目录执行：`mvn -q -pl im-message-service -am test`
- 在 `im-server/` 目录执行：`mvn -q verify`

完成记录：

- 已把会话级 seq 分配从 Redis 预分配改为 DB 事务内分配：`SequenceService` 通过 `conversation.max_seq = max_seq + 1` 分配 seq，并读取当前 `max_seq` 返回。
- 已调整发送链路：`MessageSendService` 不再提前消耗 seq；`MessagePersistService` 在同一事务内完成 seq 分配、`message` 插入、`conversation.last_msg_*` 更新、`outbox` 写入。
- 已检查 `conversation` 进度更新返回值，更新失败会抛出 `INTERNAL_ERROR` 并触发事务回滚。
- 已更新单元测试和集成测试装配，覆盖 DB seq 分配、异常路径、持久化链路和发送链路 mock 断言。
- 已在 `im-server/` 目录执行 `mvn -q -pl im-message-service -am test`，通过；当前环境无 `/var/run/docker.sock`，Testcontainers 集成测试自动跳过。
- 已在 `im-server/` 目录执行 `mvn -q verify`，通过；当前环境无 `/var/run/docker.sock`，所有 Testcontainers 集成/E2E 用例自动跳过。

---

### T15 — 修复 Outbox/MQ 投递语义与消费幂等基础

状态：PENDING（PR1 已完成 mandatory return 与 DEAD 状态；多实例 claim/lock 和消费幂等模板仍待后续任务）

目标：

- 避免 RabbitMQ exchange ack 但无 queue 绑定时误删 outbox。
- 避免多实例 outbox poller 重复抢同一事件。
- 增加消费侧幂等模板和失败补偿状态。

涉及模块：

- `im-server/im-common`
- `im-server/im-bootstrap`

需要修改的文件：

- `im-server/im-common/src/main/java/com/im/common/mq/**`
- `im-server/im-common/src/main/java/com/im/common/outbox/**`
- `im-server/im-common/src/test/java/com/im/common/mq/**`
- `im-server/im-common/src/test/java/com/im/common/outbox/**`
- 必要时新增 Flyway migration。

验收标准：

- RabbitMQ publish 使用 mandatory return 或声明必要 queue/binding 后才删除 outbox。
- poller 有 claim/lock 机制，支持多实例不重复抢同一行。
- 超过最大重试后进入明确 DEAD/FAILED 状态并可告警/人工重投。
- 消费侧提供按 `event_id` 或业务键幂等的复用模板。

测试方式：

- `mvn -q -pl im-common -am test`
- 有 Docker 时执行 RabbitMQ Testcontainers 集成测试。

完成记录：

- PR1 复审修复已把 `outbox` 加入租户拦截忽略表，`OutboxPoller` 改为全租户扫描并使用事件行内 `tenant_id` 投 MQ。
- `RabbitTemplate` 已启用 mandatory publish；exchange ack 但消息被 broker return 时不会删除 outbox。
- 超过最大重试次数后 outbox 状态进入 `DEAD(2)`，避免无限重试掩盖问题。
- 多实例 claim/lock 与消费侧幂等模板仍保留在本任务后续范围。

---

### T16 — 修复 SYNC_REQ 空列表全量同步与分页语义

状态：DONE

目标：

- 实现新设备 `conv_versions` 为空时的全量会话增量同步。
- 明确同步分页和 `has_more/full_sync` 行为。

涉及模块：

- `im-server/im-message-service`
- `im-server/im-conversation-service`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-message-service/src/main/java/com/im/message/service/MessageQueryService.java`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/grpcapi/ConversationGrpcService.java`
- `im-server/im-message-service/src/test/java/com/im/message/service/**`
- 必要时调整 proto 或新增内部 RPC。

验收标准：

- 空 `conv_versions` 能返回当前用户有消息的会话增量。
- 指定会话缺口仍按 `[local_max_seq + 1, server_max]` 返回。
- `has_more` 与分页数量一致。
- 历史接口返回顺序在接口文档中明确并有测试覆盖。

测试方式：

- `mvn -q -pl im-message-service,im-conversation-service -am test`

完成记录：

- 已新增 `ConversationRpc.ListMemberConvs`，conversation 模块可按当前用户列出会话同步源。
- `MessageQueryService.sync` 在 `conv_versions` 为空时会拉取成员会话并按 `[local_max_seq + 1, server_max]` 生成增量。
- 已补充 message/conversation 单元测试覆盖空 `conv_versions`、指定会话缺口、gRPC 会话列表响应。
- 已执行 `mvn -q -pl im-message-service -am test`、`mvn -q -pl im-user-service,im-conversation-service -am test` 和完整 `mvn -q verify`，通过；当前环境无 `/var/run/docker.sock`，Testcontainers 集成/E2E 用例自动跳过。

---

### T17 — 撤回最小闭环

状态：DONE

目标：

- 实现消息撤回的 DB 状态、outbox 事件和同步/历史一致性。

涉及模块：

- `im-server/im-message-service`
- `im-server/im-push-service`
- `im-server/im-common`
- `im-proto`
- `im-server/im-proto-java`

需要修改的文件：

- `im-server/im-message-service/src/main/java/com/im/message/grpcapi/MessageGrpcService.java`
- `im-server/im-message-service/src/main/java/com/im/message/service/**`
- `im-server/im-message-service/src/main/java/com/im/message/rest/MessageHistoryController.java`
- `im-server/im-push-service/src/main/java/com/im/push/**`
- `im-proto/proto/events/events.proto`
- `im-server/im-message-service/src/test/java/com/im/message/**`
- `im-server/im-push-service/src/test/java/com/im/push/**`

验收标准：

- `RevokeMsg` 校验成员/操作者权限后更新 `message.status/revoke_reason`。
- 同事务写 `msg.revoked.{tenant_id}` outbox。
- SYNC/历史能返回撤回状态，客户端可一致更新。
- 重复撤回幂等。

测试方式：

- `mvn -q -pl im-message-service,im-push-service -am test`

完成记录：

- 已实现 `MessageRevokeService`：按 `(tenant_id, conv_id, seq)` 定位消息，`BY_SENDER` 校验发送者本人、成员身份和 2 分钟撤回窗口，`BY_ADMIN/BY_MODERATION` 作为内部可信原因直接撤回。
- 撤回在同一事务内更新 `message.status=REVOKED`、`message.revoke_reason`，并写入 `msg.revoked.{tenant_id}` outbox；重复撤回幂等返回，不重复写 outbox。
- 撤回最新消息时会条件更新 `conversation.last_msg_abstract=message revoked`，避免会话列表继续展示原文摘要。
- `MsgRevokedEvent` 兼容新增 `server_msg_id` 字段，push 模块新增 `msg.revoked.*` 队列绑定和消费者，下发 `REVOKE_NOTIFY`。
- SYNC、RPC pull 和 REST history 复用 `MessageAssembler` 返回 revoked 状态；已撤回消息不再返回原始 content，状态通过 `MsgPush.ext.status/revoke_reason` 和 REST DTO 字段暴露。
- 新增 REST `POST /api/v1/convs/{convId}/messages/{seq}/revoke`，用户侧按 `BY_SENDER` 撤回。
- 已执行 `mvn -q -pl im-message-service,im-push-service -am test`，通过。
- 已执行 `mvn -q -pl im-bootstrap -am test`，通过；本机无 Docker socket，Testcontainers 用例按既有配置跳过。

---

### T18 — 多端同步与推送补偿基础

状态：DONE

目标：

- 实现 push 模块 MVP：在线路由表、PushEnvelope 批量投递、msg.saved 消费扇出。
- 实现同平台类互踢与 `token_ver` 失效闭环。
- 保证 push 失败不影响消息落库与客户端 SYNC 补齐路径。

涉及模块：

- `im-server/im-push-service`
- `im-server/im-common`
- `im-server/im-user-service`
- `im-server/im-bootstrap`

需要修改的文件：

- `im-server/im-common/src/main/java/com/im/common/device/PlatformClass.java`
- `im-server/im-common/src/main/java/com/im/common/auth/TokenVersionService.java`
- `im-server/im-common/src/main/java/com/im/common/redis/RedisKeys.java`
- `im-server/im-user-service/src/main/java/com/im/user/service/JwtService.java`
- `im-server/im-user-service/src/main/java/com/im/user/service/AuthService.java`
- `im-server/im-user-service/src/main/java/com/im/user/service/TokenVerifier.java`
- `im-server/im-push-service/src/main/java/com/im/push/**`
- `im-server/im-bootstrap/src/test/java/com/im/bootstrap/e2e/ImServerMvpE2eTest.java`
- `docs/architecture.md`
- `docs/im-server-design.md`
- `docs/review-checklist.md`

验收标准：

- msg.saved 消费后按用户在线路由投递，离线不重复推送。
- `PushEnvelope` 按 `gw_instance` 分组批量投递，禁止逐用户逐条投。
- REST 登录/注册按平台类递增 `token_ver` 并写入 JWT，`GatewayAuth.VerifyToken` 校验 Redis 当前版本。
- `ConnEvent.OnConnected` 同平台类新连接会向旧连接投 `KICK`，再覆盖路由。
- `ConnEvent.OnDisconnected` 只删除当前连接对应路由，不能误删新连接。
- 路由表 TTL 默认 90s，匹配 30s 心跳两次容错。
- 多端同步不依赖 push 成功，重连可通过 SYNC 补齐。

测试方式：

- `mvn -q -pl im-user-service,im-push-service -am test`
- `mvn -q verify`

完成记录：

- 已新增 `PlatformClass` 和 `TokenVersionService`；JWT access/refresh token 增加 `platform_class/token_ver` claim。
- `AuthService` 登录/注册会按平台类递增 `token_ver` 后发 token；refresh/current user 会校验 token 版本仍有效。
- `GatewayAuth.VerifyToken` 会校验请求平台类与 token 平台类一致，并校验 Redis 当前 `token_ver`。
- push 模块新增 Redis 路由仓储、`PushDispatchService`、`PushRpcGrpcService`、`ConnEventGrpcService`、`MsgSavedEventConsumer`。
- `msg.saved` 消费后用 `ConversationRpc.GetMembers` 查成员，按在线路由批量投 `PushEnvelope` 到 `push.gw.{instance}`；重复 MQ 事件用 `push:event:{tenant}:{event_id}` 去重。
- 已补测试覆盖平台类映射、token_ver 过期、PushEnvelope 分组、同类互踢、重复 msg.saved 跳过。

---

### T19 — PR1 复审阻塞项修复

状态：DONE

目标：

- 按 `docs/reviews/2026-06-13-pr1-mvp-foundation.md` 修复 S1~S7。
- 采纳 Jade 对 S3/S7 的裁决：TenantContext 改普通 ThreadLocal；seq 采纳 DB 行锁自增并修订文档。
- 顺手完成 B1/B2，提升线上排障和 SQL 防护基线。

涉及模块：

- `im-server/im-common`
- `im-server/im-bootstrap`
- `im-server/im-user-service`
- `im-server/im-conversation-service`
- `im-server/im-message-service`
- `im-proto`
- `docs`

需要修改的文件：

- `im-server/pom.xml`
- `im-server/im-bootstrap/pom.xml`
- `im-server/im-common/src/main/java/com/im/common/tenant/TenantContext.java`
- `im-server/im-common/src/main/java/com/im/common/mybatis/**`
- `im-server/im-common/src/main/java/com/im/common/outbox/**`
- `im-server/im-common/src/main/java/com/im/common/mq/RabbitMqPublisher.java`
- `im-server/im-common/src/main/java/com/im/common/id/WorkerIdLease.java`
- `im-server/im-user-service/src/main/java/com/im/user/**`
- `im-server/im-message-service/src/main/java/com/im/message/**`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/**`
- `im-proto/proto/rpc/internal.proto`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/architecture.md`
- `docs/im-server-design.md`
- `docs/review-checklist.md`
- `TASKS.md`

验收标准：

- Maven enforcer 在父 POM 生效，bootstrap 明确 skip 业务模块依赖规则。
- Outbox poller 不再固定 tenant 1，支持全租户扫描；RabbitMQ mandatory return 不误删 outbox；超过最大重试进入 DEAD。
- 生产启动不依赖 `--enable-preview`。
- `MessageSendService` 在发送链路调用 `UserRpc.CheckRelation`，被拉黑返回 `BLOCKED_BY_PEER`。
- `SYNC_REQ` 空 `conv_versions` 能返回当前用户会话增量。
- Snowflake workerId 启动时通过 Redis 租约 fail-fast 防冲突。
- 文档正式采纳 DB 行锁 seq，Redis seq 只作为二阶段高吞吐预留方案。

测试方式：

- `mvn -q -pl im-common -am test`
- `mvn -q -pl im-message-service -am test`
- `mvn -q -pl im-user-service,im-conversation-service -am test`
- `mvn -q verify`

完成记录：

- 已完成 S1~S7 和 B1/B2。
- 已补充 `UserRpc.CheckRelation`、`ConversationRpc.ListMemberConvs`、workerId Redis 租约、outbox 全租户扫描与 mandatory return 单元测试。
- 已修订 AGENTS/CLAUDE/architecture/design/checklist/TASKS，补齐 S7 的流程记录。
- 已执行上述测试命令，均通过；当前环境无 `/var/run/docker.sock`，Testcontainers 集成/E2E 用例自动跳过。

---

### T20 — Rust 网关基础链路

状态：DONE

目标：

- 建立 `im-gateway-rust` 可维护工程骨架。
- 实现 WS 连接鉴权、PING/PONG、上行业务帧透传 `Uplink.Dispatch`。
- 实现消费 `push.gw.{instance}` 的 `PushEnvelope`，按本地连接投递下行帧。
- 对 `need_ack=true` 的下行帧使用网关分配的 `req_id` 做送达跟踪，超时主动断连，依赖客户端重连 `SYNC_REQ` 补齐。

涉及模块：

- `im-gateway-rust`
- `im-proto`
- `docs`

需要修改的文件：

- `im-gateway-rust/Cargo.toml`
- `im-gateway-rust/build.rs`
- `im-gateway-rust/src/**`
- `im-gateway-rust/README.md`
- `TASKS.md`
- `AGENTS.md`
- `CLAUDE.md`
- `docs/protocol.md`
- `docs/architecture.md`

验收标准：

- 网关只编译 `ws/frame.proto` 与 `rpc/gateway.proto`，不编译业务 body proto。
- WS 建连后 5s 内必须收到 `AUTH`，鉴权成功后调用 `ConnEvent.OnConnected`，断连调用 `ConnEvent.OnDisconnected`。
- `PING` 返回 `PONG`；业务上行帧原样调用 `Uplink.Dispatch` 并回写同 `req_id` 响应。
- RabbitMQ 队列 `push.gw.{instance}` 消费 `PushEnvelope`，只投递本实例本地连接；`KICK` 投递后主动断连。
- `need_ack=true` 时网关为下行帧分配非 0 `req_id`，收到同连接 `MSG_RECV_ACK` 且 `req_id` 一致才清除 pending ack。
- ack 超时不重推，主动关闭连接并通知 `ConnEvent.OnDisconnected`，后续由客户端重连同步补齐。

测试方式：

- `cargo fmt --check`
- `cargo test`
- `cargo clippy --all-targets -- -D warnings`

完成记录：

- 已新增 Rust 工程 `im-gateway-rust`，通过 `build.rs` 只生成 `ws/frame.proto` 与 `rpc/gateway.proto`。
- 已实现 `/ws`：5s 内 AUTH、gRPC `GatewayAuth.VerifyToken`、`ConnEvent.OnConnected/OnDisconnected`、PING/PONG、业务帧 `Uplink.Dispatch` 透传。
- 已实现 RabbitMQ `push.gw.{instance}` 消费，按本地 ConnMap 投递 `PushEnvelope`，`KICK` 下发后关闭连接。
- 已实现 D28：`need_ack=true` 下行帧由网关分配 `req_id`，`MSG_RECV_ACK` 回同 `req_id` 才清 pending；超时关闭连接并通知断连，不做重推。
- 已在 PING 时异步调用幂等 `ConnEvent.OnConnected` 刷新 Java push 模块里的 Redis 路由 TTL。
- 已执行 `cargo fmt --check`、`cargo test`、`cargo clippy --all-targets -- -D warnings`，均通过。

---

### T21 — PR2 审查阻塞项修复

状态：DONE

目标：

- 按 `docs/reviews/2026-06-13-pr2-gateway-push.md` 修复 Rust 网关 R1~R5。
- 顺手闭环 B1/B8 与 PR-A P2/P3，保持改动集中在网关生产成熟度与文档说明。

涉及模块：

- `im-gateway-rust`
- `im-server/im-common`
- `deploy`
- `docs`

需要修改的文件：

- `im-gateway-rust/src/config.rs`
- `im-gateway-rust/src/connection.rs`
- `im-gateway-rust/src/frame_codec.rs`
- `im-gateway-rust/src/main.rs`
- `im-gateway-rust/src/metrics.rs`
- `im-gateway-rust/src/push.rs`
- `im-gateway-rust/src/rpc.rs`
- `im-gateway-rust/src/state.rs`
- `im-gateway-rust/README.md`
- `im-server/im-common/src/main/java/com/im/common/auth/TokenVersionService.java`
- `im-server/im-common/src/main/java/com/im/common/device/PlatformClass.java`
- `deploy/README.md`
- `docs/reviews/2026-06-13-pr2-gateway-push.md`

验收标准：

- R1：服务端按 `heartbeat_interval * 3` 执行 idle timeout，静默连接会清理 ConnMap 并上报断连。
- R2：Verify/Dispatch/ConnEvent 都有 gRPC deadline；Dispatch 失败返回 `ERROR` 帧并保持连接。
- R3：RabbitMQ push consumer 断开后指数退避重连，MQ 闪断不会让网关永久失去下行能力。
- R4：单连接 outbound 队列有界，队列满按慢消费者断连并上报。
- R5：提供 `/metrics`，输出在线连接数、上行帧计数、推送送达/失败、ack 超时断连。
- B1：路由 TTL 续期降频为每 3 次 PING 一次。
- B8：连接层硬编码错误码有注释标注来源。
- PR-A P2/P3：部署文档说明 Redis token_ver 丢失影响；common 类注释说明基础设施边界。

测试方式：

- `cargo fmt --check`
- `cargo test`
- `cargo clippy --all-targets -- -D warnings`
- `mvn -q -pl im-common -am test`

完成记录：

- Rust 网关新增 `Metrics` 模块和 `/metrics` endpoint。
- `RpcClients` 统一增加 verify/dispatch/conn_event timeout。
- `read_loop` 使用 heartbeat 三倍窗口做 idle timeout；dispatch 异常返回 `Cmd.ERROR`，不主动断开。
- RabbitMQ push consumer 改成永久循环，失败后 1s..30s 指数退避重连。
- outbound 改 bounded queue，满队列判慢消费者并清本地连接、上报 `OnDisconnected`。
- 心跳路由续期由每次 PING 降为每 3 次 PING 一次。
- 已补 Redis token_ver 运维说明和 im-common 边界注释。

---

### T22 — push 路由批量查询优化与 enforcer 挂账核验

状态：DONE

目标：

- 先解决群聊/已读回执 PR 前置的 P1：push 模块路由查询必须支持批量用户查询。
- 去掉按用户循环 `KEYS route:{tenant}:{user}:*` 的实现，避免群聊 500 人扇出时放大 Redis RTT 和阻塞扫描风险。
- 核验 PR-1 L1：`im-common` 必须绑定 Maven enforcer，不能让业务模块互依赖规则漏检。

涉及模块：

- `im-server/im-push-service`
- `im-server/im-common`
- `docs`

需要修改的文件：

- `im-server/im-push-service/src/main/java/com/im/push/route/OnlineRouteRepository.java`
- `im-server/im-push-service/src/main/java/com/im/push/route/RedisOnlineRouteRepository.java`
- `im-server/im-push-service/src/main/java/com/im/push/service/PushDispatchService.java`
- `im-server/im-push-service/src/test/java/com/im/push/service/PushDispatchServiceTest.java`
- `im-server/im-push-service/src/test/java/com/im/push/route/RedisOnlineRouteRepositoryTest.java`
- `TASKS.md`
- `docs/reviews/2026-06-13-pr3-gateway-rereview.md`

验收标准：

- `PushDispatchService.pushToUsers` 对目标用户去重后只调用一次批量路由查询。
- Redis 路由查询按 `mobile/desktop/web` 三类平台 key 构造固定 key 列表并一次 `multiGet`，不再使用 `KEYS` 通配扫描。
- 在线连接仍按 `gw_instance` 分组生成 `PushEnvelope`，离线用户计数按去重后的目标用户计算。
- 重复 userId、null userId、非法 userId 不会造成重复推送或 Redis 无效查询。
- `im-common` 的 enforcer 插件绑定已通过 `validate` 阶段核验。

测试方式：

- `mvn -q -pl im-push-service -am test`
- `mvn -q -pl im-common validate`

完成记录：

- 已新增 `OnlineRouteRepository.findAllByUsers`，`PushDispatchService.pushToUsers` 对目标用户去重后一次批量查询路由。
- `RedisOnlineRouteRepository` 不再使用 `KEYS route:{tenant}:{user}:*`，改为按 `mobile/desktop/web` 三类固定 key 构造后一次 `multiGet`。
- 已补单测覆盖网关分组不变、重复/非法目标用户去重、Redis 批量查询不调用 `keys(pattern)`。
- PR-1 L1 挂账已核验：`im-common` 已绑定父 POM enforcer，`mvn -q -pl im-common validate` 通过。
- 已执行 `mvn -q -pl im-push-service -am test`，通过；当前环境无 `/var/run/docker.sock`，Testcontainers 探测日志仍会出现但未导致测试失败。
- 已补跑 PR-3 复审备注要求的 `cargo fmt --check`、`cargo test`、`cargo clippy --all-targets -- -D warnings`，均通过。

---

### T23 — PR4 挂账收口：网关 backoff 与慢消费者

状态：DONE

目标：

- 明确闭环 PR-1 L1：`im-common` 子模块可单独触发父 POM enforcer，防止业务模块互依赖漏检。
- 修复 PR-3 N2：RabbitMQ push consumer 稳定运行一段时间后重置重连 backoff，避免长期运行后偶发闪断仍从高退避恢复。
- 修复 PR-3 N1：单连接 outbound 队列连续满 N 次后主动断连，促使客户端重连后走 SYNC 自愈。

涉及模块：

- `im-server/im-common`
- `im-gateway-rust`
- `docs`

需要修改的文件：

- `im-server/im-common/pom.xml`
- `im-gateway-rust/src/config.rs`
- `im-gateway-rust/src/connection.rs`
- `im-gateway-rust/src/push.rs`
- `im-gateway-rust/README.md`
- `TASKS.md`

验收标准：

- `im-common` POM 明确绑定 `maven-enforcer-plugin`，`mvn -q -pl im-common validate` 通过。
- push consumer 重连 backoff 仍按 1s→30s 指数退避，但 consumer 稳定运行 60s 后下一次重连延迟复位为 1s。
- `IM_GATEWAY_OUTBOUND_QUEUE_FULL_THRESHOLD` 可配置，默认 3；连续满队列达到阈值后关闭连接。
- KICK 控制帧发不出去时仍立即断连，避免同类互踢失效。
- 单测覆盖 backoff 复位和连续满队列断连阈值。

测试方式：

- `mvn -q -pl im-common validate`
- `cargo fmt --check`
- `cargo test`
- `cargo clippy --all-targets -- -D warnings`

完成记录：

- `im-common/pom.xml` 已保留并标注明确的 enforcer 插件绑定，`mvn -q -pl im-common validate` 通过。
- push consumer 重连退避增加稳定运行 60s 后复位逻辑，已补单测覆盖 1s→30s 增长和稳定后复位。
- outbound 队列增加连续满队列计数，默认阈值 3；达到阈值主动 close，已补单测覆盖。
- KICK 控制帧如果因队列满未发出，仍立即清本地路由并上报断连。
- 已执行 `cargo fmt --check`、`cargo test`、`cargo clippy --all-targets -- -D warnings`，均通过。

---

### T24 — 启动自检、Dockerfile 与网关握手防护

状态：DONE

目标：

- 补齐 `docs/im-server-design.md` §6 的启动期自检：MySQL schema、Redis、RabbitMQ、MinIO bucket、workerId 租约 fail-fast。
- 交付 compose app profile 可构建的 Dockerfile，解决 PR1 Q2 部署物挂账。
- 补齐 `docs/architecture.md` §13.8 的 WebSocket Origin 校验和 §13.3 的实例级握手限流。

涉及模块：

- `im-server/im-bootstrap`
- `im-gateway-rust`
- `deploy/docker-compose`
- `docs`

需要修改的文件：

- `im-server/Dockerfile`
- `im-server/pom.xml`
- `im-server/im-bootstrap/pom.xml`
- `im-server/im-bootstrap/src/main/java/com/im/bootstrap/selfcheck/**`
- `im-server/im-bootstrap/src/main/resources/application.yml`
- `im-server/im-bootstrap/src/main/resources/application-docker.yml`
- `im-gateway-rust/Dockerfile`
- `im-gateway-rust/src/config.rs`
- `im-gateway-rust/src/connection.rs`
- `im-gateway-rust/src/handshake_limiter.rs`
- `im-gateway-rust/src/main.rs`
- `im-gateway-rust/src/state.rs`
- `im-gateway-rust/README.md`
- `im-web/Dockerfile`
- `deploy/docker-compose/docker-compose.yml`
- `deploy/docker-compose/.env.example`
- `deploy/README.md`
- `TASKS.md`

验收标准：

- docker profile 下 `im-server` 默认开启启动自检；本地 profile 默认关闭，不影响无中间件空跑。
- 启动自检任一依赖失败会抛出 `ImException` 并终止应用。
- `docker compose --profile app config` 可解析 app 服务构建配置。
- `im-server`、`im-gateway-rust` Dockerfile 以仓库根为 build context，可读取 `im-proto`。
- 网关 `/ws` 握手先做实例级令牌桶限流，再做 Origin 白名单校验；无 Origin 的原生客户端连接不受影响。
- Origin 白名单、握手速率和突发容量均可通过环境变量配置。

测试方式：

- `mvn -q -pl im-bootstrap -am test`
- `cargo fmt --check`
- `cargo test`
- `cargo clippy --all-targets -- -D warnings`
- `docker compose --profile app config`

完成记录：

- 已新增 `StartupSelfCheckRunner`，docker profile 默认开启 MySQL 表、Redis PING、RabbitMQ exchange、MinIO bucket 和 workerId 租约自检；本地默认关闭。
- 已新增 `im-server/Dockerfile`、`im-gateway-rust/Dockerfile`、`im-web/Dockerfile` 和根 `.dockerignore`。
- compose app profile 已改为仓库根 build context，server/gateway 构建时可读取同级 `im-proto`；`im-server` 等待 `minio-init` 完成后启动。
- 网关 `/ws` 已在 upgrade 前加入实例级 token bucket 握手限流和 Origin 白名单校验；无 Origin 的原生客户端连接允许通过。
- 已执行 `mvn -q -pl im-bootstrap -am test`、`cargo fmt --check`、`cargo test`、`cargo clippy --all-targets -- -D warnings`，均通过。
- 当前环境没有 Docker CLI，无法执行 `docker compose --profile app config`；已用 Ruby YAML 解析兜底校验 app build context 和 dockerfile 字段。
- 端到端冒烟补测（2026-06-13）：已将冒烟流程固化为“两个客户端注册/登录 → A 发 B → B 从 `local_max_seq=0` 同步 seq=1 → B 回复同会话 seq=2 → 关闭 gRPC channel 模拟断线 → A 重新 `VerifyToken` → A 从 `local_max_seq=1` 增量同步且只收到 seq=2”。
- Docker/Testcontainers 版本命令：`mvn -q -pl im-bootstrap -am -Dtest=com.im.bootstrap.e2e.ImServerMvpE2eTest -Dsurefire.failIfNoSpecifiedTests=false test`；本机无 `docker` CLI 和 `/var/run/docker.sock`，该版本被 Testcontainers 跳过，Surefire 显示 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 1`。
- 外部中间件版本已实跑通过：本地 MySQL `127.0.0.1:3306`（临时库 `im_smoke_*`，测试后删除）、本地 Redis `127.0.0.1:6379`；RabbitMQ 配置指向 `103.45.65.84:5672`，但本冒烟保持 `im.outbox.enabled=false`，不验证 MQ 投递。
- 外部冒烟执行命令：`mvn -q -pl im-bootstrap -am -Dtest=com.im.bootstrap.e2e.ImServerMvpExternalSmokeTest -Dsurefire.failIfNoSpecifiedTests=false -Dim.e2e.external=true -Dim.e2e.mysql.username=root -Dim.e2e.mysql.password=****** -Dim.e2e.redis.host=127.0.0.1 -Dim.e2e.redis.port=6379 -Dim.e2e.rabbitmq.host=103.45.65.84 -Dim.e2e.rabbitmq.port=5672 test`。
- 外部冒烟结果：Surefire 显示 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，用例 `mvpFlowThroughRestGatewayAuthUplinkSyncAndHistory` 通过；补充执行 `mvn -q -pl im-bootstrap -am test` 也通过。

---

### T25 — 已读回执 MVP

状态：DONE

目标：

- 实现会话级 read_seq 上报，支持客户端同步已读位置。
- 保持 MVP 简化：只做会话级已读，不做逐消息已读成员列表和复杂多端回执。

涉及模块：

- `im-proto`
- `im-server/im-conversation-service`
- `im-server/im-message-service`
- `im-server/im-push-service`
- `docs`

需要修改的文件：

- `im-proto/proto/rpc/internal.proto`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/**`
- `im-server/im-message-service/src/main/java/com/im/message/**`
- `im-server/im-push-service/src/main/java/com/im/push/**`
- `TASKS.md`

验收标准：

- `READ_REPORT` 按 tenant/user/conv 校验成员身份后更新 `conversation_member.read_seq`。
- read_seq 不允许回退，不允许超过会话当前 max_seq。
- 推送只发轻量已读通知，不影响消息可靠链路。
- 同步/历史接口能返回当前用户会话 read_seq。

测试方式：

- `mvn -q -pl im-conversation-service,im-message-service,im-push-service -am test`

完成记录：

- 已实现 `READ_REPORT` CmdHandler：解析 `ReadReport` 后校验 tenant/user/conv 成员身份，并以 `READ_NOTIFY` 作为同步响应返回最终 `read_seq`。
- `conversation_member.read_seq` 更新使用单调条件 `read_seq < request.read_seq`；回退请求不更新、不推送；超过 `conversation.max_seq` 直接拒绝。
- 已新增内部 `ConversationRpc.GetMemberConv`，message 模块显式同步和 REST 历史均返回当前用户视角的 `read_seq`。
- 已读通知通过 `PushRpc.PushToUsers` 发送 `READ_NOTIFY`，`need_ack=false`，并新增 `exclude_user_id/exclude_conn_id` 排除当前连接，只推给对端和自己其他端。
- 已扩展端到端冒烟：两个客户端互发后，B 上报 read_seq=1，历史接口和 DB 均校验 B 的 read_seq。
- 已执行 `mvn -q -pl im-conversation-service,im-message-service,im-push-service -am test`，通过。
- 已执行外部冒烟 `ImServerMvpExternalSmokeTest`，本地 MySQL/Redis 环境下 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，通过。

---

### T26 — 群聊 MVP

状态：DONE

目标：

- 实现 500 人以内群聊的建群、成员变更、群消息发送与同步。
- 复用 conversation/topic 模型和 PushEnvelope 批量路由，不做大群特殊路径。

涉及模块：

- `im-proto`
- `im-server/im-group-service`
- `im-server/im-conversation-service`
- `im-server/im-message-service`
- `im-server/im-push-service`
- `docs`

需要修改的文件：

- `im-proto/proto/body/messages.proto`
- `im-server/im-group-service/src/main/java/com/im/group/**`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/**`
- `im-server/im-message-service/src/main/java/com/im/message/**`
- `TASKS.md`

验收标准：

- 建群默认创建 GROUP conversation，写入 operator 和初始成员。
- 群成员上限默认 500，超限拒绝。
- 群消息只允许成员发送；收件人按群成员扇出，排除发送者由产品决定并记录。
- 成员变更产生 `NotificationContent` 系统消息并拿会话 seq。

测试方式：

- `mvn -q -pl im-group-service,im-conversation-service,im-message-service -am test`

完成记录：

- 已新增 group-service REST 写入口：建群、加成员、踢成员、改名。
- 建群会同事务写入 `group_info`、`group_member`、`conversation`、`conversation_member`，并追加 `group.created` NotificationContent 系统消息。
- 成员变更和改名会追加 `group.member_added`、`group.member_removed`、`group.name_changed` 系统消息，走 `msg.saved` outbox，不新增旁路通知通道。
- 群成员上限从 `tenant_config.max_group_members` 读取，默认 500；超限返回 `GROUP_FULL`。
- message/conversation 已支持 `group_id` 和 GROUP `conv_id` 解析，非群成员不能发群消息；群消息同步和历史返回 GROUP 类型。
- 已补充 group-service 单测、conversation GROUP 解析单测、bootstrap E2E 群聊冒烟。
- 已执行 `mvn -q -pl im-group-service,im-conversation-service,im-message-service -am test`，通过。
- 已执行 `mvn -q -pl im-bootstrap -am test`，通过；本机无 Docker socket，Testcontainers 用例按既有配置跳过。
- 已执行外部冒烟 `ImServerMvpExternalSmokeTest`，本地 MySQL/Redis 环境下 `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，通过。

---

### T27 — conv_list_version 语义补全

状态：DONE

> **前提约束（2026-06-13 确认）**：项目 Flyway 已上线且当前处于 **V1** 版本，
> `V2__user_conv_event.sql` 可直接新增，无需处理 baseline 兼容问题；本任务**不考虑 schema 版本迁移兼容性**。

目标：

- 实现 `SyncReq.conv_list_version` 的真实会话列表同步语义。
- 为每个用户维护会话列表变更版本和事件流水，支持新会话、更新、删除等会话列表变更可通过 SYNC 对齐。

涉及模块：

- `im-proto`
- `im-server/im-conversation-service`
- `im-server/im-group-service`
- `im-server/im-message-service`
- `im-server/im-bootstrap`
- `docs`

需要修改的文件：

- `im-proto/proto/body/messages.proto`
- `im-proto/proto/rpc/internal.proto`
- `im-server/im-conversation-service/src/main/java/com/im/conversation/**`
- `im-server/im-group-service/src/main/java/com/im/group/**`
- `im-server/im-message-service/src/main/java/com/im/message/**`
- `im-server/im-bootstrap/src/main/resources/db/migration/V2__user_conv_event.sql`
- `docs/architecture.md`
- `TASKS.md`

验收标准：

- 新增 `user_conv_version` 和 `user_conv_event`，会话列表事件在业务事务内落库。
- C2C 新会话、GROUP 建群/加人/踢人会写入对应用户的会话列表事件。
- `ListMemberConvsResp.conv_list_version` 返回当前用户有效版本；`ListMemberConvsReq.conv_list_version` 可返回变更会话 diff。
- `SYNC_REQ.conv_list_version` 生效：服务端将会话列表 diff 合并进 `SyncResp.deltas`，删除会话以 `ConvInfo.deleted=true` 表达。
- 新设备 `conv_versions` 为空时仍返回当前用户全量会话，并带有有意义的初始 `conv_list_version`。

测试方式：

- `mvn -q -pl im-conversation-service,im-group-service,im-message-service -am test`
- `mvn -q -pl im-bootstrap -am test`

完成记录：

- 新增 `user_conv_version` / `user_conv_event` Flyway V2 迁移，按用户维护会话列表版本和事件流水。
- C2C 建会话、GROUP 建群/加人/踢人/改名会在业务事务内写入用户会话事件。
- `ListMemberConvsReq.conv_list_version` 已生效：`0` 返回全量会话；大于 `0` 返回事件 diff，删除会话用 `ConvInfo.deleted=true` 表达。
- `SYNC_REQ.conv_list_version` 已接入 message 查询链路，`SyncResp.conv_list_version` 返回当前有效版本，diff 过大时标记 `full_sync=true`。
- Bootstrap 冒烟覆盖双人互发、重连增量同步和群聊新增会话 diff。
- 已执行 `mvn -q -pl im-conversation-service,im-group-service,im-message-service -am test`，通过。
- 已执行 `mvn -q -pl im-bootstrap -am test`，通过。
- 已执行外部冒烟 `ImServerMvpExternalSmokeTest`，使用本地 MySQL/Redis 与远端 RabbitMQ，命令退出码 0，通过。

---

### T28 — 文件上传 MVP

状态：DONE

目标：

- 实现 MinIO 预签名直传和上传确认，支持后续图片/语音消息引用文件元数据。
- MVP 只做对象凭证和元数据落库，不走业务服务器中转文件流。

涉及模块：

- `im-server/im-file-service`
- `im-server/im-message-service`
- `deploy`
- `docs`

需要修改的文件：

- `im-server/im-file-service/src/main/java/com/im/file/**`
- `im-server/im-message-service/src/main/java/com/im/message/**`
- `im-server/im-bootstrap/src/main/resources/application.yml`
- `deploy/docker-compose/docker-compose.yml`
- `TASKS.md`

验收标准：

- `POST /api/v1/files/presign` 返回 5 分钟有效的 PUT 预签名 URL。
- 限制 content-type、大小、对象 key 前缀和租户隔离。
- 上传确认落库文件元数据，消息发送只能引用同租户已确认文件。
- 不实现转码、缩略图、病毒扫描和第三方离线推送。

测试方式：

- `mvn -q -pl im-file-service,im-message-service -am test`

完成记录：

- 新增 `im-file-service` 文件上传 REST 接口：`POST /api/v1/files/presign` 生成 5 分钟 PUT 预签名 URL，`POST /api/v1/files/confirm` 确认上传并更新 `file_meta`。
- 文件服务按租户生成对象 key：`{tenant}/{yyyyMM}/{uuid}.{ext}`，并限制 MIME、大小、tenant 前缀和上传确认状态。
- MinIO 访问通过 `ObjectStorageClient` 抽象隔离，生产实现为 `MinioObjectStorageClient`，单测使用 fake/mock，不依赖本地 MinIO。
- `im-message-service` 发送链路支持 `ImageContent` / `VoiceContent` / `FileContent`，发送前只允许引用同租户、已确认、大小/MIME 匹配的 `file_meta`。
- `MessageAssembler` 已补齐图片/语音/文件消息类型和会话列表摘要，并在 `MsgPush.ext.msg_type` 暴露类型供 REST history 回显。
- 未修改 `application.yml` / docker compose：当前 compose 已注入 `MINIO_ENDPOINT`、`MINIO_ROOT_USER`、`MINIO_ROOT_PASSWORD`、`MINIO_BUCKET`，文件服务配置会读取这些环境变量作为默认值。
- 已执行 `mvn -q -pl im-file-service,im-message-service -am test`，通过。
- 已执行 `mvn -q -pl im-bootstrap -am test`，通过。

---

### T29 — 内容安全审核最小版

状态：DONE

目标：

- 实现 D16 的最小闭环：文本消息先发后审，命中敏感词后复用撤回链路下发撤回通知，并写入审核日志留证。
- MVP 只做本地敏感词审核，不接第三方图片/语音审核、不做复杂处罚和申诉后台。

涉及模块：

- `im-server/im-message-service`
- `im-server/im-common`
- `im-server/im-bootstrap`
- `TASKS.md`

需要修改的文件：

- `im-server/im-message-service/src/main/java/com/im/message/moderation/**`
- `im-server/im-message-service/src/main/java/com/im/message/service/MessageRevokeService.java`
- `im-server/im-common/src/main/java/com/im/common/mybatis/TenantLineHandlerConfig.java`
- `im-server/im-common/src/main/java/com/im/common/redis/RedisKeys.java`
- `im-server/im-bootstrap/src/main/resources/db/migration/V4__moderation.sql`
- `im-server/im-message-service/src/test/java/com/im/message/moderation/**`
- `TASKS.md`

验收标准：

- `msg.saved.*` 事件会触发审核消费者；非文本消息跳过。
- 敏感词支持平台级 `tenant_id IS NULL` 和租户级词库，`word.reload` 事件可热刷新本地缓存。
- 命中 REVOKE 词后调用 `MessageRevokeService`，以 `BY_MODERATION` 原因撤回消息，并通过现有 outbox 产生 `msg.revoked`。
- `moderation_log` 写入 `provider/category/action_taken/original_content`，重复事件不重复处理。
- 新增 Flyway migration 覆盖 `sensitive_word` / `moderation_log`，不依赖 docker 初始 schema。

测试方式：

- `mvn -q -pl im-message-service -am test`
- `mvn -q -pl im-bootstrap -am test`

完成记录：

- 新增 `V4__moderation.sql`，补齐 `sensitive_word` / `moderation_log` Flyway 迁移，并为 `moderation_log` 增加 `(tenant_id, message_id, provider)` 唯一键防重复日志。
- 新增 `com.im.message.moderation` 子包：监听 `msg.saved.*` 做文本敏感词审核，监听 `word.reload` 热刷新词库缓存。
- 词库匹配支持平台级 `tenant_id IS NULL` 和租户级词；`sensitive_word` 因平台词语义加入 MyBatis tenant ignore，mapper SQL 显式约束租户范围。
- 命中 REVOKE 词后，审核服务在同一事务内调用 `MessageRevokeService.revokeIfNeeded(..., BY_MODERATION, 0)` 并写入 `moderation_log.original_content` 留证。
- 新增 Redis 审核事件去重 key，避免 clean 消息被重复扫描；违规消息同时依赖 `moderation_log` 唯一键和撤回幂等兜底。
- `MessageRevokeService` 保留原 REST/gRPC `revoke` 入口，并新增返回 boolean 的 `revokeIfNeeded` 供审核链路判断是否真的撤回。
- 已执行 `mvn -q -pl im-message-service -am test`，通过。
- 已执行 `mvn -q -pl im-bootstrap -am test`，通过。

---

## 4. 后续候选任务（当前阶段不执行）

这些任务进入后续阶段，不在当前 im-server MVP 优先队列：

- 客服会话。
- Web/App 客户端。
- 图片/语音消息（PR1 复审 Q3）：进入文件上传与消息类型 PR。

## 5. 当前待确认点

- 暂无。T20 已修正 `architecture.md` 中与当前网关协议和职责边界不一致的旧表述。
