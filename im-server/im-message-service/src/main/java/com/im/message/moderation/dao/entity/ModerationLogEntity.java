package com.im.message.moderation.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("moderation_log")
public class ModerationLogEntity {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("message_id")
  private Long messageId;

  @TableField("provider")
  private String provider;

  @TableField("category")
  private String category;

  @TableField("score")
  private BigDecimal score;

  @TableField("action_taken")
  private String actionTaken;

  @TableField("original_content")
  private String originalContent;

  @TableField("audit_status")
  private Integer auditStatus;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
