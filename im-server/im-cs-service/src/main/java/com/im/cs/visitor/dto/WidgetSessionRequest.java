package com.im.cs.visitor.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 访客进入 widget 时的请求（T31）。
 *
 * <p>客户端（JS snippet / uni-app）在 localStorage 生成或读取 visitorToken（UUID），
 * 每次打开 widget 时携带此 token 请求此接口以获取 JWT 和会话 ID。
 *
 * <p>租户 ID 通过 {@code X-Tenant-Id} 请求头传入（由 TenantInterceptor 注入 TenantContext）。
 */
public record WidgetSessionRequest(

    /**
     * 客户端 localStorage 中的访客唯一标识（UUID 格式，36 字符含连字符）。
     * 首次访问时由客户端生成并持久化；后续访问携带同一 token 以续旧会话。
     */
    @NotBlank(message = "visitorToken 不能为空")
    @Size(max = 64, message = "visitorToken 最长 64 字符")
    String visitorToken
) {}
