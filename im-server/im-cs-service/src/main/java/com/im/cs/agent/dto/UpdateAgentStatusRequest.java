package com.im.cs.agent.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 坐席切换在线状态：0=offline 1=online 2=busy（T34/T35）。 */
public record UpdateAgentStatusRequest(
    @NotNull @Min(0) @Max(2) Integer agentStatus) {
}
