package com.im.group.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("message")
public class GroupMessageEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("conversation_id")
  private Long conversationId;

  @TableField("seq")
  private Long seq;

  @TableField("sender_id")
  private Long senderId;

  @TableField("client_msg_id")
  private String clientMsgId;

  @TableField("msg_type")
  private Integer msgType;

  @TableField("content")
  private byte[] content;

  @TableField("abstract")
  private String abstractText;

  @TableField("ext")
  private String ext;

  @TableField("status")
  private Integer status;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
