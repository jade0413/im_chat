-- V12: message 聚簇主键改 (tenant_id, conversation_id, seq)，id 退为二级唯一键（D-3）
-- 关联：评审 2026-06-29 D-3
--
-- 背景：原聚簇主键 = id(snowflake)，但最高频读路径是按 (tenant_id, conversation_id, seq) 区间
--   做离线增量补齐 / 历史翻页，走二级唯一索引 uk_conv_seq 命中后还要按随机 snowflake 主键回表。
-- 收益：把主读维度做成聚簇主键后，同一会话消息物理相邻 → 同步/历史读变顺序聚簇扫描、回表消失，
--   IO 局部性显著改善（表越大越明显）。
-- 不变量：id 仍是全局唯一逻辑 ID（selectById/外部引用走 uk_id），(tenant_id,client_msg_id) 幂等键不变。
--
-- ⚠️ 上线须知（务必读）：
--   1) 本语句 DROP/ADD PRIMARY KEY 会触发 InnoDB 全表重建（连同二级索引），对**已有大量数据**的表是
--      长时间锁表操作。务必在**数据量小时**（如当前 MVP 仅种子数据）执行；线上大表请改用
--      pt-online-schema-change / gh-ost 在线变更，切勿直接跑本迁移。
--   2) 原 uk_conv_seq(tenant_id,conversation_id,seq) 与新主键完全等价，迁移后由主键承担唯一性，故删除之。
--   3) Java 侧无需改动：MessageEntity 的 @TableId(id) 仅作逻辑主键映射，selectById 走 uk_id 仍唯一定位。

ALTER TABLE message
  DROP PRIMARY KEY,
  DROP INDEX uk_conv_seq,
  ADD PRIMARY KEY (tenant_id, conversation_id, seq),
  ADD UNIQUE KEY uk_id (id);
