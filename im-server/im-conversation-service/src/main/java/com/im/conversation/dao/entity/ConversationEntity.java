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
@TableName("conversation")
public class ConversationEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("type")
  private Integer type;

  @TableField("c2c_key")
  private String c2cKey;

  @TableField("group_id")
  private Long groupId;

  @TableField("max_seq")
  private Long maxSeq;

  @TableField("last_msg_abstract")
  private String lastMsgAbstract;

  @TableField("last_msg_time")
  private LocalDateTime lastMsgTime;

  @TableField("cs_status")
  private Integer csStatus;

  /** 当前绑定的坐席 user_id（V6 migration 新增列，D34）。null 表示未分配。 */
  @TableField("agent_id")
  private Long agentId;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
