package com.im.cs.widget.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * widget_config 表实体（D37）。
 * 每个租户一行（UNIQUE KEY uk_tenant），不存在时使用 CsConstants 默认值。
 */
@Getter
@Setter
@TableName("widget_config")
public class WidgetConfigEntity {

  @TableId(value = "id", type = IdType.INPUT)
  private Long id;

  @TableField("tenant_id")
  private Long tenantId;

  @TableField("color")
  private String color;

  @TableField("welcome_msg")
  private String welcomeMsg;

  @TableField("offline_msg")
  private String offlineMsg;

  @TableField("display_name")
  private String displayName;

  @TableField("position")
  private String position;

  /** 1=显示 "Powered by XXX" 徽标（D37 免费版病毒传播机制）。 */
  @TableField("powered_by")
  private Integer poweredBy;

  @TableField("created_at")
  private LocalDateTime createdAt;

  @TableField("updated_at")
  private LocalDateTime updatedAt;
}
