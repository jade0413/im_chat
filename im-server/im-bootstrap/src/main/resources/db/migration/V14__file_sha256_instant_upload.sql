ALTER TABLE file_meta
  ADD COLUMN sha256 CHAR(64) NULL COMMENT 'client-calculated sha256 for same-uploader instant upload' AFTER duration_ms,
  ADD KEY idx_tenant_uploader_sha256 (tenant_id, uploader_id, sha256, size, mime, status);
