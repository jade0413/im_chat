package com.im.conversation.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_conv_event")
public class UserConvEventEntity {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("user_id")
  private Long userId;

  @TableField("conv_id")
  private Long convId;

  @TableField("event_version")
  private Long eventVersion;

  @TableField("event_type")
  private String eventType;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
