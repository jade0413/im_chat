package com.im.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 好友申请（D40，状态唯一真相）。映射 friend_request 表。 */
@Getter
@Setter
@TableName("friend_request")
public class FriendRequestEntity {

  /** 申请状态：0 pending / 1 accepted / 2 rejected / 3 ignored */
  public static final int STATUS_PENDING = 0;
  public static final int STATUS_ACCEPTED = 1;
  public static final int STATUS_REJECTED = 2;
  public static final int STATUS_IGNORED = 3;

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("from_user_id")
  private Long fromUserId;

  @TableField("to_user_id")
  private Long toUserId;

  /** 申请备注，对方可见 */
  @TableField("note")
  private String note;

  @TableField("status")
  private Integer status;

  /** 1=因对方免验证自动通过 */
  @TableField("auto_accepted")
  private Integer autoAccepted;

  @TableField("handle_time")
  private LocalDateTime handleTime;

  @TableField("create_time")
  private LocalDateTime createTime;
}
