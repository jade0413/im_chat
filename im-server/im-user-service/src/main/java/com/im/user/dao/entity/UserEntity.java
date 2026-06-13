package com.im.user.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("`user`")
public class UserEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("account")
  private String account;

  @TableField("password_hash")
  private String passwordHash;

  @TableField("nickname")
  private String nickname;

  @TableField("avatar")
  private String avatar;

  @TableField("user_type")
  private Integer userType;

  @TableField("verified_type")
  private Integer verifiedType;

  @TableField("device_fp")
  private String deviceFp;

  @TableField("status")
  private Integer status;

  @TableField("mute_until")
  private LocalDateTime muteUntil;

  /** V6 新增：0=普通用户 1=坐席（与 user_type 正交，见 D34） */
  @TableField("is_agent")
  private Integer isAgent;

  /** V6 新增：0=offline 1=online 2=busy（见 D35） */
  @TableField("agent_status")
  private Integer agentStatus;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
