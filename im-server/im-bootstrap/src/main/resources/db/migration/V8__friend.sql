-- V8: 好友申请 / 关系链 + 唯一用户名
-- 关联决策：D40（申请验证流程）、D41（跨租户只留配置位）、D42（唯一 username）
-- 详见 docs/friend-service-design.md
-- 注：关系表 `friend` 已在 baseline(01-schema.sql) 建好，本迁移仅新增 friend_request 与 user 列

-- -------------------------------------------------------
-- 1. user 表新增：对外唯一标识 + 好友相关开关
--    - username:                  D42 自填、租户内唯一、可分享的"加我"标识（独立于登录用 account）
--    - friend_verify_required:    D40 加我是否需要验证，默认 1=需验证（免验证开关默认关）
--    - allow_cross_tenant_friend: D41 是否允许跨租户加好友，默认 0=仅租户内（MVP 仅留配置位，不实现）
-- -------------------------------------------------------
ALTER TABLE `user`
  ADD COLUMN username                  VARCHAR(32) NULL                COMMENT 'D42 对外唯一标识，格式 ^[a-z][a-z0-9_]{5,31}$，visitor/agent 不分配',
  ADD COLUMN friend_verify_required    TINYINT(1)  NOT NULL DEFAULT 1  COMMENT 'D40 1=加我需验证(默认) 0=免验证直接通过',
  ADD COLUMN allow_cross_tenant_friend TINYINT(1)  NOT NULL DEFAULT 0  COMMENT 'D41 0=仅租户内(默认) 1=允许跨租户(MVP 不实现)';

-- username 租户内唯一；NULL 不参与唯一约束（老用户/访客可为 NULL）
ALTER TABLE `user`
  ADD UNIQUE KEY uk_tenant_username (tenant_id, username);

-- -------------------------------------------------------
-- 2. friend_request 表：好友申请（状态唯一真相，D40）
--    每次申请插入一行 → 完整历史；同一 from→to 至多一条 pending（应用层事务内 FOR UPDATE 兜底）
-- -------------------------------------------------------
CREATE TABLE friend_request (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  from_user_id  BIGINT       NOT NULL COMMENT '申请发起方',
  to_user_id    BIGINT       NOT NULL COMMENT '申请接收方',
  note          VARCHAR(128) NOT NULL DEFAULT '' COMMENT '申请备注，对方可见',
  status        TINYINT      NOT NULL DEFAULT 0  COMMENT '0 pending / 1 accepted / 2 rejected / 3 ignored',
  auto_accepted TINYINT(1)   NOT NULL DEFAULT 0  COMMENT '1=因对方免验证自动通过',
  handle_time   DATETIME(3)  NULL                COMMENT '处理时间(accepted/rejected/ignored 时写)',
  create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  -- 仅 pending(status=0) 时有值，配合唯一键在 DB 层强制「同一 from→to 至多一条 pending」；
  -- 终态(status≠0)为 NULL，不参与唯一约束 → 拒绝/忽略后可再次申请，历史多行保留
  pending_pair  VARCHAR(48)  GENERATED ALWAYS AS
                (CASE WHEN status = 0 THEN CONCAT(from_user_id, ':', to_user_id) ELSE NULL END) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_pending (tenant_id, pending_pair),                      -- pending 去重(并发安全)
  KEY idx_to_status (tenant_id, to_user_id, status),                    -- 通知列表/待处理计数
  KEY idx_from      (tenant_id, from_user_id, create_time)              -- 我发出的申请历史
) ENGINE=InnoDB COMMENT='好友申请(D40，状态唯一真相)';
