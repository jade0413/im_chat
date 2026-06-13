package com.im.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * 坐席在线状态切换请求（D35）。
 *
 * @param agentStatus 0=offline, 1=online, 2=busy
 */
public record UpdateAgentStatusRequest(
    @Min(0) @Max(2) int agentStatus
) {}
