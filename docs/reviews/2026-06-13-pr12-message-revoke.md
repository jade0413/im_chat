# PR-12 审查报告：feat: add message revoke flow (ce1ef12)

审查人：Claude｜日期：2026-06-13｜关联任务：T17

## 结论：**通过，可合并** ✅

无严重项。三处建议级问题不阻塞合并。

---

## 通过项（✅）

| 项 | 验证结果 |
|---|---|
| 事务完整性（D18 Outbox）| ✅ `revoke()` 标 `@Transactional`：`selectByConvSeq` + `markRevoked` + `updateLastMsgAbstract` + `outboxWriter.write()` 同一事务，落库即可靠投递 |
| 幂等性 | ✅ 双重保险：① `isRevoked()` 前置短路返回；② `markRevoked` WHERE 加 `AND status <> #{status}`，并发撤回时第二方 `updated=0` 静默返回，不重复写 outbox |
| `BY_SENDER` 权限校验 | ✅ 三卡：① `getMemberConv` 验证操作者是会话成员；② `senderId == operatorUserId`；③ `createdAt` 在 2 分钟窗口内，超窗口抛 `REVOKE_WINDOW_EXPIRED` |
| `BY_ADMIN` 跳过发送者校验 | ✅ 符合设计：管理撤回不受窗口/成员限制；`validatePermission` 仅在 `BY_SENDER` 分支执行检查 |
| `MessageAssembler` 撤回内容脱敏 | ✅ `isRevoked()` 时：不设 `content`（proto default 空 MsgContent），仅透传 `ext.status=2` + `ext.revoke_reason`；历史拉取和实时推送路径统一 |
| 新消息 `ext.status` 初始化 | ✅ `toSave()` 写入 `ext.status = "1"`；老消息无此字段时 `toPush` 动态补，客户端可安全 absent→1 处理 |
| `updateLastMessageAbstractIfLatest` 条件 | ✅ `WHERE id=? AND max_seq=seq`：只在撤回的是最新消息时更新摘要；并发新消息到达后 `max_seq` 已递增，条件不满足，不会覆盖新消息摘要 |
| 推送扇出语义 | ✅ 撤回通知推全部会话成员（含 GROUP 全员）是正确语义——所有成员都需替换本地消息展示为"已撤回"；与已读回执 GROUP 仅推自身其他设备的区分是对的 |
| 去重 | ✅ `dedupId = eventId header 优先，fallback serverMsgId`；`serverMsgId` 全局唯一（Snowflake），不依赖 MQ delivery tag |
| proto 兼容性 | ✅ `MsgRevokedEvent.server_msg_id = 6` 新增字段，向后兼容；已有消费者收到无此字段的旧事件时 `getServerMsgId()` 返回 0，fallback 到 `eventId` header |
| MQ 绑定 | ✅ `PushRabbitConfig` 新增 `pushMsgRevokedQueue` + `Binding`，routing key `msg.revoked.*`，与 `MsgRevokedEventFactory.routingKey()` 格式一致 |
| REST 端点 | ✅ `POST /{convId}/messages/{seq}/revoke` 强制 `BY_SENDER`，普通用户不可绕过；token 校验与 tenantId 一致性检查复用 `verifiedClaims()` 提取为私有方法，消除重复 |
| 模块依赖铁律 | ✅ `im-push-service` 新增 `MsgRevokedEventConsumer` 只依赖 `im-common` + `im-proto-java`，无跨模块业务依赖 |
| 测试覆盖 | ✅ `MessageRevokeServiceTest` 5 用例：正常撤回/重复撤回幂等/他人撤回/超窗口/管理撤回；`MsgRevokedEventConsumerTest` 推送+去重；`MessageHistoryControllerTest` 撤回状态回显+撤回端点；`MessageGrpcServiceTest` 正常+业务错误码 |

---

## 建议项（B，不阻塞）

### B1 — `updateLastMessageAbstractIfLatest` 无显式 `tenant_id`

```java
@Update("""
    UPDATE conversation
    SET last_msg_abstract = #{lastMsgAbstract}
    WHERE id = #{conversationId}
      AND max_seq = #{seq}
    """)
```

`conversation.id` 是 Snowflake 全局唯一 PK，实际无跨租户数据风险。但与项目约定"自定义 `@Update/@Select` SQL 显式传 `tenant_id`"不符（同 PR-10 S1 同类问题）。建议补 `AND tenant_id = #{tenantId}` 保持一致性，避免 MP 拦截器 ignore 列表调整时引入静默 bug。

### B2 — `BY_ADMIN` 撤回无调用方鉴权

`validatePermission` 对 `BY_ADMIN` 分支完全跳过，gRPC 端任何内部调用者均可指定 `BY_ADMIN` 撤回任意消息。REST 端点硬编码 `BY_SENDER`，不受影响。二阶段管理后台上线时需在 gRPC 层补 admin token/role 校验。MVP 内部服务信任模型下可接受，记录在案。

### B3 — `@Autowired` 冗余

`MessageRevokeService` 公有构造器上标注 `@Autowired`，Spring Boot 单构造器 Bean 自动注入无需显式标注。无功能影响，可顺手删除。

---

## 附：整体质量评价

撤回链路实现完整且结构清晰：Outbox 事务边界正确、双路幂等（前置判断 + UPDATE 条件）稳健、测试用例覆盖全部权限分支。`MessageAssembler` 对撤回消息的内容脱敏处理干净，历史拉取和实时推送复用同一 `toPush` 路径，不存在遗漏点。D16 挂账的撤回通知事件（T17 验收标准）全部兑现。
