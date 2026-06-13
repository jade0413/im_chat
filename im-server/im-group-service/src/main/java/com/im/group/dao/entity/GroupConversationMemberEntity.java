package com.im.group.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("conversation_member")
public class GroupConversationMemberEntity {

  @TableField("conv_id")
  private Long convId;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("user_id")
  private Long userId;

  @TableField("read_seq")
  private Long readSeq;

  @TableField("pinned")
  private Integer pinned;

  @TableField("muted")
  private Integer muted;

  @TableField("deleted_at")
  private LocalDateTime deletedAt;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
