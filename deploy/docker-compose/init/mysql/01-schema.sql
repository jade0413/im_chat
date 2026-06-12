-- ============================================================
-- im-project 初始 Schema（与 docs/architecture.md §7 同步维护）
-- 约定：所有业务表第一业务列 = tenant_id；联合索引以 tenant_id 打头
-- MySQL 首次启动自动执行；后续演进改用 Flyway 迁移（V2__ 起）
-- ============================================================
SET NAMES utf8mb4;
USE im;

CREATE TABLE tenant (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(128) NOT NULL,
  plan          VARCHAR(32)  NOT NULL DEFAULT 'free',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2停用',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='租户';

CREATE TABLE tenant_config (
  tenant_id          BIGINT NOT NULL,
  multi_device_policy JSON  NULL COMMENT 'D11 三平台类各限几台,默认{"MOBILE":1,"DESKTOP":1,"WEB":1}',
  max_group_members  INT    NOT NULL DEFAULT 500 COMMENT 'D13',
  friend_required    TINYINT NOT NULL DEFAULT 0 COMMENT 'D17 好友制开关',
  msg_retention_days INT    NOT NULL DEFAULT 365 COMMENT '§13.5 保留策略',
  plan_features      JSON   NULL,
  PRIMARY KEY (tenant_id)
) ENGINE=InnoDB COMMENT='租户配置(套餐参数)';

CREATE TABLE `user` (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  account       VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(128) NULL COMMENT 'bcrypt; visitor 为空',
  nickname      VARCHAR(64)  NOT NULL DEFAULT '',
  avatar        VARCHAR(255) NOT NULL DEFAULT '',
  user_type     TINYINT      NOT NULL DEFAULT 1 COMMENT '1member 2agent 3visitor',
  verified_type TINYINT      NOT NULL DEFAULT 0 COMMENT 'D12 0无 1个人 2企业 3官方人员',
  device_fp     VARCHAR(128) NULL COMMENT '访客设备指纹',
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 2禁言 3封号',
  mute_until    DATETIME     NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_account (tenant_id, account),
  KEY idx_tenant_devicefp (tenant_id, device_fp)
) ENGINE=InnoDB COMMENT='用户(member/agent/visitor)';

CREATE TABLE user_certification (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  verified_type TINYINT      NOT NULL,
  cert_name     VARCHAR(128) NOT NULL,
  cert_material JSON         NULL,
  audit_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  audited_by    BIGINT       NULL,
  audited_at    DATETIME     NULL,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB COMMENT='认证资料(蓝V)';

CREATE TABLE conversation (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  type          TINYINT      NOT NULL COMMENT '1c2c 2group 3cs 4system',
  c2c_key       VARCHAR(64)  NULL COMMENT 'c2c防重: 小uid_大uid',
  group_id      BIGINT       NULL,
  max_seq       BIGINT       NOT NULL DEFAULT 0 COMMENT 'DB事务内会话级seq水位(D26)',
  last_msg_abstract VARCHAR(255) NOT NULL DEFAULT '',
  last_msg_time DATETIME     NULL,
  cs_status     TINYINT      NULL COMMENT '客服预留 1open 2assigned 3resolved',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_c2c (tenant_id, c2c_key),
  KEY idx_tenant_group (tenant_id, group_id)
) ENGINE=InnoDB COMMENT='会话(一切皆topic)';

CREATE TABLE conversation_member (
  conv_id       BIGINT   NOT NULL,
  tenant_id     BIGINT   NOT NULL,
  user_id       BIGINT   NOT NULL,
  read_seq      BIGINT   NOT NULL DEFAULT 0 COMMENT '未读=conversation.max_seq-read_seq',
  pinned        TINYINT  NOT NULL DEFAULT 0,
  muted         TINYINT  NOT NULL DEFAULT 0,
  deleted_at    DATETIME NULL COMMENT '用户删除会话(软删,新消息复活)',
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (conv_id, user_id),
  KEY idx_tenant_user (tenant_id, user_id) -- 按人拉会话列表
) ENGINE=InnoDB COMMENT='会话成员(收件箱指针)';

CREATE TABLE message (
  id            BIGINT       NOT NULL COMMENT 'server_msg_id, snowflake',
  tenant_id     BIGINT       NOT NULL,
  conversation_id BIGINT     NOT NULL,
  seq           BIGINT       NOT NULL COMMENT '会话级单调递增(D7)',
  sender_id     BIGINT       NOT NULL,
  client_msg_id VARCHAR(64)  NOT NULL COMMENT '幂等(D9)',
  msg_type      TINYINT      NOT NULL COMMENT '1text 2image 3voice 4file 10notification 20custom',
  content       VARBINARY(8192) NOT NULL COMMENT 'MsgContent pb bytes(D20)',
  abstract      VARCHAR(255) NOT NULL DEFAULT '' COMMENT '冗余文本摘要(列表/审核)',
  ext           JSON         NULL,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1normal 2revoked',
  revoke_reason TINYINT      NULL,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_conv_seq (tenant_id, conversation_id, seq), -- 同步拉取主路径
  UNIQUE KEY uk_client_msg (tenant_id, client_msg_id)
) ENGINE=InnoDB COMMENT='消息(每会话一份,收件箱按seq引用)';

CREATE TABLE group_info (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  name          VARCHAR(128) NOT NULL,
  owner_id      BIGINT       NOT NULL,
  avatar        VARCHAR(255) NOT NULL DEFAULT '',
  member_count  INT          NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 1,
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='群';

CREATE TABLE group_member (
  group_id      BIGINT   NOT NULL,
  tenant_id     BIGINT   NOT NULL,
  user_id       BIGINT   NOT NULL,
  role          TINYINT  NOT NULL DEFAULT 1 COMMENT '1member 2admin 3owner',
  joined_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (group_id, user_id),
  KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB COMMENT='群成员';

CREATE TABLE file_meta (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  uploader_id   BIGINT       NOT NULL,
  object_key    VARCHAR(255) NOT NULL COMMENT '{tenant}/{yyyymm}/{uuid}',
  mime          VARCHAR(64)  NOT NULL,
  size          BIGINT       NOT NULL,
  duration_ms   INT          NULL COMMENT '语音',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待确认 1已确认 2审核违规',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_object_key (object_key),
  KEY idx_tenant_uploader (tenant_id, uploader_id)
) ENGINE=InnoDB COMMENT='文件元数据(D10)';

CREATE TABLE user_blacklist (
  tenant_id     BIGINT   NOT NULL,
  user_id       BIGINT   NOT NULL,
  blocked_user_id BIGINT NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, user_id, blocked_user_id)
) ENGINE=InnoDB COMMENT='黑名单(D17,BLOCKED_BY_PEER)';

CREATE TABLE friend (
  tenant_id     BIGINT      NOT NULL,
  user_id       BIGINT      NOT NULL,
  friend_user_id BIGINT     NOT NULL,
  remark        VARCHAR(64) NOT NULL DEFAULT '',
  status        TINYINT     NOT NULL DEFAULT 1,
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tenant_id, user_id, friend_user_id)
) ENGINE=InnoDB COMMENT='好友(二阶段,friend_required开关用)';

CREATE TABLE sensitive_word (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT       NULL COMMENT 'NULL=平台级词库',
  word          VARCHAR(64)  NOT NULL,
  category      VARCHAR(32)  NOT NULL DEFAULT 'general',
  action        TINYINT      NOT NULL DEFAULT 1 COMMENT '1revoke 2replace 3flag',
  enabled       TINYINT      NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='敏感词(§9.1,改动发word.reload)';

CREATE TABLE moderation_log (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  message_id    BIGINT       NOT NULL,
  provider      VARCHAR(32)  NOT NULL COMMENT 'dfa/aliyun/...',
  category      VARCHAR(32)  NOT NULL,
  score         DECIMAL(5,4) NULL,
  action_taken  VARCHAR(32)  NOT NULL,
  original_content TEXT      NULL COMMENT '留证,仅审计可查',
  audit_status  TINYINT      NOT NULL DEFAULT 0 COMMENT '0auto 1复核中 2维持 3误杀回滚',
  created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant_msg (tenant_id, message_id)
) ENGINE=InnoDB COMMENT='审核日志(§9.1)';

CREATE TABLE outbox (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  tenant_id     BIGINT       NOT NULL,
  event_type    VARCHAR(64)  NOT NULL COMMENT 'msg.saved/msg.revoked/...',
  routing_key   VARCHAR(128) NOT NULL,
  payload       VARBINARY(16384) NOT NULL COMMENT 'events.proto pb bytes',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0待投 1投失败重试中 2死亡需人工处理',
  retry_count   INT          NOT NULL DEFAULT 0,
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_status_created (status, created_at) -- 轮询扫描路径
) ENGINE=InnoDB COMMENT='事务发件箱(D18,投成即删)';

-- 开发种子数据：默认租户
INSERT INTO tenant (id, name, plan) VALUES (1, 'dev-tenant', 'dev');
INSERT INTO tenant_config (tenant_id) VALUES (1);
