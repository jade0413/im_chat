package com.im.cs.agent.dto;

public record CsInternalNoteResponse(
    long id,
    long convId,
    long agentId,
    String content,
    long createdAtMs
) {}
