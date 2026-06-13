package com.im.group.rest;

import com.im.common.auth.UserContext;
import com.im.common.web.ApiResponse;
import com.im.group.dto.AddGroupMembersRequest;
import com.im.group.dto.CreateGroupRequest;
import com.im.group.dto.GroupMemberChangeResponse;
import com.im.group.dto.GroupMemberResponse;
import com.im.group.dto.GroupResponse;
import com.im.group.dto.UpdateGroupRequest;
import com.im.group.service.GroupService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

  private final GroupService groupService;

  public GroupController(GroupService groupService) {
    this.groupService = groupService;
  }

  @PostMapping
  public ApiResponse<GroupResponse> create(@Valid @RequestBody CreateGroupRequest request) {
    return ApiResponse.ok(groupService.createGroup(UserContext.requiredUserId(), request));
  }

  @GetMapping("/{groupId}")
  public ApiResponse<GroupResponse> getGroup(@PathVariable long groupId) {
    return ApiResponse.ok(groupService.getGroup(UserContext.requiredUserId(), groupId));
  }

  @GetMapping("/{groupId}/members")
  public ApiResponse<List<GroupMemberResponse>> getMembers(@PathVariable long groupId) {
    return ApiResponse.ok(groupService.getMembers(UserContext.requiredUserId(), groupId));
  }

  @PostMapping("/{groupId}/members")
  public ApiResponse<GroupMemberChangeResponse> addMembers(
      @PathVariable long groupId,
      @Valid @RequestBody AddGroupMembersRequest request) {
    return ApiResponse.ok(groupService.addMembers(UserContext.requiredUserId(), groupId, request));
  }

  @DeleteMapping("/{groupId}/members/{userId}")
  public ApiResponse<GroupMemberChangeResponse> removeMember(
      @PathVariable long groupId,
      @PathVariable long userId) {
    return ApiResponse.ok(groupService.removeMember(UserContext.requiredUserId(), groupId, userId));
  }

  @PatchMapping("/{groupId}")
  public ApiResponse<GroupResponse> rename(
      @PathVariable long groupId,
      @Valid @RequestBody UpdateGroupRequest request) {
    return ApiResponse.ok(groupService.rename(UserContext.requiredUserId(), groupId, request));
  }
}
