CREATE TABLE user_conv_version (
  tenant_id          BIGINT      NOT NULL,
  user_id            BIGINT      NOT NULL,
  conv_list_version  BIGINT      NOT NULL DEFAULT 0,
  updated_at         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (tenant_id, user_id)
) ENGINE=InnoDB COMMENT='每用户会话列表版本水位';

CREATE TABLE user_conv_event (
  id             BIGINT      NOT NULL AUTO_INCREMENT,
  tenant_id      BIGINT      NOT NULL,
  user_id        BIGINT      NOT NULL,
  conv_id        BIGINT      NOT NULL,
  event_version  BIGINT      NOT NULL,
  event_type     VARCHAR(32) NOT NULL COMMENT 'created/updated/removed',
  created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_user_version (tenant_id, user_id, event_version),
  KEY idx_tenant_user_id (tenant_id, user_id, id),
  KEY idx_tenant_user_conv (tenant_id, user_id, conv_id)
) ENGINE=InnoDB COMMENT='每用户会话列表变更流水';
