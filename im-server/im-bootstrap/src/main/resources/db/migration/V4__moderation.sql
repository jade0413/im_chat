CREATE TABLE IF NOT EXISTS sensitive_word (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT       NULL COMMENT 'NULL=平台级词库',
  word          VARCHAR(64)  NOT NULL,
  category      VARCHAR(32)  NOT NULL DEFAULT 'general',
  action        TINYINT      NOT NULL DEFAULT 1 COMMENT '1revoke 2replace 3flag',
  enabled       TINYINT      NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='敏感词';

CREATE TABLE IF NOT EXISTS moderation_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  message_id    BIGINT       NOT NULL,
  provider      VARCHAR(32)  NOT NULL,
  category      VARCHAR(32)  NOT NULL,
  score         DECIMAL(5,4) NULL,
  action_taken  VARCHAR(32)  NOT NULL,
  original_content TEXT      NULL COMMENT '留证,仅审计可查',
  audit_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '0auto 1复核中 2维持 3误杀回滚',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant_msg (tenant_id, message_id),
  UNIQUE KEY uk_tenant_msg_provider (tenant_id, message_id, provider)
) ENGINE=InnoDB COMMENT='审核日志';
