-- V6: 客服会话（CS Session）基础 Schema
-- 关联决策：D29~D37 | 详见 docs/cs-service-design.md

-- -------------------------------------------------------
-- 1. user 表新增坐席相关列
--    - is_agent:     0=普通用户  1=坐席（与 user_type 正交，同一账号可同时是 IM 用户和坐席）
--    - agent_status: 0=offline  1=online  2=busy
--    注：user_type=2(AGENT) 为 proto 预留枚举值，业务层改用 is_agent 标志，不再使用 user_type=2
-- -------------------------------------------------------
ALTER TABLE `user`
  ADD COLUMN is_agent     TINYINT(1) NOT NULL DEFAULT 0 COMMENT '0普通 1坐席权限',
  ADD COLUMN agent_status TINYINT    NOT NULL DEFAULT 0 COMMENT '0offline 1online 2busy';

-- 坐席在线状态查询索引（CS 推送路由用）
ALTER TABLE `user`
  ADD KEY idx_tenant_agent_status (tenant_id, is_agent, agent_status);

-- -------------------------------------------------------
-- 2. conversation 表新增 CS 坐席绑定列
--    - agent_id: 认领该 CS 会话的坐席 user_id，open 时为 NULL
--    注：cs_status 列已在 V1 schema 预留（1=open 2=assigned 3=resolved），此处不重建
-- -------------------------------------------------------
ALTER TABLE conversation
  ADD COLUMN agent_id BIGINT NULL COMMENT '接待坐席 user_id，cs_status=assigned/resolved 时有值';

-- CS 会话查询索引（坐席 inbox 用）
ALTER TABLE conversation
  ADD KEY idx_tenant_cs (tenant_id, type, cs_status, agent_id);

-- -------------------------------------------------------
-- 3. visitor_profile 表：localStorage visitor_token ↔ user_id
-- -------------------------------------------------------
CREATE TABLE visitor_profile (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  visitor_token VARCHAR(64)  NOT NULL COMMENT 'UUID，存于客户端 localStorage',
  user_id       BIGINT       NOT NULL COMMENT '对应 user 表 user_type=VISITOR 记录',
  display_name  VARCHAR(64)  NOT NULL COMMENT '"访客XXXX" 格式，生成后不可修改',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_token (tenant_id, visitor_token),
  KEY idx_tenant_user (tenant_id, user_id)
) ENGINE=InnoDB COMMENT='访客身份：localStorage token 到用户的映射（D29）';

-- -------------------------------------------------------
-- 4. widget_config 表：租户 CS Widget 配置
-- -------------------------------------------------------
CREATE TABLE widget_config (
  id            BIGINT       NOT NULL COMMENT 'snowflake',
  tenant_id     BIGINT       NOT NULL,
  color         VARCHAR(16)  NOT NULL DEFAULT '#1890FF'         COMMENT '品牌主色',
  welcome_msg   VARCHAR(128) NOT NULL DEFAULT '有什么可以帮您？'    COMMENT '在线欢迎语',
  offline_msg   VARCHAR(128) NOT NULL DEFAULT '我们现在不在线，留言我们会尽快回复' COMMENT '离线提示',
  display_name  VARCHAR(64)  NOT NULL DEFAULT '在线客服'           COMMENT 'Widget 头部展示名称',
  position      VARCHAR(16)  NOT NULL DEFAULT 'bottom-right'    COMMENT 'bottom-right|bottom-left',
  powered_by    TINYINT(1)   NOT NULL DEFAULT 1                 COMMENT '1=显示"由XX提供支持"徽标（免费版病毒传播）',
  created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB COMMENT='Widget 配置（D37）';
