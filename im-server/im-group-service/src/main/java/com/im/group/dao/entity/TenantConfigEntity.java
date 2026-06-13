package com.im.group.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("tenant_config")
public class TenantConfigEntity {

  @TableId("tenant_id")
  private Long tenantId;

  @TableField("max_group_members")
  private Integer maxGroupMembers;
}
