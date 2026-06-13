# 后端架构全面审查报告

> 审查日期：2026-06-13 | 审查人：Claude（架构师视角）
> 范围：Rust 网关 + Java 业务层（全部已实现模块）

---

## 0. 快速结论

| 层 | 等级 | 核心评语 |
|---|---|---|
| Rust 网关 | ✅ **生产可用** | 协议正确、容错完整、测试覆盖、可长期少改动 |
| Java 架构 | 🟡 **良好但有可改进项** | 核心链路扎实，Auth 散落是主要痛点，几处细节需修 |
| 接口设计 | 🟡 **基本规范，有一处 URL 不一致** | 需在前后端联调前对齐 |

---

## Part 1：Flyway V4 Checksum Mismatch 修复

### 根因

`f3e5741` commit 删除了 V4 末尾的冗余 `PREPARE/EXECUTE` 块（该块条件性添加 UNIQUE KEY，
但 `CREATE TABLE` 语句中已内含该约束），文件内容变了但 V4 已经 apply 到 DB，
导致 `flyway_schema_history` 中存储的旧 checksum（`1110920203`）
与当前文件的 checksum（`197511072`）不一致。

V4 当前内容是**正确的**（删除冗余 SQL 是对的）。需要的是让 Flyway 重新认可当前文件。

### 修复方法（两选一）

**方法 A — 推荐：直接 SQL 修复（最快）**

连接 MySQL 执行：
```sql
UPDATE flyway_schema_history
SET    checksum = 197511072
WHERE  version  = '4';
```

**方法 B — 标准方式：flyway repair**

```bash
# 在 im-server 目录下
mvn flyway:repair -pl im-bootstrap
```

或者临时在 `application-local.yml` 加（仅限本地开发）：
```yaml
spring:
  flyway:
    repair-on-migrate: false   # Flyway 9.x 不支持 repair-on-migrate
    # 正确做法是用 validate-on-migrate: false（不推荐生产用）
    validate-on-migrate: false
```

> ⚠️ `validate-on-migrate: false` 只用于紧急本地救场，生产 **绝对不能** 关闭校验。

**推荐操作**：执行方法 A 的 SQL，重启应用即可。

### 附：V1 迁移文件缺失说明

`db/migration/` 目录只有 V2~V6，无 V1。V1 是初始化 schema（user/conversation/message 等核心表），
曾经存在过，已被删除（属于已知情况，Flyway 因 V1 文件被删而不报错，
说明 flyway_schema_history 里 V1 有记录且 Flyway 配置允许 missing migration，
或者 V1 本身就是 baseline — 可接受）。

---

## Part 2：Rust 网关健全性审查

### 2.1 架构职责评估

网关完整实现了 CLAUDE.md 约定的"零业务"原则：

| 职责 | 实现 | 评价 |
|------|------|------|
| WS 连接生命周期 | `connection.rs` AUTH → registry → read_loop → cleanup | ✅ 正确 |
| Token 校验 | gRPC `GatewayAuth.VerifyToken`，不持有 JWT 密钥 | ✅ 零业务 |
| 帧编解码 | `frame_codec.rs` protobuf binary，无业务 proto 依赖 | ✅ 透传式 |
| 上行透传 | `Uplink.Dispatch(cmd, bytes)` gRPC，Java 做路由 | ✅ 符合 D19 |
| 下行推送 | RabbitMQ 消费 → 按 conn_id 精确投递 | ✅ 完整 |
| 路由表维护 | Java push-service 通过 ConnEvent 维护 Redis | ✅ 无状态网关 |

### 2.2 稳定性评估

**✅ 已具备（生产级）**

| 能力 | 实现细节 |
|------|---------|
| 心跳检测 | `idle_timeout = heartbeat_interval * 3`；最低值强制 30s |
| 推送 ACK 超时 | 10s 无 ACK → disconnect → Java 侧 SYNC 补齐；不重推（协议 §3 约定）|
| 背压/慢消费 | outbound 队列满 `threshold`（默认 3）次 → 主动 close；单次满不立即踢 |
| 握手限流 | `HandshakeLimiter` token-bucket（200 req/s，burst 400） |
| ORIGIN 检查 | 白名单逐一匹配 + 大小写不敏感 |
| 防重放 | `AuthReq.timestamp` 窗口 ±300s |
| 协议版本门控 | `min_protocol_version` 配置；过低发 KICK 然后关闭 |
| 优雅退出 | SIGTERM + ctrl+c → `graceful_shutdown` → push_task abort |
| MQ 重连退避 | 指数退避 1s→30s；稳定运行 60s 后重置（避免瞬时故障惩罚） |
| Prometheus 指标 | 每租户在线数（gauge）、uplink 帧统计、push 送达/失败、ack 超时 |
| 单元测试 | timestamp 窗口、origin 白名单、心跳 floor、队列满阈值、MQ 退避 |

### 2.3 发现的问题

#### R1 — ⚠️ [中] 无最大帧大小检查（潜在 OOM 风险）

`frame_codec::decode` 直接 `Frame::decode(payload)`，没有限制输入大小。
恶意客户端可以发送超大 binary frame 消耗内存。

**修复建议（Codex 实现）**：

```rust
// frame_codec.rs
const MAX_FRAME_BYTES: usize = 64 * 1024; // 64 KB

pub fn decode(payload: &[u8]) -> Result<Frame> {
    if payload.len() > MAX_FRAME_BYTES {
        anyhow::bail!("ws frame too large: {} bytes", payload.len());
    }
    Frame::decode(payload).context("invalid ws frame")
}
```

同时在 `config.rs` 加环境变量 `IM_GATEWAY_MAX_FRAME_BYTES`（可选）。

#### R2 — ⚠️ [低] 共享单个 gRPC Channel

`RpcClients::connect` 建一条 `tonic Channel` 分给 `GatewayAuth`、`Uplink`、`ConnEvent` 三个客户端。
三者共享连接池和背压，其中一个服务慢会影响另外两个。

**MVP 影响**：单进程 Java 时完全没有问题（三个服务同一端口）。
拆服务时需要各自独立 Channel。当前可接受，记录为技术债。

#### R3 — ⚠️ [低] `push_task.abort()` 不优雅

主 WS 服务 shutdown 后直接 `push_task.abort()`，正在处理中的 RabbitMQ delivery
可能不会被 ack，导致消息重投（幂等设计下可自愈）。影响极小但在 k8s 滚动发布时有窗口。

**修复建议**（可在 Phase 2 做）：用 `CancellationToken` 通知 push consumer 退出后 join。

#### R4 — ℹ️ [信息] HandshakeLimiter 是全局速率不是 per-IP

当前限流器是进程级 token-bucket，不区分来源 IP。
单一客户端可耗尽令牌影响其他用户。

**处理方式**：应在前置 nginx 配置 `limit_req_zone $binary_remote_addr` per-IP 限流，
Rust 侧做全局兜底保护。这是正确的分层防护，Rust 侧无需改。

### 2.4 Rust 网关最终评定

> **结论：Rust 网关在 MVP 1~5 万连接规模下稳定可靠，核心逻辑正确，可以长期少改动。**
> 唯一需要 Codex 补充的是 R1（最大帧大小检查），其余可接受或二阶段处理。

---

## Part 3：Java 后端架构审查

### 3.1 整体架构优点

| 维度 | 评价 |
|------|------|
| 模块隔离 | enforcer 禁止跨业务模块直接依赖，已落地到所有业务模块 pom.xml |
| 依赖注入 | 全部构造器注入，无 `@Autowired` 字段注入 |
| 错误体系 | `ErrorCode` enum 统一映射 proto error code + HTTP status，整洁 |
| 全局异常处理 | `GlobalExceptionHandler` 覆盖 `ImException/Validation/Unexpected`，完整 |
| TenantContext | `callWithTenant` save-restore 模式正确，虚拟线程安全（D25） |
| 消息幂等 | findExisting → tryAcquire → DuplicateKey catch 三层防护 |
| Seq 分配 | 行锁 `UPDATE max_seq=max_seq+1` + 同事务读，无空洞，符合 D26 |
| Outbox | 同事务写 message + outbox，异步投 MQ，符合 D18 |
| 测试覆盖 | 关键 Service 都有单元测试，集成测试用 Testcontainers |

### 3.2 发现的问题

#### A1 — 🔴 [高优先级] Auth 校验散落在各 Controller

**现状**：`GroupController`、`FileController`、`MessageHistoryController` 每个都重复：

```java
AuthTokenClaims claims = tokenVerifier.verifyAccessToken(BearerTokenExtractor.extract(authorization));
if (claims.tenantId() != TenantContext.requiredTenantId()) {
    throw new ImException(ErrorCode.TOKEN_INVALID);
}
```

这是每个 Controller 方法都要写一遍的样板。随着后续 Controller 增加（CS 坐席 API、Widget API 等），
必然有人忘写，成为鉴权漏洞入口。且同一用户信息（userId、tenantId）要一遍遍从 token 解析。

**正确做法**：抽 `JwtAuthInterceptor implements HandlerInterceptor`，
在 `preHandle` 统一解析 JWT，将 userId 存入 Request attribute 或 ThreadLocal `UserContext`，
业务代码直接 `UserContext.requiredUserId()`，不再接受 Authorization header 参数。

```java
// im-common 新增
public final class UserContext {
    private static final ThreadLocal<AuthTokenClaims> CLAIMS = new ThreadLocal<>();
    public static long requiredUserId() { return current().userId(); }
    public static AuthTokenClaims current() {
        return Optional.ofNullable(CLAIMS.get())
            .orElseThrow(() -> new ImException(ErrorCode.TOKEN_INVALID));
    }
    // set/clear 由 interceptor 调用
}

// im-common 新增
@Component
public class JwtAuthInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        String auth = req.getHeader("Authorization");
        AuthTokenClaims claims = tokenVerifier.verifyAccessToken(
            BearerTokenExtractor.extract(auth));
        if (claims.tenantId() != TenantContext.requiredTenantId()) {
            throw new ImException(ErrorCode.TOKEN_INVALID);
        }
        UserContext.set(claims);
        return true;
    }
    @Override
    public void afterCompletion(...) { UserContext.clear(); }
}
```

Controller 就变成：

```java
@PostMapping
public ApiResponse<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
    return ApiResponse.ok(groupService.createGroup(UserContext.requiredUserId(), request));
}
```

注意：`/api/v1/auth/*`（register/login/refresh）和 `/api/v1/cs/widget/*`（访客，用不同 auth 逻辑）
需要用 `excludePathPatterns` 排除或分别配置。

#### A2 — 🟡 [中] MessageHistoryController 用 ext Map 解析消息类型

`toResponse` 方法中：
```java
intExt(push, "msg_type", push.getContent().hasText() ? 1 : 0)
```
`push.getExtOrDefault("msg_type", "")` 是 string → int 运行时转换，类型不安全，
且 fallback 只判断了 hasText，忽略了 IMAGE/VOICE/FILE/NOTIFICATION。

**修复建议**：直接从 `push.getContent().getContentCase()` 取类型：

```java
private int msgType(MsgContent content) {
    return switch (content.getContentCase()) {
        case TEXT         -> 1;
        case IMAGE        -> 2;
        case VOICE        -> 3;
        case FILE         -> 4;
        case NOTIFICATION -> 5;
        case CUSTOM       -> 6;
        default           -> 0;
    };
}
```

同时将消息状态 (`status`)、撤回原因 (`revoke_reason`) 也改为从 proto 强类型字段获取，
不要依赖 ext Map 里的字符串。这意味着 `MsgPush` proto 需要补充 `int32 status` 和 `RevokeReason revoke_reason` 字段。

#### A3 — 🟡 [中] REST URL 不一致

| Controller | 实际 URL |
|-----------|---------|
| `MessageHistoryController` | `GET /api/v1/convs/{convId}/messages` |
| 前端架构文档 | `GET /api/v1/messages?convId=&beforeSeq=&limit=` |

需要在前端开发前统一。建议保留 `GET /api/v1/convs/{convId}/messages`（RESTful 风格更清晰，
convId 是路径参数而非 query），前端架构文档同步修正。

**同时检查**：撤回接口实际是 `POST /api/v1/convs/{convId}/messages/{seq}/revoke`，
前端文档写的是 `POST /api/v1/messages/revoke`，也需对齐。

#### A4 — 🟡 [中] SequenceService 两次 DB round-trip

```java
// 当前实现
conversationProgressMapper.incrementMaxSeq(conversationId);   // UPDATE
Long seq = conversationProgressMapper.selectMaxSeq(conversationId);  // SELECT
```

同一事务内两条语句无一致性问题，但多一次 DB round-trip。

**优化方案**：用 MyBatis `@SelectKey` 在 UPDATE 之后自动 SELECT：

```java
@Update("UPDATE conversation SET max_seq = max_seq + 1 WHERE id = #{convId}")
@SelectKey(statement = "SELECT max_seq FROM conversation WHERE id = #{convId}",
           keyProperty = "seq", before = false, resultType = Long.class)
int incrementAndGetMaxSeq(@Param("convId") long convId, @Param("seq") Long seq);
```

或者更简单：直接在 Mapper 用一次 `UPDATE ... RETURNING`（MySQL 8.0 不支持，
可改用 Session 级变量）。最简单的做法是保持现状，两次查询本身没有 bug，
仅当性能成为瓶颈时优化。

#### A5 — 🟡 [中] 幂等 busy-wait 1s 过长

```java
// MessageIdempotencyService
Thread.sleep(50ms) * 20次 = 最长 1s 等待
```

虚拟线程中 sleep 无载体线程占用，但用户侧会感知 1s 延迟。
**建议**将 `WAIT_ATTEMPTS` 改为 10，`WAIT_INTERVAL` 改为 100ms，最长等待 1s→500ms。
或者不等待，直接返回正在处理的错误码让客户端重试（IM 客户端发送逻辑已有重试机制）。

#### A6 — 🟢 [低] CustomContent 在 MessageSendService 被 default 分支拒绝

```java
case TEXT -> { ... }
case IMAGE, VOICE, FILE -> { }
default -> throw new ImException(ErrorCode.VALIDATION_FAILED, "unsupported content type");
```

`CUSTOM` content 走 default 直接被拒。如果 CS widget 未来发自定义消息（含 JSON payload），
会被服务端拒绝。

**修复**：加 `case CUSTOM -> {}` 分支（跳过正文校验，由业务层处理）。

#### A7 — 🟢 [低] PushDispatchService.kickMessage if-chain

```java
if (reason == KickNotify.Reason.NEW_DEVICE_LOGIN_VALUE) return "new device login";
if (reason == KickNotify.Reason.ADMIN_OFFLINE_VALUE) return "admin offline";
// ...
```

改为 `switch` 表达式，更符合 Java 21 风格，且更易扩展：

```java
private String kickMessage(int reason) {
    return switch (reason) {
        case KickNotify.Reason.NEW_DEVICE_LOGIN_VALUE -> "new device login";
        case KickNotify.Reason.ADMIN_OFFLINE_VALUE    -> "admin offline";
        case KickNotify.Reason.TOKEN_EXPIRED_VALUE    -> "token expired";
        case KickNotify.Reason.PROTO_TOO_OLD_VALUE    -> "protocol too old";
        default                                        -> "kicked";
    };
}
```

#### A8 — 🟢 [低] AuthController 冗余 bearerToken 静态方法

`AuthController.bearerToken()` 与 `BearerTokenExtractor.extract()` 功能重复。
AuthController 内部有自己的实现，其他 Controller 用 `BearerTokenExtractor`，二者逻辑等价。
**建议**：删除 `AuthController.bearerToken()`，统一用 `BearerTokenExtractor.extract()`。

### 3.3 设计模式与多态使用评估

#### ✅ 已良好使用的模式

**策略模式 — `CmdHandlerRegistry`**：
上行帧路由用注册表 + 策略，新增 Cmd 只需实现 `CmdHandler` 并注册，
`UplinkGrpcService.dispatch` 不需要修改，完全符合开闭原则。

**工厂模式 — `MsgSavedEventFactory`**：
消息持久化后用工厂构建 Outbox 事件，封装 routing key 生成逻辑，
`MessagePersistService` 不知道 MQ 路由细节，解耦干净。

**Repository 模式 — `OnlineRouteRepository`**：
路由表的 Redis 操作被 Repository 封装，`PushDispatchService` 不直接操作 Redis Key 格式。

**值对象 — Java Record**：
`ApiResponse`、DTO 等广泛使用 record，不可变、无 setter，类型安全。

#### ⚠️ 可改进的设计

**auth 解析应用装饰器/拦截器而非散落 Controller**：（见 A1，最核心问题）

**GroupConversationEntity / GroupMessageEntity 的设计意图需说明**：
这两个 Entity 映射到与 im-conversation-service / im-message-service 完全相同的物理表
（`@TableName("conversation")` / `@TableName("message")`）。
这是**有意为之的模块视图模式**（每个模块有自己的"只读视图" Entity，只访问自己需要的列），
不是双写。此设计正确，但需要在 review-checklist 或注释中说明，避免后继开发者误解为双表。

---

## Part 4：接口规范性评估

### 4.1 REST 接口整体评分：良好

| 维度 | 状态 | 说明 |
|------|------|------|
| URL 风格 | ✅ | RESTful 资源型，`/api/v1/{资源}/{id}/{子资源}` |
| HTTP 方法 | ✅ | GET/POST/PATCH/DELETE 语义正确 |
| 响应结构 | ✅ | 统一 `ApiResponse{code, message, data, traceId, timestamp}` |
| 错误码 | ✅ | 业务错误码 + HTTP status 双轨，前端可按 HTTP 状态快速判断 |
| 校验 | ✅ | Bean Validation + GlobalExceptionHandler 统一返回错误 |
| 版本化 | ✅ | `/api/v1/` 前缀 |

### 4.2 URL 不一致清单（需 Codex 对齐前端）

| 功能 | Controller 实际 URL | 前端架构文档写的 URL | 建议保留 |
|------|---------------------|---------------------|---------|
| 消息历史 | `GET /api/v1/convs/{convId}/messages` | `GET /api/v1/messages?convId=` | **Controller** |
| 撤回消息 | `POST /api/v1/convs/{convId}/messages/{seq}/revoke` | `POST /api/v1/messages/revoke` | **Controller** |

> 前端架构文档 `docs/frontend-architecture.md §6.3` 需要同步修正这两条 URL。

### 4.3 缺失的接口（前端需要但后端未实现）

| 接口 | 状态 | 影响 |
|------|------|------|
| `GET /api/v1/user/:userId` | 未见实现 | 前端加载对方资料 |
| `GET /api/v1/files/:objectKey/download` | 未见实现 | 文件预签名下载 URL |
| `GET /api/v1/groups/:groupId` | 未见 GET | 前端加载群信息 |
| `GET /api/v1/convs/{convId}/members` | 未见 | 群成员列表 |

这些接口**需要 Codex 补充**，属于前端联调前的必要工作。

---

## Part 5：待 Codex 处理的改进任务（按优先级）

### 必须在前端联调前完成

| 编号 | 任务 | 优先级 |
|------|------|--------|
| B1 | 修复 Flyway V4 checksum（执行上文 SQL 或 repair） | 🔴 立即 |
| B2 | 修复 `frame_codec::decode` 加最大帧大小检查 | 🔴 高 |
| B3 | 抽 `JwtAuthInterceptor` + `UserContext`，所有 Controller 去掉手动 JWT 解析 | 🔴 高 |
| B4 | 对齐前端文档 URL（`/convs/{convId}/messages`）；修正前端架构文档 §6.3 | 🟡 中 |
| B5 | 补充缺失接口：用户资料/文件下载/群信息/群成员列表 | 🟡 中 |
| B6 | `MessageSendService` 加 `case CUSTOM -> {}` | 🟡 中 |
| B7 | `MessageHistoryController.toResponse` 改用 `content.getContentCase()` | 🟡 中 |

### 可推迟到第二阶段

| 编号 | 任务 | 优先级 |
|------|------|--------|
| B8 | `PushDispatchService.kickMessage` 改 switch 表达式 | 🟢 低 |
| B9 | 删除 `AuthController.bearerToken()` 冗余方法 | 🟢 低 |
| B10 | `SequenceService` 合并两次 DB round-trip（性能优化） | 🟢 低 |
| B11 | 幂等等待时间从 1s 降到 500ms | 🟢 低 |
| B12 | Rust 网关：push_task 优雅退出（`CancellationToken`） | 🟢 低 |
| B13 | `MsgPush` proto 补 `status` 和 `revoke_reason` 强类型字段 | 🟢 低 |

---

## Part 6：各模块最终稳定性评估

| 模块 | 改动频率预测 | 备注 |
|------|------------|------|
| im-gateway-rust | 🟢 几乎不改 | 协议稳定后 Rust 侧锁死，只加帧大小检查 |
| im-common | 🟢 少改 | 加 UserContext/JwtAuthInterceptor 后基本稳定 |
| im-user-service | 🟡 小改 | 加坐席 status API；手机号登录二阶段 |
| im-message-service | 🟢 少改 | 加 CUSTOM 分支；历史接口 URL 对齐 |
| im-conversation-service | 🟢 少改 | CS 扩展由 im-cs-service 调用，主模块稳定 |
| im-push-service | 🟡 小改 | T33 CS 消息路由扩展（坐席推送） |
| im-group-service | 🟢 少改 | 补 GET 接口 |
| im-file-service | 🟢 少改 | 补 download URL 接口；加 video MIME |
| im-cs-service | 🔴 持续开发 | T32~T36 待实现 |
