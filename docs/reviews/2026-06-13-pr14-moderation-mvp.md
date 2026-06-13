# PR-14 审查报告：feat: add moderation mvp (a0e33ce)

审查人：Claude｜日期：2026-06-13｜关联任务：D16

## 结论：**通过，可合并** ✅

无严重项。两处建议级问题不阻塞合并。

---

## 通过项（✅）

| 项 | 验证结果 |
|---|---|
| D16 架构对齐 | ✅ 先发后审：`MsgSavedModerationConsumer` 订阅 `msg.saved.*`，`ModerationService.moderate()` 异步审核；文本走本地 DFA 词库，违规→系统撤回+审核日志，与设计文档完全一致 |
| `BY_MODERATION` proto | ✅ `RevokeReason.BY_MODERATION = 2` 已在 `im-proto/proto/common/enums.proto` 定义；`validateRequest` 对此 reason 允许 `operatorUserId=0`（`SYSTEM_OPERATOR_USER_ID`），`validatePermission` 跳过所有检查（正确，系统撤回无需发送者校验） |
| 事务完整性 | ✅ `moderate()` 标 `@Transactional`，调用 `revokeIfNeeded()`（propagation=REQUIRED 共享同一事务）：markRevoked + updateLastMsgAbstract + outboxWriter + insertModerationLog 四步原子提交，符合 D18 Outbox 约定 |
| 双路幂等 | ✅ Redis 快速去重（`isMarked` 前置短路）+ DB 强去重（`hasProcessed` SELECT + `DuplicateKeyException` catch on insertLog）。并发撤回：`markRevoked WHERE status<>REVOKED` 保证至多一次写入；并发 insertLog：唯一键 `uk_tenant_msg_provider` + DuplicateKeyException 静默忽略 |
| `sensitive_word` 跳过拦截器 | ✅ 添加到 `TenantLineHandlerConfig.IGNORED_TABLES`；原因正确：`tenant_id` 可为 NULL（平台级词库），拦截器追加 `AND tenant_id=?` 会过滤掉 NULL 行导致平台词失效 |
| `SensitiveWordMapper` 查询 | ✅ `WHERE enabled=1 AND (tenant_id IS NULL OR tenant_id=#{tenantId})`，无拦截器干扰；ORDER BY 确保租户自定义词优先于平台词返回 |
| 词库缓存与热更新 | ✅ `ConcurrentHashMap` + `computeIfAbsent` 懒加载；`WordReloadEvent` → `reload(tenantId)` 使缓存失效；多实例场景每个实例独立监听同一 Queue，各自更新本地缓存 |
| `revokeIfNeeded` 返回值语义 | ✅ 新增 `boolean` 返回：已撤回→`false`（ALREADY_REVOKED，不重复写日志）；并发 updated=0→`false`；正常撤回→`true`（写日志）；旧 `revoke()` 封装保持兼容 |
| MQ 绑定与队列隔离 | ✅ `moderationMsgSavedQueue` 与 `push` 的 `pushMsgSavedQueue` 是独立队列，各自绑定 `msg.saved.*`；push 和 moderation 互不影响，都能完整消费所有事件 |
| `moderation_log` 只审文本 | ✅ `content.getContentCase() != TEXT → SKIPPED`；图片/语音/文件内容当前不进入 DFA（第三方 API 二阶段），预留扩展 |
| `RedisKeys.moderationEventDedup` | ✅ `moderation:event:{tenantId}:{eventId}` 与 push 的 `push:event:...` 键空间不冲突 |
| 测试覆盖 | ✅ `ModerationServiceTest`：命中撤回+日志/去重/非文本跳过/已撤回不重日志/disabled 跳过；`MsgSavedModerationConsumerTest`：正常处理/已标记跳过/词库热更新；`SensitiveWordServiceTest`：匹配/无词/大小写；`MessageRevokeServiceTest` 新增 BY_MODERATION + operatorId=0 用例 |
| V4 迁移 | ✅ `sensitive_word` + `moderation_log` DDL 完整，含 `uk_tenant_msg_provider` 唯一键和必要索引 |

---

## 建议项（B，不阻塞）

### B1 — `ModerationService` 公有构造器 `@Autowired` 冗余

累计第 3 次出现（`MessageRevokeService` 已在 75a375a 修复，`FileService` 在 b9be982 修复，此 PR 新类未同步）。单构造器 Spring Bean 无需显式标注，可顺手删除。

### B2 — `V4__moderation.sql` 动态 SQL 块冗余

`CREATE TABLE IF NOT EXISTS moderation_log` 语句内已包含 `UNIQUE KEY uk_tenant_msg_provider`，但文件末尾仍有一段动态 SQL（`SET @... := ... PREPARE ... EXECUTE ... DEALLOCATE`）试图再次添加该唯一键。V4 是该表首次创建，动态块的"表已存在但缺少该键"场景不会发生，这段代码是死代码。建议删除，保持迁移脚本简洁。

---

## 附：整体质量评价

D16 内容安全的 MVP 实现完整且架构清晰：异步消费与主流程零耦合、DFA 词库内存缓存+热更新、双路幂等（Redis+DB）、模块内聚（moderation 包集中在 im-message-service 内、不跨模块依赖）。`revokeIfNeeded` 的 boolean 返回设计干净，避免了用异常控制流的反模式。测试覆盖全部分支。两处 B 级问题均为风格遗留，不影响正确性。
