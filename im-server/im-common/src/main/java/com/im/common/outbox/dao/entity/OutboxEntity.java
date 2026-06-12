package com.im.common.outbox.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("outbox")
public class OutboxEntity {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("event_type")
  private String eventType;

  @TableField("routing_key")
  private String routingKey;

  @TableField("payload")
  private byte[] payload;

  @TableField("status")
  private Integer status;

  @TableField("retry_count")
  private Integer retryCount;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
