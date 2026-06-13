# PR-15 审查报告：fix: close moderation review and outbox claim (99aa479 + 89bc7df + f3e5741)

审查人：Claude｜日期：2026-06-13｜关联任务：T15（Outbox 多实例 claim）+ PR-14 B 项收尾

## 结论：**通过，可合并** ✅

无严重项。三处建议级问题不阻塞合并。

> **说明**：`application.yml` 中的 IP 和密码变更为 Jade 本地测试临时改动，合并前需还原为 dev 占位值（localhost + 占位密码），不计入正式审查缺陷。

---

## 建议项（B，不阻塞）

### B1 — `selectClaimCandidates` 无 `tenant_id` 过滤

`CommonOutboxMapper.selectClaimCandidates` 的 SELECT 语句：

```sql
SELECT * FROM outbox
WHERE retry_count < #{maxRetries}
  AND (status IN (0,1) OR (status=3 AND claim_until < #{now}))
ORDER BY created_at ASC LIMIT #{limit}
```

`outbox` 是跨租户共享表，此处未过滤 `tenant_id`。在 MVP 单进程场景下无隔离风险，但与"tenant_id 贯穿一切"约定不符——若未来多进程部署、按租户分片轮询，现在缺少 `tenant_id` 列在索引中会有全表扫描风险。建议 `idx_claim_status` 复合索引考虑是否需要按租户拆分（二阶段评估）。可在 Open Questions 记录。

### B2 — `FileService` 已有 `@Qualifier("fileClock")` 但确认时机

`FileService` 构造器加了 `@Qualifier("fileClock")`，`FileServiceConfig` 中已有对应 `@Bean`（确认存在）。但本次 diff 只见 `FileService` 改动，`FileServiceConfig` 未变，说明 `fileClock` Bean 是早先已有的——此处仅是同步 `ModerationService` 的双构造器合并模式，没有问题。**记录仅供备查，非缺陷**。

### B3 — `RedisConsumerIdempotency` 与 `ModerationEventDeduplicator` 功能重叠未整合

新增的 `RedisConsumerIdempotency`（`consumer:dedup:{ns}:{tenant}:{key}`）和已有的 `ModerationEventDeduplicator`（`moderation:event:{tenant}:{eventId}`）逻辑几乎相同，均是 Redis SETNX。当前二者并存、未整合，不影响正确性，但增加了维护点。建议后续将 `ModerationEventDeduplicator` 重构为用 `RedisConsumerIdempotency` 实现，统一抽象（二阶段）。

---

## 通过项（✅）

### Outbox Claim 核心正确性

| 项 | 验证结果 |
|---|---|
| T15 多实例安全 | ✅ `selectClaimCandidates` 读候选 → `claim()` UPDATE 乐观锁（`WHERE id=? AND status IN(0,1) OR (status=3 AND claim_until<now)`）→ 返回 0 则跳过；多实例并发 SELECT 到同一行，只有一个 UPDATE 成功，竞争失败者静默跳过，符合 claim 模式 |
| 过期 claim 恢复 | ✅ `selectClaimCandidates` 条件包含 `status=3 AND claim_until < #{now}`；claim() 的 WHERE 也含同条件；宕机实例的 PROCESSING 记录在 TTL（30s）到期后可被其他实例重新认领 |
| 所有权校验 | ✅ `deleteClaimed(id, claimOwner, PROCESSING)` 和 `releaseClaim(id, claimOwner, ...)` 均携带 `claim_owner`，防止跨实例误删/误释放 |
| `STATUS_PROCESSING = 3` | ✅ 提取至 `OutboxWriter` 公开常量，Mapper/Poller/测试均引用同一常量，无魔数 |
| V5 迁移幂等性 | ✅ 动态 SQL 包裹 `IF NOT EXISTS` 检查（`@col_exists IS NULL`），可重复执行；`idx_claim_status (status, claim_until, created_at)` 覆盖轮询查询路径 |
| TTL 配置化 | ✅ `OutboxProperties.claimTtl`（默认 30s）+ `normalizedClaimTtl()` 防止零/负值；`application.yml` 新增 `claim-ttl: ${IM_OUTBOX_CLAIM_TTL:30s}` 可外部覆盖 |
| `defaultClaimOwner` 唯一性 | ✅ `PID@hostname-UUID`：PID+主机名区分实例，UUID 防止重启后碰撞 |
| 测试覆盖 | ✅ 4 个用例：正常发布+deleteClaimed / 发布失败+releaseClaim(FAILED) / 达到死信上限+releaseClaim(DEAD) / claim 竞争失败→跳过不发布（`verify(publisher, never()).publish(...)`）；`Clock.fixed` 控制时间，`"test-owner"` 固定 owner，断言精确 |

### PR-14 B 项修复

| 项 | 验证结果 |
|---|---|
| B1 @Autowired 冗余 | ✅ `ModerationService` 由双构造器（公有@Autowired + 包私有测试用）合并为单构造器 + `@Qualifier("moderationClock")`；`ModerationRabbitConfig` 新增 `moderationClock()` @Bean 提供生产 `Clock.systemUTC()` |
| B2 V4 动态 SQL 冗余 | ✅ 已删除 16 行死代码 |
| FileService 同步 | ✅ 同一模式：`@Qualifier("fileClock")` 替代原有歧义 |

### RedisConsumerIdempotency

| 项 | 验证结果 |
|---|---|
| 接口设计 | ✅ `tryMarkEvent`（数字 eventId）+ `tryMarkBusinessKey`（字符串 key）双接口；eventId≤0 返回 false 不调 Redis，防止无效键 |
| TTL 归一化 | ✅ null/zero/negative → 24h 兜底，调用方不需要判空 |
| 键空间隔离 | ✅ `consumer:dedup:{namespace}:{tenantId}:{key}`，namespace 有正则校验 `[a-zA-Z0-9_.-]{1,64}`，不同 consumer 互不干扰 |
| 测试 | ✅ 2 个用例：正常标记（验证 key 格式 `consumer:dedup:push:1:10`）/ eventId=0 短路 |

---

## 合并前提醒

`application.yml` 的 IP / 密码改动（本地测试用）需在合并前还原为 dev 占位值，不进入主干。B1~B3 可带上或后续跟踪。
