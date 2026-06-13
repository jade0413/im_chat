import { apiClient } from './client';
import type { IdLike, UserProfile, UserPublicProfile } from './types';

export function getCurrentUser() {
  return apiClient.get<unknown, UserProfile>('/api/v1/users/me');
}

/** 搜索用户（昵称/账号前缀），最多 20 条。用于发起单聊时的用户选择。 */
export function searchUsers(keyword: string) {
  return apiClient.get<unknown, UserPublicProfile[]>('/api/v1/users/search', {
    params: { keyword },
  });
}

/** 批量获取用户公开资料，供历史消息填充昵称/头像。 */
export function batchGetUsers(ids: IdLike[]) {
  return apiClient.get<unknown, UserPublicProfile[]>('/api/v1/users/batch', {
    params: { ids: ids.join(',') },
  });
}
