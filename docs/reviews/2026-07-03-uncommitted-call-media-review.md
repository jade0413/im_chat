# 未提交代码审查：通话扩展（群通话/视频）+ 富媒体链路

> 日期：2026-07-03 ｜ 范围：工作区全部未提交改动（88 文件，+7165/-534），重点 im-app 与 im-server
> 涉及功能块：① 通话扩展为群通话+视频（call.proto/im-call-service/CallEngine/CallPage）② 视频消息+转码（file-service ffmpeg/V14/V15）③ 大群媒体轻推送（MsgPush.content_omitted）④ 媒体审核 provider ⑤ 历史接口媒体字段 ⑥ 秒传（sha256）

## 0. 总评

**不是垃圾补丁。** 实现质量整体在线：Lua 原子状态迁移、群成员忙线语义（邀请不锁全群、接听才占线）、幂等、trickle ICE 候选缓存、offer 方向约定（老成员→新成员，天然无 glare）、四端摄像头权限、V16 迁移与实体对齐、秒传按 (tenant, uploader, sha256) 隔离防跨用户探测、ffmpeg 走 ProcessBuilder 参数数组（无 shell 注入）、配套了 Java + Dart 测试。方向和手感都对。

**但有 1 个 P0 设计缺口、4 个 P1 缺陷、1 个流程违规**，提交前应处理。

## 1. 流程违规（先说规矩）

D45 白纸黑字："仅 1v1 语音；视频/群通话（SFU）二阶段挂 Open Questions"。本次直接实现了 **视频 + mesh 群通话**，未提 Open Question、未修订 docs/call-service-design.md、CLAUDE.md 无对应决策条目（未提交 diff 里的 D46~D49 是钱包/朋友圈等，与此无关）。这是 D26 之后第二次"代码先行"。

技术方向本身可接受（小规模 mesh 不上 SFU 是合理 MVP），**建议追认而非退回**：修订 call-service-design.md（群通话=mesh、上限、忙线/退出语义）+ CLAUDE.md 增补决策条目，与代码同一提交。

## 2. P0：群通话无人数上限——扇出与 mesh 双重炸弹

`CallConversationClient.resolveGroupTarget` 原样返回全群成员（群上限 500，D13）；`CallProperties` 无任何人数上限配置；`inviteGroup` 对每个成员**逐个** `push.notifyUsers`（为了按人签发 TURN 凭证）。

后果：对 500 人群发起通话 = 500 次串行 push RPC + 全群全端 need_ack=true 振铃（每端一个网关 pending ack）+ 若干人接听后 N×(N-1)/2 路 P2P 音视频——mesh 超过 6~9 人媒体面就崩，而信令面在拨出瞬间就是一次自制的推送风暴。**一行恶意/误触即可打击全服**。

修法（最小）：`CallProperties` 加 `groupCallMaxMembers`（建议默认 9，对齐微信）；`inviteGroup` 在 resolve 后 `memberIds.size() > max` 直接返回 `VALIDATION_FAILED`（或新错误码 CALL_GROUP_TOO_LARGE=7005）。同时 `invited_ids` CSV 存 Redis hash 也因此有界。

## 3. P1 缺陷

### P1-a 并发同时接听 → 成员间互不建连（听不到对方）

`CallService.answer`：accept Lua 成功后用的是**本地快照** `session.withAcceptedCallee(自己)`（基于 accept **之前** `find()` 的数据）构造 `acceptedNotifyTargets`。A、B 几乎同时接听时（群通话全员同时点接听是常态），B 的 find 若发生在 A 写入 Redis 之前，B 的快照 active 里没有 A → ACCEPTED 通知漏发 A → A 不向 B 发 offer → **A、B 互相无媒体**，且无自愈路径。

修法：accept 成功后 `sessions.find()` 重读再构造通知目标（`leaveGroupCall` 已经是这个写法，answer 漏了）；或让 ACCEPT Lua 直接返回接后完整 `active_ids`。

### P1-b 群通话振铃不止

首个成员接听时 ACCEPT Lua `ZREM` deadline → sweeper 不再管这通电话；其余未接成员**没有任何超时来源**：服务端不发 TIMEOUT，客户端 `CallEngine` 的 incoming 态也没有本地振铃超时定时器。通话打 30 分钟，未接成员响 30 分钟（除非主叫挂断走 `invitedUserIds` 全量 HANGUP 通知）。

修法（客户端一行价值最高）：CallEngine incoming 态起 60s 本地定时器 → 超时自动 `_finish(timeout)`（1v1 也因此多一层兜底）。服务端 per-member deadline 列二阶段。

### P1-c 群通话拒接不停自己其他端的铃

`answer` 的 group reject 分支直接 `return ack(OK)`，没调 `stopOtherCalleeEnds`。1v1 拒接会给其他端推 ANSWERED_ELSEWHERE 停铃，群通话该端拒接后，同账号其他设备继续响。修法：group reject 也调 `stopOtherCalleeEnds(ctx, session)`。

### P1-d ffmpeg 管道死锁 → 转码假超时

`ProcessFfmpegRunner`：`redirectErrorStream(true)` 后**无人读取 stdout**。ffmpeg 把进度/日志写 stderr，输出超过管道缓冲（~64KB，转码长视频必然超）后 ffmpeg 阻塞在 write → `waitFor` 干等到 timeout → destroyForcibly。表象是"长视频总是转码超时"，实际是经典管道死锁。

修法：`.redirectOutput(ProcessBuilder.Redirect.DISCARD)`（连同 errorStream 合并后丢弃），或起线程消费流。

## 4. P2 及以下

- **轻推送双份编码**：`MsgPush.content_omitted/omitted_reason` 正式字段与 `ext["__push_mode"/"__omitted_reason"/"__abstract"]` 魔法键并存——补丁气味。proto 字段是真相，建议只保留 `ext["__abstract"]`（摘要确实无处安放），删掉另两个冗余键。
- **阈值语义**：`largeGroupMediaLightPushThreshold` 默认 500 = 群上限，即默认仅满员群触发，功能默认近似关闭。若有意如此（D13：500 内无需特殊路径），在 application.yml 注释里写明"默认不生效，调小才启用"。
- **群通话主叫挂断 = 整场结束**：微信语义是"发起者退出通话继续"。当前实现（caller hangup → full end）是有效简化，但要写进设计文档，客户端文案应显示"通话已结束"而非"对方已挂断"。
- **resolveGroupTarget 的成员资格**：依赖 `ResolveConv` 对非成员返回 NOT_CONV_MEMBER。请在联调中验证非成员对群 CALL_INVITE 确实被拒（若 ResolveConv 存在"隐式建会话"路径则是越权漏洞）。
- **`CallService.ack(...)` 群通话若干分支未带 groupId**（answer 校验失败等路径回 `groupId=0`），客户端靠 `_state.groupId` 兜底，无实害，顺手统一。
- **下载接口授权粒度（存量，非本次引入）**：`presignDownload` 仅校验租户前缀 + CONFIRMED，同租户内知道 objectKey 即可下载。objectKey 含 UUID 难枚举，MVP 可接受；建议挂 Open Question（会话成员级授权）。
- 历史接口媒体字段、秒传（V14）、HttpMediaModerationProvider（HMAC 签名+超时）设计合格，无发现问题。

## 5. 安全面结论

未发现注入类漏洞：ffmpeg 参数数组无 shell；SIGNAL payload 服务端不解析且受帧 64KB 上限约束；SIGNAL/ANSWER 均校验参与者身份；TURN 凭证按用户签发带时效；秒传按 uploader 隔离。P0 的群通话扇出是本次唯一可被滥用为 DoS 的面。

## 6. 建议处理顺序（提交前）

1. P0 上限 + P1-a 重读快照 + P1-c 停铃：im-call-service 三处小改
2. P1-b 客户端振铃超时：call_engine.dart 一个 Timer
3. P1-d ffmpeg DISCARD：一行
4. 文档追认：call-service-design.md 修订 + CLAUDE.md 决策条目（连同 D46~D49 一起提交）
5. 老规矩：`mvn test` + `flutter analyze/test` + proto 三端重生成后再提交
