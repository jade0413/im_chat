package com.im.cs.agent.dto;

import java.util.List;

/**
 * 坐席工作台会话列表响应（T35）。
 */
public record AgentConvListResponse(
    List<CsConvItemResponse> convs,
    boolean hasMore
) {}
