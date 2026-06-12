# PR-1 修复任务单（交给实现者）

> 依据：docs/reviews/2026-06-13-pr1-mvp-foundation.md。Jade 已裁决 S3/S7（见 CLAUDE.md D25/D26）。
> 完成定义：下列 7 项全改 + 自查 review-checklist §0 + 集成测试全绿，然后提复审。

## 必改（对应审查报告 S1~S7）

1. **enforcer 生效**（S1）：父 POM `<build><plugins>` 绑定 maven-enforcer-plugin；
   im-bootstrap 自己的 POM 对 `ban-business-module-dependencies` 设 skip 并注释原因。
   验证：在任一业务模块加另一业务模块依赖，构建必须失败。

2. **Outbox 全租户轮询**（S2）：`outbox` 加入 TenantLineHandlerConfig.IGNORED_TABLES；
   poller 去掉 TenantContext 绑定、全表扫描；删除 OutboxProperties.tenantId。
   验证：集成测试写入 tenant=2 的 outbox 行，poller 必须投出。

3. **TenantContext 改 ThreadLocal，删除全部 --enable-preview**（S3，D25）：
   compiler/surefire 的 preview 参数一并删；runWithTenant/callWithTenant 语义不变，finally 必须 remove()。
   验证：`java -jar` 裸启动成功（不带任何 preview 参数）。

4. **补关系校验**（S4，D17）：user 模块实现 UserRpc.CheckRelation（查 user_blacklist：to 是否拉黑 from；
   friend_required 本期恒 false）+ user_blacklist 的 DAO。
   MessageSendService 在幂等检查之后、resolve 之前调用，被拉黑返回 BLOCKED_BY_PEER。

5. **SYNC_REQ 新设备全量**（S5）：conv_versions 为空时，经 ConversationRpc.ListMemberConvs
   （proto 已更新，见 rpc/internal.proto）拉该用户全部会话做 diff 源；
   ConvDelta.conv 填完整 ConvInfo（type 不准硬编码、必须含 read_seq/title/peer_user_id）。
   验证：e2e 增加"新设备空 SyncReq 拿到全部会话与消息"用例。

6. **Snowflake workerId 租约**（S6）：启动时 Redis `SETNX im:worker:{id}` + TTL + 虚拟线程续期；
   获取失败 fail-fast 退出。design §6 启动自检（B6，MySQL/Redis/MQ 连通检查）不阻塞本次复审，留后续任务。

7. **采纳 DB 行锁 seq 并补流程**（S7，D26）：保留 `ConversationProgressMapper.incrementMaxSeq` 路径，
   SequenceService 通过 `conversation.max_seq = max_seq + 1` 在 DB 事务内分配 seq；
   同步修订 AGENTS/CLAUDE/architecture/review-checklist，把 Redis INCR 降级为高吞吐预留路径。
   验证：并发同会话发送后 seq 连续无空洞，事务回滚不消耗 seq。

## 随手改（B 类中两项，约 20 行）

8. UplinkGrpcService 的 catch(Exception) 加 log.error（trace_id/cmd/tenant_id）。
9. MybatisPlusConfig 加 BlockAttackInnerInterceptor。

## 需书面答复（写在 PR 描述里）

- Q1 互踢/token_ver 在哪个 PR？ Q2 Dockerfile 哪个 PR？ Q3 图片/语音哪个 PR？

## 不要做

- 不要顺手实现 push/已读/群——保持 PR 范围聚焦，那些走下一个 PR。
- 不要改 ws/frame.proto 与 rpc/gateway.proto（网关侧冻结面）。
