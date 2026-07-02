package com.im.call.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 通话记录（CDR，D45）。result：1=completed 2=rejected 3=canceled 4=timeout。 */
@Getter
@Setter
@TableName("call_record")
public class CallRecordEntity {

  public static final int RESULT_COMPLETED = 1;
  public static final int RESULT_REJECTED = 2;
  public static final int RESULT_CANCELED = 3;
  public static final int RESULT_TIMEOUT = 4;

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("call_id")
  private String callId;

  @TableField("caller_user_id")
  private Long callerUserId;

  @TableField("callee_user_id")
  private Long calleeUserId;

  @TableField("media_type")
  private Integer mediaType;

  @TableField("result")
  private Integer result;

  @TableField("connected_at")
  private LocalDateTime connectedAt;

  @TableField("ended_at")
  private LocalDateTime endedAt;

  @TableField("duration_sec")
  private Integer durationSec;
}
