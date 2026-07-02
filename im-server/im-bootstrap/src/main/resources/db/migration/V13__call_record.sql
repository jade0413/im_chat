-- D45：1v1 实时语音通话记录（CDR）。呼叫状态机在 Redis，终态落此表。
-- result：1=completed 2=rejected 3=canceled 4=timeout
CREATE TABLE call_record (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id      BIGINT       NOT NULL,
  call_id        VARCHAR(64)  NOT NULL,
  caller_user_id BIGINT       NOT NULL,
  callee_user_id BIGINT       NOT NULL,
  media_type     TINYINT      NOT NULL DEFAULT 1,
  result         TINYINT      NOT NULL,
  connected_at   DATETIME(3)  NULL,
  ended_at       DATETIME(3)  NULL,
  duration_sec   INT          NOT NULL DEFAULT 0,
  created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_call (tenant_id, call_id),
  KEY idx_caller (tenant_id, caller_user_id, created_at),
  KEY idx_callee (tenant_id, callee_user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'D45 通话记录：状态机终态快照，质检/计费/未接列表数据源';
