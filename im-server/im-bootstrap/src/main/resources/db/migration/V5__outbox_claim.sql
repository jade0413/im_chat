SET @outbox_claim_owner_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'outbox'
    AND column_name = 'claim_owner'
);
SET @outbox_claim_owner_sql := IF(
  @outbox_claim_owner_exists = 0,
  'ALTER TABLE outbox ADD COLUMN claim_owner VARCHAR(64) NULL AFTER retry_count',
  'SELECT 1'
);
PREPARE outbox_claim_owner_stmt FROM @outbox_claim_owner_sql;
EXECUTE outbox_claim_owner_stmt;
DEALLOCATE PREPARE outbox_claim_owner_stmt;

SET @outbox_claim_until_exists := (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'outbox'
    AND column_name = 'claim_until'
);
SET @outbox_claim_until_sql := IF(
  @outbox_claim_until_exists = 0,
  'ALTER TABLE outbox ADD COLUMN claim_until DATETIME(3) NULL AFTER claim_owner',
  'SELECT 1'
);
PREPARE outbox_claim_until_stmt FROM @outbox_claim_until_sql;
EXECUTE outbox_claim_until_stmt;
DEALLOCATE PREPARE outbox_claim_until_stmt;

SET @outbox_claim_index_exists := (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'outbox'
    AND index_name = 'idx_claim_status'
);
SET @outbox_claim_index_sql := IF(
  @outbox_claim_index_exists = 0,
  'ALTER TABLE outbox ADD KEY idx_claim_status (status, claim_until, created_at)',
  'SELECT 1'
);
PREPARE outbox_claim_index_stmt FROM @outbox_claim_index_sql;
EXECUTE outbox_claim_index_stmt;
DEALLOCATE PREPARE outbox_claim_index_stmt;
