package com.im.file.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("file_transcode_job")
public class FileTranscodeJobEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("file_id")
  private Long fileId;

  @TableField("source_object_key")
  private String sourceObjectKey;

  @TableField("source_mime")
  private String sourceMime;

  @TableField("target_profile")
  private String targetProfile;

  @TableField("status")
  private Integer status;

  @TableField("target_object_key")
  private String targetObjectKey;

  @TableField("error_msg")
  private String errorMsg;

  @TableField("retry_count")
  private Integer retryCount;

  @TableField("next_retry_at")
  private LocalDateTime nextRetryAt;

  @TableField("claim_owner")
  private String claimOwner;

  @TableField("claim_until")
  private LocalDateTime claimUntil;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
