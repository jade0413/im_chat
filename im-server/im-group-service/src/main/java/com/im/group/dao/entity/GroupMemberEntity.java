package com.im.group.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("group_member")
public class GroupMemberEntity {

  @TableField("group_id")
  private Long groupId;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("user_id")
  private Long userId;

  @TableField("role")
  private Integer role;

  @TableField("joined_at")
  private LocalDateTime joinedAt;
}
