package com.im.cs.visitor.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 访客 localStorage token ↔ user_id 映射。
 * 详见 docs/cs-service-design.md §2.2
 */
@Getter
@Setter
@TableName("visitor_profile")
public class VisitorProfileEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  /** 客户端 localStorage 存储的 UUID，跨 session 持久标识访客 */
  @TableField("visitor_token")
  private String visitorToken;

  /** 对应 user 表中 user_type=VISITOR 的记录 */
  @TableField("user_id")
  private Long userId;

  /** "访客XXXX" 格式，生成后不可修改 */
  @TableField("display_name")
  private String displayName;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
