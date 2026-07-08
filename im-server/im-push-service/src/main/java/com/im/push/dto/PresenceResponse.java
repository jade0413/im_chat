package com.im.push.dto;

/** 用户在线状态查询响应。online=false 时前端不展示状态标签。 */
public record PresenceResponse(long userId, boolean online) {
}
