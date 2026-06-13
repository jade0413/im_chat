package com.im.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateGroupRequest(
    @NotBlank
    @Size(max = 128)
    String name
) {
}
