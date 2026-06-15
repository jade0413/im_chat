import { apiClient } from './client';
import type { IdLike, UserProfile, UserPublicProfile } from './types';

export function updateProfile(data: { nickname?: string; avatar?: string }) {
  return apiClient.put<typeof data, UserProfile>('/api/v1/users/me', data);
}

export function getCurrentUser() {
  return apiClient.get<unknown, UserProfile>('/api/v1/users/me');
}

/**
 * 查找用户（D42）：精确匹配 username 或完整手机号（无昵称/前缀模糊）。
 * 用于发起单聊或添加好友。
 */
export function searchUsers(keyword: string) {
  return apiClient.get<unknown, UserPublicProfile[]>('/api/v1/users/search', {
    params: { keyword },
  });
}

/** 设置/修改唯一用户名（D42，可分享的对外加好友标识）。 */
export function updateUsername(username: string) {
  return apiClient.put<unknown, void>('/api/v1/users/me/username', { username });
}

/** 批量获取用户公开资料，供历史消息填充昵称/头像。 */
export function batchGetUsers(ids: IdLike[]) {
  return apiClient.get<unknown, UserPublicProfile[]>('/api/v1/users/batch', {
    params: { ids: ids.join(',') },
  });
}

/** 坐席在线状态：0=离线 1=在线 2=忙碌。 */
export function updateAgentStatus(agentStatus: number) {
  return apiClient.patch<unknown, void>('/api/v1/users/me/agent-status', { agentStatus });
}
