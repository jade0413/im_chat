package com.im.message.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("file_meta")
public class MessageFileMetaEntity {

  @TableId("id")
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("uploader_id")
  private Long uploaderId;

  @TableField("object_key")
  private String objectKey;

  @TableField("mime")
  private String mime;

  @TableField("size")
  private Long size;

  @TableField("duration_ms")
  private Integer durationMs;

  @TableField("status")
  private Integer status;
}
