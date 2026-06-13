package com.im.group.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AddGroupMembersRequest(
    @NotEmpty
    @Size(max = 500)
    List<Long> userIds
) {
}
