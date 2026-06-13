package com.im.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateGroupRequest(
    @NotBlank
    @Size(max = 128)
    String name,

    @Size(max = 499)
    List<Long> memberUserIds
) {
}
