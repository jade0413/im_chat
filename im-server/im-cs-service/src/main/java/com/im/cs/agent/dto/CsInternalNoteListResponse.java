package com.im.cs.agent.dto;

import java.util.List;

public record CsInternalNoteListResponse(
    List<CsInternalNoteResponse> notes
) {}
