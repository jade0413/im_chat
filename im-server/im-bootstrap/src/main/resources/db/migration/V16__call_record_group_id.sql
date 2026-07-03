ALTER TABLE call_record
  ADD COLUMN group_id BIGINT NULL AFTER callee_user_id,
  ADD KEY idx_group_call (tenant_id, group_id, created_at);
