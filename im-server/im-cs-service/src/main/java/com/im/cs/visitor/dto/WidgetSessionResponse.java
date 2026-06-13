package com.im.cs.visitor.dto;

/**
 * 访客接入 widget 的响应（T31）。
 *
 * <p>前端收到后：
 * <ol>
 *   <li>将 accessToken 存入内存（不存 localStorage，防止被第三方读取）</li>
 *   <li>建立 WebSocket 连接时带上 accessToken 完成网关鉴权</li>
 *   <li>打开 conversationId 对应的会话窗口</li>
 *   <li>显示 displayName 作为访客昵称</li>
 * </ol>
 *
 * @param accessToken      JWT Access Token，有效期 2h，用于 WS 连接鉴权
 * @param refreshToken     JWT Refresh Token，有效期 30d，用于续签（访客场景一般不需要）
 * @param tokenType        固定 "Bearer"
 * @param expiresIn        accessToken 有效期秒数（默认 7200）
 * @param conversationId   当前 CS 会话 ID（open 或 assigned 状态）
 * @param visitorId        访客 user_id（发消息时作为 sender_id）
 * @param displayName      访客昵称，如 "访客A3K9"
 * @param isNewConversation true=新建会话；false=续旧会话
 * @param csStatus         当前会话状态（1=open 2=assigned）
 */
public record WidgetSessionResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long   expiresIn,
    long   conversationId,
    long   visitorId,
    String displayName,
    boolean isNewConversation,
    int     csStatus
) {}
