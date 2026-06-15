package com.im.user.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 好友关系（双向行，A→B 与 B→A 各一行）。映射 baseline 已有的 friend 表（D17 预留，D40 启用）。
 * 复合主键 (tenant_id, user_id, friend_user_id)，无独立 id 列。
 */
@Getter
@Setter
@TableName("friend")
public class FriendEntity {

  /** 关系正常 */
  public static final int STATUS_NORMAL = 1;

  @TableField("tenant_id")
  private Long tenantId;

  /** 关系拥有者 */
  @TableField("user_id")
  private Long userId;

  /** 好友 user_id */
  @TableField("friend_user_id")
  private Long friendUserId;

  /** owner 给对方起的备注名 */
  @TableField("remark")
  private String remark;

  @TableField("status")
  private Integer status;

  @TableField("created_at")
  private LocalDateTime createdAt;
}
