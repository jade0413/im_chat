-- V9: message 大表治理 —— 保留清理与管理查询所需索引
-- 关联：架构审查 2026-06-15 第五节(大表风险) / P2-5(消息保留清理)
--
-- 背景：message 单表、全租户共用、created_at 无索引。
--   1) 保留清理作业按 (tenant_id, created_at < cutoff) 批量删除，无索引会全表扫描。
--   2) 审核/管理按发送者维度查询消息，缺 (tenant_id, sender_id) 索引。
-- 说明：仅加索引，不改数据；分区(按月/按会话哈希)作为后续高阶演进，见 docs。

-- 保留清理批量删除扫描路径
ALTER TABLE message
  ADD INDEX idx_tenant_created (tenant_id, created_at);

-- 审核/管理按发送者维度查询
ALTER TABLE message
  ADD INDEX idx_tenant_sender (tenant_id, sender_id);
