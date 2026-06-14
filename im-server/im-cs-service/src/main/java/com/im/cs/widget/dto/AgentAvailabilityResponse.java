package com.im.cs.widget.dto;

/**
 * 坐席可用性响应（D35/D36，T36）。
 *
 * @param available        true=至少有一名坐席 online，可接新访客
 * @param onlineAgentCount 在线坐席数（展示用）
 */
public record AgentAvailabilityResponse(
    boolean available,
    int onlineAgentCount
) {}
