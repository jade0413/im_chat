-- V10: 默认开启好友制。
-- 除 CS_SESSION 访客/客服会话外，普通 C2C 需要好友关系才允许打开会话或发送消息。

ALTER TABLE tenant_config
  MODIFY COLUMN friend_required TINYINT NOT NULL DEFAULT 1 COMMENT 'D17 好友制开关，默认开启';

UPDATE tenant_config
SET friend_required = 1
WHERE friend_required = 0;
