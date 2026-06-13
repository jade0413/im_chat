# PR-8 审查报告：feat: add group chat mvp (9f24db4)

审查人：Claude｜日期：2026-06-13

## 结论：**打回，需整改** ❌

一项严重缺陷（S1）：PR-7 明确挂账"群聊 PR 必须处理"的 ReadReceiptService GROUP 扇出问题，本 PR 未触碰 ReadReceiptService.java，原问题仍在。其余均为建议级。

---

## 严重项（S，阻塞合并）

### S1 — ReadReceiptService GROUP 扇出未修复（PR-7 挂账）

**位置**：`im-server/im-conversation-service/src/main/java/com/im/conversation/service/ReadReceiptService.java`

PR-7 审查报告结尾明确写道：
> 群聊 PR 时改为：C2C 推对端+自己其他端，GROUP 仅推自己其他端。

本 PR 新增了 GROUP 会话支持，但 `ReadReceiptService.java` **一行未改**。当前实现：

```java
// 第 68-70 行（未改）
if (changed) {
    readReceiptPusher.pushReadNotify(ctx, getMemberUserIds(request.getConvId()), notify);
}

// getMemberUserIds —— 返回全部成员，无 conv_type 判断，无 deleted_at 过滤
private List<Long> getMemberUserIds(long conversationId) {
    return memberMapper.selectList(Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getConvId, conversationId)
            .orderByAsc(ConversationMemberEntity::getUserId))  // ← 无 isNull(deletedAt)
        .stream()
        .map(ConversationMemberEntity::getUserId)
        .toList();
}
```

两处缺陷合并在这里：

**缺陷 1 — conv_type 盲推**：GROUP 会话中一人上报已读 → 推送 ReadNotify 给全部 500 名成员。MVP 群聊只显示"自己未读数"，对端看不到别人何时已读，这 499 条推送是无意义的带宽消耗，且在大群下会形成推送风暴。

**缺陷 2 — 无 deleted_at 过滤**：`getMemberUserIds` 未加 `.isNull(ConversationMemberEntity::getDeletedAt)`。成员被踢后 `conversation_member.deleted_at` 已写入，但仍会出现在查询结果中并收到 ReadNotify。C2C 时成员永不退出所以无影响，但 GROUP 场景下已是真实 bug。

**要求修复如下**：

```java
// ReadReceiptService 中补 conv_type 查询
ConversationEntity conversation = ...;  // 已查，直接加一个字段读取
ConvType convType = ConvType.forNumber(...);

// pushReadNotify 调用分支
if (convType == ConvType.GROUP) {
    // GROUP：仅推自己其他端 —— user_ids=[reader], exclude_conn_id=ctx.getConnId()
    readReceiptPusher.pushReadNotify(ctx, List.of(ctx.getUserId()), notify);
} else {
    // C2C：推对端 + 自己其他端（原逻辑，但补 deleted_at 过滤）
    readReceiptPusher.pushReadNotify(ctx, getMemberUserIds(request.getConvId()), notify);
}

// getMemberUserIds 补过滤
private List<Long> getMemberUserIds(long conversationId) {
    return memberMapper.selectList(Wrappers.lambdaQuery(ConversationMemberEntity.class)
            .eq(ConversationMemberEntity::getConvId, conversationId)
            .isNull(ConversationMemberEntity::getDeletedAt)   // ← 必加
            .orderByAsc(ConversationMemberEntity::getUserId))
        ...
}
```

---

## 建议项（B，不阻塞，下一 PR 可带上）

### B1 — PullMsgs gRPC 历史拉取返回 conv_type = UNSPECIFIED

`MessageQueryService.pullForPaging()` 走 `CONV_TYPE_UNSPECIFIED`：

```java
return range(request.getConvId(), request.getBeginSeq(), endSeq, limit,
    ConvType.CONV_TYPE_UNSPECIFIED);  // ← GROUP 消息历史拉取时 conv_type 错误
```

SYNC 路径（`buildSync`）正确：经 ConvInfo 取 `conv.getType()` ✅。但内部 gRPC `PullMsgs` 调用方拿到的 `MsgPush.conv_type = 0`，未来客户端用 REST/gRPC 拉历史时会解析到错误类型。

修复：`PullMsgsReq` 加 `conv_type` 字段，或在 `MessageRpc` impl 内先查 ConvInfo 再传给 `pullForPaging`（一行改动）。

### B2 — GroupServiceTest removeMember 正常路径无测试

`GroupServiceTest` 中 `removeMember` 只有 `rejectsRemovingOwner` 这一个负例，没有覆盖正常踢成员路径：verify `groupMemberMapper.delete` 被调用、`conversationMemberMapper.update` 写入 `deleted_at`、notification 写入 outbox。建议补一个正例。

### B3 — GroupService.removeMember 更新条件用 lambdaQuery 而非 lambdaUpdate

```java
conversationMemberMapper.update(patch, Wrappers.lambdaQuery(GroupConversationMemberEntity.class)
    .eq(...).eq(...));
```

MyBatis-Plus `update(entity, Wrapper)` 接受 QueryWrapper 作为 WHERE 条件在功能上可用，但语义上应用 `lambdaUpdate`——保持与 `upsertConversationMember` 同一风格，也避免未来升级 MP 版本时行为变更。

---

## 通过项（✅）

| 项 | 验证结果 |
|---|---|
| 依赖铁律 | ✅ im-group-service pom.xml 只依赖 im-common + im-proto-java，无业务模块依赖；enforcer plugin 已绑定 |
| 大事务完整性 | ✅ createGroup/addMembers/removeMember/rename 均 @Transactional；group + group_member + conversation + conversation_member + message + outbox 在同一事务内 |
| seq 分配 | ✅ GroupConversationMapper.incrementMaxSeq = `UPDATE SET max_seq=max_seq+1`，与 D26 路径一致；@Transactional 持行锁，UPDATE+SELECT 串行化正确 |
| 群人数上限 | ✅ maxGroupMembers() 读 tenant_config，缺省 500（D13）；createGroup/addMembers 两处都检查 |
| NotificationContent 系统消息 | ✅ 建群/加人/踢人/改名均落 NotificationContent，event_type 与 protocol.md 附录 A 注册表对齐 |
| 新成员 read_seq 初始化 | ✅ addMembers 时 upsertConversationMember 传 currentMaxSeq(convId)，新成员从当前水位开始，不会被历史消息计入未读 |
| GrpcConversationResolver | ✅ GROUP_ID target 不再抛异常；CONV_ID target 仅 C2C 做 relation check |
| MessageAssembler/QueryService | ✅ GROUP conv_type 正确透传到 MsgPush |
| 端到端冒烟 | ✅ AbstractImServerMvpSmokeTest 补 GROUP 建群+发消息+SYNC 路径，验证 group.created notification seq=1、text msg seq=2 |
| 租户隔离 | ✅ 所有写入首行取 TenantContext.requiredTenantId()；entity 均设 tenantId |
| outbox 路由 | ✅ routing_key = `msg.saved.{tenantId}`，与 message-service 一致 |

---

## 整改清单

1. **S1（必须）** `ReadReceiptService`：按 conv_type 分支推送，GROUP 仅推自己其他端；`getMemberUserIds` 补 `isNull(deletedAt)`。

整改后重新提交，S1 验证通过即可合并（B1~B3 可带上或下一 PR 跟踪）。

---

## 附：整体质量评价

群聊核心实现干净：事务原子性、seq 分配、成员限制、NotificationContent 事件化路径均正确，与文档约定吻合。测试有覆盖核心正/负例。唯一的严重问题是未兑现 PR-7 明确挂账的已读扇出修复，这不是遗漏而是挂账未消。修完 S1 本 PR 质量与前几轮持平。
