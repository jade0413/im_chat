package com.im.group.rest;

import com.im.common.auth.AuthTokenClaims;
import com.im.common.auth.BearerTokenExtractor;
import com.im.common.auth.JwtAccessTokenVerifier;
import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.common.tenant.TenantContext;
import com.im.common.web.ApiResponse;
import com.im.group.dto.AddGroupMembersRequest;
import com.im.group.dto.CreateGroupRequest;
import com.im.group.dto.GroupMemberChangeResponse;
import com.im.group.dto.GroupResponse;
import com.im.group.dto.UpdateGroupRequest;
import com.im.group.service.GroupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {

  private final GroupService groupService;
  private final JwtAccessTokenVerifier tokenVerifier;

  public GroupController(GroupService groupService, JwtAccessTokenVerifier tokenVerifier) {
    this.groupService = groupService;
    this.tokenVerifier = tokenVerifier;
  }

  @PostMapping
  public ApiResponse<GroupResponse> create(
      @Valid @RequestBody CreateGroupRequest request,
      @RequestHeader("Authorization") String authorization) {
    return ApiResponse.ok(groupService.createGroup(currentUserId(authorization), request));
  }

  @PostMapping("/{groupId}/members")
  public ApiResponse<GroupMemberChangeResponse> addMembers(
      @PathVariable long groupId,
      @Valid @RequestBody AddGroupMembersRequest request,
      @RequestHeader("Authorization") String authorization) {
    return ApiResponse.ok(groupService.addMembers(currentUserId(authorization), groupId, request));
  }

  @DeleteMapping("/{groupId}/members/{userId}")
  public ApiResponse<GroupMemberChangeResponse> removeMember(
      @PathVariable long groupId,
      @PathVariable long userId,
      @RequestHeader("Authorization") String authorization) {
    return ApiResponse.ok(groupService.removeMember(currentUserId(authorization), groupId, userId));
  }

  @PatchMapping("/{groupId}")
  public ApiResponse<GroupResponse> rename(
      @PathVariable long groupId,
      @Valid @RequestBody UpdateGroupRequest request,
      @RequestHeader("Authorization") String authorization) {
    return ApiResponse.ok(groupService.rename(currentUserId(authorization), groupId, request));
  }

  private long currentUserId(String authorization) {
    AuthTokenClaims claims = tokenVerifier.verifyAccessToken(BearerTokenExtractor.extract(authorization));
    if (claims.tenantId() != TenantContext.requiredTenantId()) {
      throw new ImException(ErrorCode.TOKEN_INVALID);
    }
    return claims.userId();
  }
}
