-- V7: 客服内部备注
-- 备注只给坐席侧协作/交接/质检使用，不进入 message/outbox，不推送给访客。

CREATE TABLE cs_internal_note (
  id         BIGINT      NOT NULL COMMENT 'snowflake',
  tenant_id  BIGINT      NOT NULL,
  conv_id    BIGINT      NOT NULL COMMENT 'CS conversation.id',
  agent_id   BIGINT      NOT NULL COMMENT '备注坐席 user_id',
  content    TEXT        NOT NULL COMMENT '内部备注正文，仅坐席可见',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_conv_created (tenant_id, conv_id, created_at),
  KEY idx_tenant_agent_created (tenant_id, agent_id, created_at)
) ENGINE=InnoDB COMMENT='客服内部备注：仅坐席可见';
