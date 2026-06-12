# PR 审查报告：feat: implement im-server MVP foundation (829482f)

审查人：Claude（架构师）｜日期：2026-06-13｜依据：docs/review-checklist.md + CLAUDE.md 决策日志

## 总评

工程质量整体**良好**：模块结构与设计文档一致、九模块依赖干净、protobuf 仅 im-proto-java 生成、
幂等三层防御（DB 查→Redis SETNX→DuplicateKey 兜底回原结果）、UplinkRouter 防御完整、
事务边界正确（seq 分配+消息+会话+outbox 同事务）、42 个测试文件含 Testcontainers 集成与 e2e。
但存在 **7 个严重问题**（其中 2 个是"上线即事故"级），**本次打回**，修复后复审。

范围确认：实现了 design §8 的 1~4 步（骨架/common/user/message+conversation 的 C2C 文本链路），
push/已读/群/文件/审核不在本 PR——与排期一致，但下方 S4/S5 属于本范围内的缺失。

---

## 严重（必须修复，按危害排序）

**S1. enforcer 依赖铁律从未生效。**
`maven-enforcer-plugin` 只在父 POM `<pluginManagement>` 里，没有任何模块绑定执行——"业务模块互相禁止依赖"目前零保护（本次靠自觉没违规）。
且现配置一旦绑定会让 im-bootstrap 构建失败（它必须依赖全部业务模块）——侧面证明该规则从未运行过。
修法：父 POM `<build><plugins>` 绑定执行；bootstrap 在自己 POM 中对该 rule 设 skip（并注释原因）。

**S2. OutboxPoller 只轮询单个租户（默认 1），其他租户事件永不投递。**
`pollOnce()` 用 `properties.getTenantId()` 绑租户上下文，租户拦截器把扫描 SQL 过滤成 `tenant_id=1`。
SaaS 租户的消息推送/审核事件会静默丢失——多租户正确性事故（违反核心约定 1 的精神）。
修法：`outbox` 表加入 `TenantLineHandlerConfig.IGNORED_TABLES`（它是基础设施表，行内已带 tenant_id 列供溯源），
poller 全表扫描；删除 OutboxProperties.tenantId。

**S3. `--enable-preview` 导致生产启动即崩。**
TenantContext 用 ScopedValue（JDK21 预览特性），编译/surefire 都加了 preview 参数，但 spring-boot 运行配置没加、
也没有 Dockerfile——`java -jar` 启动时预览类拒绝加载，**测试全绿但生产起不来**。preview 还会把运行时锁死在 JDK21 同小版本。
两个修法（二选一，见"需要决策"）：a) TenantContext 改普通 ThreadLocal（虚拟线程不复用，无泄漏风险，去掉 preview）；
b) 全链路统一注入 `--enable-preview`（启动脚本+Dockerfile+文档）。审查人倾向 a。

**S4. 发送链路缺第 2 步：关系校验（D17 黑名单）未实现也未调用。**
checklist §4 要求顺序"幂等检查→**关系校验**→INCR seq→…"；现状 ErrorCode 定义了 BLOCKED_BY_PEER，
但 UserRpc.CheckRelation 无实现、MessageSendService 无调用、user_blacklist 表无 DAO。黑名单是 D17 的 MVP 必做项。

**S5. SYNC_REQ 新设备全量路径缺失。**
protocol.md §4/checklist §4：`conv_versions` 为空 = 新设备全量同步。现实现只遍历请求里的会话列表，
空请求直接返回空 SyncResp——新设备/重装用户将看到空会话列表且永远无法自愈。
修法：空列表时改查"该用户的全部 conversation_member"做 diff 源；顺带补 ConvDelta 的 read_seq/title（现在硬编码 C2C 且无 read_seq，客户端无法算未读）。

**S6. Snowflake workerId 无租约（checklist §2 明确要求）。**
`im.id.worker-id` 默认 1、无 Redis SETNX 租约与冲突 fail-fast。今天单实例没事，未来扩第二个实例时 ID 冲突是静默数据损坏。
按 design §6 实现：启动时 `SETNX im:worker:{id}` + TTL 续期，失败则退出。

**S7. seq 生成方案偏离设计文档且未走变更流程。**
文档（架构 §5.1/D7）定的是 Redis INCR + MySQL 水位兜底；实现改成了 `UPDATE conversation SET max_seq=max_seq+1` 行锁自增。
技术评价：实现**正确**（同事务无空洞、回滚一致、并发靠行锁串行化，集成测试覆盖了 100 并发），单机下甚至比 Redis 方案更稳；
代价是热会话吞吐受行锁限制、conversation 行变热点。
问题在流程：协作模式明确"偏离文档必须先提 Open Question，禁止代码先行"。
处理：见"需要决策"——审查人建议采纳实现并修订文档，但流程必须补，下不为例。

---

## 建议（应修，可与下一 PR 合并）

- B1. UplinkGrpcService 的 `catch (Exception)` 静默吞栈——加 `log.error`（带 trace_id/cmd/tenant），否则线上排障致盲。
- B2. MybatisPlusConfig 补 `BlockAttackInnerInterceptor`（防全表 UPDATE/DELETE），一行配置的保险。
- B3. 登录无限流（checklist §3）；且用户不存在时不执行 bcrypt，存在时序枚举差——补 dummy hash 比较 + 简单令牌桶。
- B4. OutboxPoller 多实例会重复投递（无 `FOR UPDATE SKIP LOCKED`）。消费侧幂等可容忍，单机 MVP 接受，留 TODO+注释。
- B5. refresh token 不轮换；token_ver/互踢未实现——确认归属 push PR 后在其 checklist 中跟踪。
- B6. 启动自检 fail-fast（design §6）未实现：MySQL/Redis/MQ/MinIO 连通 + workerId 租约，建议随 S6 一起做。
- B7. 登录失败复用 `TOKEN_INVALID` 语义不清，建议增加 `AUTH_FAILED`（proto ErrorCode 1006）。
- B8. im-message-service 内有自己的 OutboxEntity/OutboxMapper，与 im-common 重复（persist 实际用的是 common 的 OutboxWriter）——确认是残留则删。

## 疑问（需实现者/Jade 回答）

- Q1. VerifyTokenResp.kick_old 恒 false：互踢整体（token_ver/KICK 下发）计划放哪个 PR？需要排期承诺。
- Q2. 没有 im-server Dockerfile，compose `--profile app` 起不来——部署物在哪个 PR 交付？
- Q3. MessageSendService 限制只支持 TEXT：图片/语音消息（MVP 范围）预计哪个 PR？

## 需要决策（Jade 拍板后改文档）

1. **S3 取舍**：ThreadLocal（去 preview，审查人推荐）vs 保留 ScopedValue+运行时全链路 preview 参数。设计文档当初写 ScopedValue 有误判（未注明 preview 代价），文档随决策修订。
2. **S7 取舍**：采纳 DB 行锁 seq（修订架构 §5.1，Redis 方案降级为"高吞吐预留路径"）vs 改回文档的 Redis INCR。审查人推荐前者：万级规模下正确性>吞吐，且少一个 Redis 故障依赖。

## 结论

**打回**。复审门槛：S1~S6 修复 + S7 补流程（文档修订或代码改回，二选一）+ Q1~Q3 给出排期答复。
B 类不阻塞合并，但 B1/B2 强烈建议随本 PR 顺手改（共约 20 行）。
