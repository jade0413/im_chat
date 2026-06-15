package com.im.cs.widget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Widget 配置更新（T35b/T36 管理端）。所有字段必填，整体覆盖（避免撞 NOT NULL / 被 MP 当 patch 跳过 null）。 */
public record UpdateWidgetConfigRequest(
    @NotBlank @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "color 须为 #RRGGBB") String color,
    @NotBlank @Size(max = 128) String welcomeMsg,
    @NotBlank @Size(max = 128) String offlineMsg,
    @NotBlank @Size(max = 64) String displayName,
    @NotBlank @Pattern(regexp = "^(bottom-right|bottom-left)$", message = "position 仅支持 bottom-right|bottom-left")
    String position,
    @NotNull Boolean poweredBy) {
}
