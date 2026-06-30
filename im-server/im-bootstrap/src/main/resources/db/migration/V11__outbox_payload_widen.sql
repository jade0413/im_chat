-- V11: 放大 outbox.payload，避免近上限大消息因 payload 超列宽导致整条发送回滚（R-1）
-- 关联：评审 2026-06-29 R-1
--
-- 背景：message.content 上限 8KB，但 MsgSavedEvent 还要包冗余 sender 昵称/头像 + envelope；
--   原 outbox.payload 为 VARBINARY(16384)，极端大 CUSTOM 消息逼近 16KB 时 OutboxWriter 校验失败，
--   与消息落库同事务 → 整条发送回滚（消息发不出去）。
-- 处理：放大为 MEDIUMBLOB（16MB 上限，行外存储不占主行宽），代码侧 MAX_PAYLOAD_BYTES 同步放宽。

ALTER TABLE outbox
  MODIFY COLUMN payload MEDIUMBLOB NOT NULL COMMENT 'events.proto pb bytes';
