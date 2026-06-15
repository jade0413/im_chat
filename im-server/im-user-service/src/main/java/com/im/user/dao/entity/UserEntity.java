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

  /** V8 新增：D42 对外唯一标识（租户内唯一、自填、可分享），独立于登录用 account */
  @TableField("username")
  private String username;

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

  /** V8 新增：D40 加我是否需要验证，1=需验证(默认) 0=免验证直接通过 */
  @TableField("friend_verify_required")
  private Integer friendVerifyRequired;

  /** V8 新增：D41 是否允许跨租户加好友，0=仅租户内(默认)，MVP 仅留配置位 */
  @TableField("allow_cross_tenant_friend")
  private Integer allowCrossTenantFriend;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
