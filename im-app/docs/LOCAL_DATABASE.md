# 本地数据库

## 技术选择

本地缓存使用 drift + SQLite，四端共享同一套 schema。所有消息、会话、用户、群组、附件、同步游标和发送队列都应落库，避免 UI 依赖内存态。

## 表结构

- `users`：用户资料缓存，包含头像、昵称、账号、认证标识和用户类型。
- `groups`：群资料缓存，包含群名、头像、群主、人数和上限。
- `conversations`：会话列表和会话级同步热路径字段，包含最新消息、未读、置顶、免打扰、草稿、`maxSeq/readSeq/syncSeq`。
- `messages`：消息展示态，以 `clientMsgId` 为主键，以 `(convId, seq)` 唯一索引做重连/同步去重。
- `message_attachments`：图片、语音、文件、视频附件状态，预留上传和下载状态。
- `sync_cursors`：同步游标，保存 `global:conv_list` 和 `conv:<convId>`。
- `outbox_messages`：发送队列，持久化待发送 `MSG_SEND` body、重试次数和下次重试时间。
- `app_kv`：兼容型 KV，保留非敏感轻量配置和旧游标 fallback。

## 事务边界

- 发送：`messages + conversations preview + outbox_messages` 同事务。
- 发送 ACK：`outbox_messages + messages + conversations + sync_cursors` 同事务。
- 接收：`messages + conversations + sync_cursors` 同事务，事务成功后才 ACK。
- 同步响应：每个 delta 的消息、会话和游标同事务。

## 去重策略

- 客户端发送阶段以 `clientMsgId` 幂等。
- 服务端同步或推送阶段以 `(convId, seq)` 幂等。
- 服务端没有回传 `clientMsgId` 时，客户端使用确定性 key：`convId:seq`。

