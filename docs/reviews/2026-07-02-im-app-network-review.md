# im-app 网络通讯与架构审查（Flutter 架构师视角）

> 日期：2026-07-02 ｜ 范围：im-app 网络层（ImSocket/ImEngine/codec/mappers）对照 im-proto、im-gateway-rust、im-server 的协议实现；整体工程分层
> 结论先行：连接状态机整体质量高（三态鉴权恢复、generation 守卫、退避+抖动、持久化 Outbox、seq 对齐去重都是对的），但存在 **2 个 P0 网络 bug（1 个跨端）** 与一批死代码骨架。**均已当日修复。**

## 1. 网络通讯问题

### A1 🔴 P0（跨端）：REVOKE_NOTIFY need_ack=true，客户端不 ack → 在线收撤回 10s 后被踢断

链路还原：`MsgRevokedEventConsumer` 以 `needAck=true` 推 REVOKE_NOTIFY → 网关分配非 0 req_id 并登记 pending ack（D28）→ **客户端只对 MSG_PUSH 回 MSG_RECV_ACK** → 10s 超时 → 网关判半死链、断连摘路由。表象是"别人一撤回消息，我就掉线重连"，极难排查。

协议真相（docs/protocol.md §3）：**need_ack=true 仅用于 MSG_PUSH**。Java 侧属实现偏离。

修复（双端）：
- Java：`MsgRevokedEventConsumer` `needAck` true→false（撤回漏收由 SYNC/历史 status=REVOKED 兜底），测试同步更新；
- Flutter（防御性泛化）：`ImEngine._ackPushIfNeeded` —— 任何服务端主动推送帧（READ_NOTIFY / REVOKE_NOTIFY / CONV_NOTIFY / 未知 cmd）只要 req_id≠0 一律回 MSG_RECV_ACK（空 items，网关只认 req_id）。今后服务端新增 need_ack 推送类型，旧客户端不会被踢。注意 MSG_SEND_ACK / SYNC_RESP / ERROR 是带客户端自己 req_id 的响应帧，不 ack（已正确区分）。

### A2 🔴 P0：socket error 后不重连——最长 ~75s"假在线"黑洞

`ImSocket` 订阅用 `cancelOnError: true`：错误后订阅已取消、**onDone 不会再触发**，而 `_onError` 在已认证状态下只打日志。结果：认证后的 socket error（如网络切换、对端 RST）留下死连接，无重连调度，只能等心跳看门狗（2.5×30s ≈ 75s）兜底，期间收发全黑。

修复：`_onError` 走与 `_onDone` 相同的清理+重连路径（generation 守卫 + `_scheduleReconnect` 幂等，重复触发安全）。

### A3 🟡 已达标项复核（不改）

- AUTH 时间戳单位秒 ✓（网关重放窗口 300s，按秒比较）
- 心跳间隔取 AUTH_ACK 下发值 ✓，存活看门狗 2.5× < 网关 idle 3× ✓
- MSG_PUSH 的 MSG_RECV_ACK 回带网关 req_id（D28）✓，req_id=0 不回 ✓
- 断线消息不丢：持久化 Outbox + `onAuthenticated` drain + client_msg_id 幂等 ✓
- 增量同步：per-conversation local max seq + 缺口检测触发 SYNC_REQ ✓（与收件箱模型对齐）
- KICK → 主动断开不重连 → 上层登出 ✓；token 刷新三态区分网络错误与真失效 ✓

### A4 🟢 记录在案（未改，挂 TODO/二阶段）

- MSG_RECV_ACK 未攒批（protocol.md 建议滚动收消息时批量确认）——低流量下无碍；
- `_drainOutbox` 无并发守卫，重连风暴下可能双 drain（服务端 client_msg_id 幂等兜底，无重复消息，仅浪费帧）；
- Outbox 缺"连接存活但服务端未 ACK"的超时扫描（代码已有 TODO）；
- AUTH 失败 code=1005（重放拒绝，本质时钟偏移）也走刷 token 流程——能自愈（重连时间戳重取）但多耗一次 refresh；
- `Env` 默认值硬编码公网 IP（103.45.65.84）——开发便利与源码泄露的权衡，建议生产构建强制 `--dart-define` 并在 CI 校验。

### A5 🟢 微优化（已改）

收帧路径两处逐帧整帧拷贝消除：`_onData` 的 `Uint8List.fromList(data)`（dart:io 二进制帧本就是 Uint8List）与 `_handleBusinessFrame` 的 body 拷贝，均改为类型判断后直用。

## 2. 架构问题

### B1 🔴 双平行骨架：32 个无引用 dart 文件，含一套与现行协议冲突的旧自定义 packet 层（已删除）

仓库里同时存在两套骨架：

- **废弃的 clean-architecture 脚手架**（全部零引用）：`lib/domain/`（9 entities + 4 usecases + 4 repository 接口）、`lib/core/network/`（`im_packet/packet_codec/packet_type/protocol_version` —— 一套**与 im-proto Frame 完全不同的自定义二进制协议**，还有平行的 `ws_client/ws_heartbeat/ws_reconnect_policy`）、`lib/data/local/db/`（旧 schema 平行 DB 层）、`lib/data/repositories/*_impl.dart`（空壳）、`lib/proto`、`lib/generated`（README 占位）。
- **实际工作实现**：`data/im_engine.dart` + `data/local/` + `data/remote/` + `core/proto/`。

危害不在体积而在误导：新开发者（或 AI 协作者）极易把 `core/network/packet_codec` 当成现行线协议去改。**已全部删除**，删后全仓 grep 零残留引用。保留 `data/repositories/{conversation,message}_repository.dart`（providers 与 UI 实际使用）。

### B2 🟡 分层微瑕（未改，实现期顺手收敛）

`features/chat/widgets/input_bar.dart` 直接 import `data/repositories/*`——UI 应经 `app/providers.dart` 注入。功能无碍，属规范项。

### B3 现行分层评价（保持）

`ImSocket`（纯连接状态机，依赖倒置 via Delegate）→ `ImEngine`（协议↔DB 协调）→ DAO Stream → UI 自动刷新，方向依赖干净；proto 经 `core/proto/codec.dart` 单一导入边界；连接层完全不 import DB/UI——这套边界与网关侧"零业务"哲学同构，是对的，保持。

## 3. 修改清单

| 端 | 文件 | 变更 |
|---|---|---|
| Java | push/consumer/MsgRevokedEventConsumer.java（+测试） | needAck true→false，对齐协议 §3 |
| Flutter | data/im_engine.dart | 新增 `_ackPushIfNeeded`（D28 泛化）；body 拷贝消除 |
| Flutter | data/remote/ws/im_socket.dart | `_onError` 走 onDone 清理+重连；收帧拷贝消除 |
| Flutter | lib/{domain,core/network,data/local/db,proto,generated}、repositories/*_impl | 删除（32 文件，零引用验证通过） |

⚠️ 验证状态：沙箱无 Flutter/JDK 工具链，未能本地跑 `flutter analyze` / `mvn test`；改动均经静态自审。请本机执行：`flutter analyze && flutter test`、im-server `mvn -pl im-push-service test`。联调回归重点：**撤回消息后在线端不应再掉线**；断 wifi 切 4G 场景重连应在秒级而非 ~75s。
