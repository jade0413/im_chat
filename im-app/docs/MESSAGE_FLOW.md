# 消息流程

## 发送流程

1. UI 调用 `MessageRepository.sendText/sendImage/sendFile/sendVoice`。
2. `ImEngine` 生成 `clientMsgId`，构造乐观消息，状态为 `pending`。
3. 本地事务写入 `messages`，更新已有会话预览，并写入 `outbox_messages`。
4. WebSocket 可用时发送 `MSG_SEND`，本地状态改为 `sending`；不可用时保留 `pending` 等待重连。
5. 收到 `MSG_SEND_ACK` 后，事务删除 outbox，回填 `serverMsgId`、`sequence`，状态改为 `sent`，并推进会话 `maxSeq/readSeq/syncSeq`。
6. ACK 失败时删除 outbox，消息状态改为 `failed`，保留 `failCode` 供 UI 展示和重试。

## 接收流程

1. `ImSocket` 收到二进制 Frame，解码后把业务帧交给 `ImEngine`。
2. `MSG_PUSH` 转为 `ChatMessage`。
3. 根据 `clientMsgId` 和 `(convId, seq)` 唯一索引去重。
4. 本地事务写入 `messages`，更新 `conversations` 最新消息、未读水位和 `sync_cursors`。
5. 如果发现 `push.seq` 与本地连续水位有缺口，立即触发 `SYNC_REQ`。
6. 本地落库成功后发送 `MSG_RECV_ACK`，回带网关分配的 `req_id`。

## 断线重连

1. socket 断开或心跳超时后进入 `reconnecting`。
2. `ReconnectBackoff` 指数退避，网络恢复或 App 回前台会立即探活。
3. 连接成功后重新发送 AUTH。
4. AUTH 成功后发送 `SYNC_REQ`，按 `conv_list_version + per conversation localSeq` 拉取离线消息。
5. 同时 drain `outbox_messages`，恢复发送断网期间堆积的消息。

## 状态机

消息状态至少包括：

- `pending`：本地已入库，尚未写出。
- `sending`：已写到网关，等待服务端 ACK。
- `sent`：服务端已落库并分配 seq。
- `delivered`：预留对端设备送达状态。
- `read`：预留对端已读状态。
- `failed`：服务端拒绝、超时或重试耗尽。
- `revoked`：本人、审核或管理员撤回。

