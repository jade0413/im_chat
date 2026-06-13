package com.im.conversation.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("group_info")
public class GroupInfoEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("name")
  private String name;

  @TableField("owner_id")
  private Long ownerId;

  @TableField("avatar")
  private String avatar;

  @TableField("member_count")
  private Integer memberCount;

  @TableField("status")
  private Integer status;
}
