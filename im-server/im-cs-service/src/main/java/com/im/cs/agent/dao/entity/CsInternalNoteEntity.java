package com.im.cs.agent.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 客服内部备注，仅坐席侧可见，不进入访客消息流。 */
@Getter
@Setter
@TableName("cs_internal_note")
public class CsInternalNoteEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("conv_id")
  private Long convId;

  @TableField("agent_id")
  private Long agentId;

  @TableField("content")
  private String content;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
