# PR-1 复审报告：fix: address PR1 review blockers (eae07b3)

审查人：Claude｜日期：2026-06-13｜前置：2026-06-13-pr1-mvp-foundation.md（打回）+ fix-worklist

## 结论：**通过，可合并** ✅

七项必改全部闭环，逐项验证结果：

| 项 | 验证方式 | 结果 |
|---|---|---|
| S1 enforcer | 6 个业务模块逐一声明绑定；bootstrap skip=true 带原因注释 | ✅ |
| S2 outbox 全租户 | outbox 进 IGNORED_TABLES；poller 无租户绑定；OutboxProperties.tenantId 已删；测试覆盖 tenant=2 投递 | ✅ |
| S3 去 preview | 全仓 grep 无 enable-preview 残留；TenantContext 改 ThreadLocal，且实现了 previous 值恢复（比要求更严谨，支持嵌套） | ✅ |
| S4 关系校验 | TO_USER_ID 在幂等后/resolve 前校验；CONV_ID 在 resolve 后用 peer_user_id 校验（位置合理，均在落库前）；方向正确（to 拉黑 from→BLOCKED_BY_PEER）；resolve(conv_id) 有 NOT_CONV_MEMBER 成员校验 | ✅ |
| S5 新设备全量 | conv_versions==0 走 ListMemberConvs；ConvInfo 补全 type/title/peer_user_id/read_seq | ✅ |
| S6 workerId 租约 | SETNX+TTL+Lua 比较续期，冲突抛异常 fail-fast；含单测 | ✅ |
| S7 seq 决策 | 经 Jade 复议**最终采纳 DB 行锁自增**，CLAUDE.md D26/架构 §5.1/middleware-selection/schema 注释四处文档已同步修订，流程补齐 | ✅ |

B1（UplinkRouter log.error 带 trace_id）、B2（BlockAttackInnerInterceptor）已随手修复。
额外加分项：outbox 增加 STATUS_DEAD 死信态（max retries 后转人工），优于任务单要求。

## 遗留事项（不阻塞合并，进下一 PR 跟踪）

- L1. im-common 未绑定 enforcer——它同样受"不依赖业务模块"约束，补一行声明。
- L2. listMemberConvs 存在 N+1 查询（每会话 selectById + findMembers）——会话数多的用户同步变慢，下一 PR 批量化。
- L3. Q2（Dockerfile/部署物）未在 TASKS.md 答复——push PR 前必须给排期；Q1（互踢/token_ver）、Q3（图片语音）已在 roadmap 登记。
- L4. 本复审基于代码走查（审查环境无 Maven），合并前 CI 必须全绿，并人工执行一次 enforcer 破坏性验证（任一业务模块加另一业务模块依赖→构建必须失败）。

## 流程备注

S7 出现了二次决策翻转（审查建议采纳 DB→Jade 先选 Redis→实施时复议改回 DB）。最终文档与代码一致，结果健康；
但提醒：决策翻转必须像本次一样**先改文档再写码**，CLAUDE.md D26 是当前唯一事实。
