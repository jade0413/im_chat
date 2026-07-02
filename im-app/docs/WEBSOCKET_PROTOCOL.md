# WebSocket 协议骨架

## 事实来源

协议事实来源是仓库根目录 `im-proto`。客户端通过 `tool/generate_proto.sh` 生成 Dart protobuf 文件。网关遵循 `cmd + bytes` 透传模式，Flutter 不应手写与 proto 冲突的业务结构。

## Frame

- `version`：当前客户端常量为 `1`。
- `req_id`：客户端请求自增；服务端推送需要 ACK 时携带非 0 `req_id`。
- `cmd`：命令类型，例如 AUTH、PING、PONG、MSG_SEND、MSG_PUSH、SYNC_REQ。
- `body`：业务 protobuf bytes。

## 连接流程

1. 建立 WebSocket 二进制连接。
2. 发送 `AUTH`，包含 token、tenantId、deviceId、platform、appVersion。
3. 等待 `AUTH_ACK`。失败时按 token 刷新三态处理：成功重连、凭证失效登出、网络错误退避。
4. AUTH 成功后启动心跳，发送 `SYNC_REQ`，并恢复 Outbox。

## 心跳

- 服务端 `AUTH_ACK.heartbeatIntervalSec` 指定心跳间隔，缺省 30 秒。
- 客户端周期发送 `PING`。
- 2.5 个心跳周期无任何下行帧视为半死链，主动断开重连。

## ACK

- 发送 ACK：`MSG_SEND_ACK` 回填 serverMsgId、seq、serverTime 和错误码。
- 接收 ACK：`MSG_RECV_ACK` 在本地事务成功后发送，回带网关下行 `req_id`。
- 服务端推送 ACK 超时不做服务端重推，客户端断线重连后用 SYNC 补齐。

