# 代码审查清单（实现者自查 + Claude 审查共用）

> 提 PR 前逐项自查。审查顺序与 im-server-design.md §8 实现顺序对应。
> 「拒收线」= 任一不满足直接打回。

## 0. 全局拒收线（每个 PR 都查）

- [ ] 业务模块 POM 没有出现其他业务模块坐标（enforcer 规则在且生效）
- [ ] 所有 SQL 经 MyBatis 租户拦截器（禁止绕过 mapper 裸写 SQL/JdbcTemplate；`outbox` 作为全局基础设施轮询表是已批准例外，见 architecture §7）
- [ ] 应用代码**没有手写 tenant_id 过滤条件**（那是拦截器的事；手写=拦截器失效的信号）
- [ ] 新增/变更表结构：同步改 docs/architecture.md §7 + Flyway 迁移（不改 compose 的 01-schema.sql）
- [ ] 改 proto：Rust+Java 两端编译通过；字段号只增不删改；frame.proto 的 Cmd↔body 注释同步更新（D19/协议纪律 §6）
- [ ] gRPC/MQ/日志带 tenant_id + trace_id（§13.4）；异常用 ImException+ErrorCode，禁止裸抛 RuntimeException
- [ ] 无 `new Thread()`/自建平台线程池处理请求（虚拟线程由框架管理，D2）；上下文传递统一用 im-common 的 TenantContext/TraceContext（ThreadLocal+finally，D25），禁止自建；**禁止 --enable-preview**
- [ ] 密码/密钥不进代码与日志；输入参数有校验（长度/格式/枚举值）

## 1. 骨架 PR（design §8-1）

- [ ] 九模块结构与 im-server-design.md §2 完全一致；根包 com.im
- [ ] im-proto-java 生成通过且唯一（其他模块无 protobuf 插件）
- [ ] 只有 im-bootstrap 可执行；`mvn verify` 全绿；bootstrap 空跑成功 + /actuator/health 可访问

## 2. im-common PR（§8-2）

- [ ] TenantContext 用 ThreadLocal+finally 清理（D25）；拦截器对 SELECT/UPDATE/DELETE 注入 where、INSERT 注入列，**有单测证明**（含子查询/join 场景）
- [ ] 缺失 tenant 上下文时拦截器**抛异常**而非放行（fail-closed）
- [ ] Snowflake workerId Redis 租约（SETNX+TTL+续期），冲突时启动失败
- [ ] Outbox：同事务写入验证（事务回滚则 outbox 无残留）；poller confirm 后才删；投失败退避重试有上限告警
- [ ] 启动自检按 design §6 顺序 fail-fast

## 3. user 模块 PR（§8-3）

- [ ] bcrypt；JWT 2h+refresh 30d；VerifyToken 校验 token_ver（互踢 D11 的失效机制）
- [ ] 登录失败不区分"账号不存在/密码错"（防枚举）；登录接口限流
- [ ] account 列兼容手机号/用户名（D22）；user_type/verified_type 枚举对齐 proto

## 4. message+conversation PR（§8-4）★ 核心链路，重点审

- [ ] 发送链路顺序严格对齐 architecture §5.2：幂等检查→关系校验(黑名单/好友开关 D17)→DB 事务内分配 seq→同事务(消息+会话+outbox)→ack
- [ ] client_msg_id 幂等：Redis SETNX + DB 唯一键双保险，重复请求返回**原结果**而非报错
- [ ] seq 无空洞：并发发送压测（100 并发同会话）后 max(seq)=count(*)
- [ ] SYNC_REQ：缺口区间正确、分页 has_more 正确、新设备(local=0)全量路径、full_sync 阈值
- [ ] Redis 最近消息缓存与 DB 一致（缓存 miss 回源）；read_seq 单调不回退
- [ ] CmdHandler 注册的 cmd 与 frame.proto 注释一致；解码失败返回 ERROR 帧不崩路由器

## 5. push 模块 PR（§8-5）

- [ ] 按 gw_instance 分组批量投 PushEnvelope（§5.7 扇出优化），禁止逐用户逐条投
- [ ] 互踢顺序对齐 protocol/architecture §5.5：token_ver++ → KICK → 断连清路由 → 新连接注册
- [ ] OnDisconnected 清路由有幂等保护；路由表 TTL 与心跳续期匹配（30s 心跳 → TTL≥90s）

## 6. 收尾 PR（§8-6：已读/群/文件/审核）

- [ ] 群操作产生 NotificationContent 系统消息（事件注册表 protocol.md 附录 A），不另造通知通道
- [ ] 群成员数校验读 tenant_config.max_group_members（D13），非硬编码 500
- [ ] presign：MIME 白名单/大小上限/5min 有效期/租户配额（§13.8）；object_key 格式 {tenant}/{yyyymm}/{uuid}
- [ ] 审核：词库热加载（word.reload 事件）；违规走 RevokeMsg 复用撤回；moderation_log 留证含 original_content

## 7. 审查产出格式（Claude 用）

按 严重(必须改)/建议(可商量)/疑问(需 Jade 或实现者澄清) 三级输出；
与设计文档冲突的实现一律「严重」，除非随 PR 附文档修改提案。
