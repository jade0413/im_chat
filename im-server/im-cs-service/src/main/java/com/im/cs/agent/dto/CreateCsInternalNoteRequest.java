package com.im.cs.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCsInternalNoteRequest(
    @NotBlank(message = "备注内容不能为空")
    @Size(max = 2000, message = "备注内容最长 2000 字")
    String content
) {}
