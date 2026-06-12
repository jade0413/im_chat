package com.im.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank
    @Size(min = 3, max = 64)
    String account,

    @NotBlank
    @Size(min = 8, max = 72)
    String password,

    Integer platform
) {
}
