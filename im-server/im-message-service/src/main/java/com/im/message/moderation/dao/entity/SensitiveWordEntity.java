package com.im.message.moderation.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("sensitive_word")
public class SensitiveWordEntity {

  @TableId(value = "id", type = IdType.AUTO)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("word")
  private String word;

  @TableField("category")
  private String category;

  @TableField("action")
  private Integer action;

  @TableField("enabled")
  private Integer enabled;
}
