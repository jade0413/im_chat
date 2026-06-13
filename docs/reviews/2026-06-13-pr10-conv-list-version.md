# PR-10 审查报告：feat: add conversation list version sync (ca77a7c)

审查人：Claude｜日期：2026-06-13｜关联任务：T27

## 结论：**打回，需整改** ❌

一项严重缺陷（S1）：`selectAfterVersion` 查询缺少 `tenant_id` 过滤，违反核心约定"tenant_id 贯穿一切"且无法利用为之专门设计的索引。其余均为建议级。

---

## 严重项（S，阻塞合并）

### S1 — `selectAfterVersion` 缺少 `tenant_id` 过滤

**位置**：`im-conversation-service/.../dao/mapper/ConversationUserConvEventMapper.java`

```java
@Select("""
    SELECT *
    FROM user_conv_event
    WHERE user_id = #{userId}
      AND event_version > #{afterVersion}
    ORDER BY event_version ASC
    LIMIT #{limit}
    """)
List<UserConvEventEntity> selectAfterVersion(@Param("userId") long userId,
    @Param("afterVersion") long afterVersion,
    @Param("limit") int limit);
```

**三重问题**：

**1. 违反核心约定**：本 PR 内同一模块的 `ConversationUserConvVersionMapper` 所有三个自定义 SQL 方法（`insertInitial` / `increment` / `selectVersion`）均显式传入并过滤 `tenant_id`。`selectAfterVersion` 独此一例绕过，违反"tenant_id 贯穿一切"约定。

**2. 索引失效**：`user_conv_event` 表的所有索引均以 `tenant_id` 为前导列：
```
UNIQUE KEY uk_tenant_user_version (tenant_id, user_id, event_version)
KEY idx_tenant_user_id (tenant_id, user_id, id)
KEY idx_tenant_user_conv (tenant_id, user_id, conv_id)
```
查询条件仅有 `user_id + event_version`，MySQL 无法使用以上任何索引，退化为全表扫描。

**3. 多租户安全风险**：即便 MP `TenantLineInnerInterceptor` 可能自动追加 `tenant_id` 条件，当前实现依赖拦截器的隐式行为。若 `user_conv_event` 被加入拦截器 ignore 列表（两个模块共用同一张表，有此操作动机），拦截器不生效时同 `user_id` 的跨租户数据可见。

**修复**：

```java
@Select("""
    SELECT *
    FROM user_conv_event
    WHERE tenant_id = #{tenantId}
      AND user_id = #{userId}
      AND event_version > #{afterVersion}
    ORDER BY event_version ASC
    LIMIT #{limit}
    """)
List<UserConvEventEntity> selectAfterVersion(
    @Param("tenantId") long tenantId,
    @Param("userId") long userId,
    @Param("afterVersion") long afterVersion,
    @Param("limit") int limit);
```

`ConversationService.listMemberConvs` 中同步传入 `tenantId`：
```java
List<UserConvEventEntity> events = userConvEventMapper.selectAfterVersion(
    tenantId, userId, afterVersion, effectiveLimit + 1);
```

---

## 建议项（B，不阻塞，下一 PR 可带上）

### B1 — `recordGroupMemberEvents` 在大群 rename 时产生 N×3 SQL

**位置**：`im-group-service/.../service/GroupService.java` 第 405~412 行

`rename()` 调用 `recordGroupMemberEvents()`，后者对每个成员串行执行一次 `record()`，每次 `record()` = 1× `INSERT IGNORE` + 1× `UPDATE` + 1× `SELECT`。500 人群 rename = 1500 条 SQL 在同一事务内，持有 500 行 `user_conv_version` 行锁直至提交。

rename 是低频操作，MVP 可接受。建议后续优化为：
- 批量 `INSERT INTO ... VALUES (...) ON DUPLICATE KEY UPDATE` 替代逐行 INSERT IGNORE + UPDATE
- 批量 `INSERT INTO user_conv_event (...) VALUES (...)` 替代逐行 insert

### B2 — full-sync fallback 截断 >100 会话

**位置**：`ConversationService.listMemberConvs` 第 97~100 行

```java
if (events.size() > effectiveLimit) {
    return new ConversationListResult(listActiveMemberConvs(userId, effectiveLimit),
        true, currentVersion);
}
```

`listActiveMemberConvs` 内部也受 `effectiveLimit`（≤100）约束。若用户活跃会话 >100，fallback 返回截断列表但 `full_sync=true`，客户端用不完整列表替换本地状态，余下会话被误判为已删除。

MVP 阶段此边界极少触达，但应在协议注释或文档中说明该限制，避免后期误解。

### B3 — `toEventConvInfo` N+1 查询

**位置**：`ConversationService.toEventConvInfo` + `toMemberConvInfo`

每条 event 触发 2~3 个 SQL（findMember + selectConversation + findMembers/selectGroupInfo）。diff 上限 100 条时最多 300 SQL。MVP 可接受，二阶段可优化为批量 IN 查询。

---

## 通过项（✅）

| 项 | 验证结果 |
|---|---|
| V2 Migration | ✅ `user_conv_version` + `user_conv_event` DDL 正确；主键/唯一键/索引均以 tenant_id 为前导列；Flyway 在 V1 基线上直接增加 V2，无兼容性问题 |
| Proto 兼容性 | ✅ `ConvInfo.deleted(bool)` field 14 新增；`ListMemberConvsReq.conv_list_version(int64)` field 3 新增；均向后兼容，无字段号复用 |
| `UserConvEventRecorder` 逻辑 | ✅ INSERT IGNORE → UPDATE → SELECT 三步正确；version 单调递增后才 insert event，`uk_tenant_user_version` 唯一约束保证 version 不重复 |
| 模块隔离 | ✅ `im-group-service` 独立实现 `GroupUserConvEventRecorder`，不跨模块依赖 `UserConvEventRecorder`；两者写同一张表（`@TableName("user_conv_event")`），无冲突 |
| `ConversationService.listMemberConvs` 主逻辑 | ✅ afterVersion≤0 → 全量；afterVersion≥currentVersion → 空 diff；events≤limit → 精确 diff；events>limit → full-sync fallback；逻辑分支完整 |
| `toEventConvInfo` REMOVED 处理 | ✅ `eventType=REMOVED` 或 member 已被软删除 → 返回 `deleted=true` 的 ConvInfo，客户端可据此清理本地缓存 |
| `MessageQueryService.sync` 集成 | ✅ `convListVersion` 正确透传到 `SyncResp`；deleted conv 走 `addDeletedDelta`（不拉消息）；changed conv 走 `addDelta` 拉增量消息 |
| `ConversationGrpcService` | ✅ `listMemberConvs` gRPC 接口正确委托 service，错误处理完整；TenantContext 由 gRPC 拦截器注入 |
| 测试覆盖 | ✅ `UserConvEventRecorderTest` 验证 version 递增与 event 写入；`ConversationServiceTest` 覆盖全量/空 diff/精确 diff 三路；`MessageQueryServiceTest` 覆盖 deleted conv / full-sync / conv diff 场景 |
| GroupService 事件触发点 | ✅ createGroup→CREATED（每成员）、addMembers→CREATED（新成员）、removeMember→REMOVED（被踢者）、rename→UPDATED（全成员）；触发时机与语义对应 |

---

## 整改清单

1. **S1（必须）** `ConversationUserConvEventMapper.selectAfterVersion`：补 `tenant_id` 参数和 WHERE 条件；`ConversationService.listMemberConvs` 传入 `tenantId`。

整改后重新提交，S1 验证通过即可合并。B1~B3 可带上或下一 PR 跟踪。

---

## 附：整体质量评价

conv_list_version 的双表设计（version 水位 + event 流水）架构清晰，diff/fallback 逻辑分支完整，proto 演进兼容，测试覆盖到位。GroupService 各操作触发事件的时机语义正确。唯一严重问题是 `selectAfterVersion` 漏掉 `tenant_id`——同 PR 内 version mapper 的三个方法都做对了，event mapper 这一处漏掉，属于低级遗漏。修完 S1 后本 PR 质量达标。
