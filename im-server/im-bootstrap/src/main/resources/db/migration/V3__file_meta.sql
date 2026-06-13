CREATE TABLE IF NOT EXISTS file_meta (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  uploader_id   BIGINT       NOT NULL,
  object_key    VARCHAR(255) NOT NULL COMMENT '{tenant}/{yyyymm}/{uuid}',
  mime          VARCHAR(64)  NOT NULL,
  size          BIGINT       NOT NULL,
  duration_ms   INT          NULL COMMENT '语音',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2审核违规',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_object_key (tenant_id, object_key),
  KEY idx_tenant_uploader (tenant_id, uploader_id)
) ENGINE=InnoDB COMMENT='文件元数据(D10)';
