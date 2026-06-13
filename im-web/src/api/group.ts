import { apiClient } from './client';
import type { AddGroupMembersRequest, CreateGroupRequest, GroupMemberChangeResponse, GroupResponse, IdLike } from './types';

export function createGroup(request: CreateGroupRequest) {
  return apiClient.post<CreateGroupRequest, GroupResponse>('/api/v1/groups', request);
}

export function addGroupMembers(groupId: IdLike, request: AddGroupMembersRequest) {
  return apiClient.post<AddGroupMembersRequest, GroupMemberChangeResponse>(`/api/v1/groups/${groupId}/members`, request);
}

export function removeGroupMember(groupId: IdLike, userId: IdLike) {
  return apiClient.delete<unknown, GroupMemberChangeResponse>(`/api/v1/groups/${groupId}/members/${userId}`);
}

export function renameGroup(groupId: IdLike, name: string) {
  return apiClient.patch<{ name: string }, GroupResponse>(`/api/v1/groups/${groupId}`, { name });
}
