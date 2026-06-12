package com.im.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("user_blacklist")
public class UserBlacklistEntity {

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("user_id")
  private Long userId;

  @TableField("blocked_user_id")
  private Long blockedUserId;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
