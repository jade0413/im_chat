import { apiClient } from './client';
import type { FriendItem, FriendRequestItem, IdLike, SendFriendRequestResult } from './types';

/** 发起好友申请（D40）。note 备注对方可见。toUserId 以字符串传，保留 Snowflake 精度。 */
export function sendFriendRequest(toUserId: IdLike, note: string) {
  return apiClient.post<unknown, SendFriendRequestResult>('/api/v1/friend/requests', {
    toUserId: String(toUserId),
    note,
  });
}

/** 申请历史。role=incoming 收到的 / outgoing 我发出的。 */
export function listFriendRequests(role: 'incoming' | 'outgoing' = 'incoming', limit = 50) {
  return apiClient.get<unknown, FriendRequestItem[]>('/api/v1/friend/requests', {
    params: { role, limit },
  });
}

export function acceptFriendRequest(id: IdLike) {
  return apiClient.post<unknown, void>(`/api/v1/friend/requests/${id}/accept`);
}

export function rejectFriendRequest(id: IdLike) {
  return apiClient.post<unknown, void>(`/api/v1/friend/requests/${id}/reject`);
}

export function ignoreFriendRequest(id: IdLike) {
  return apiClient.post<unknown, void>(`/api/v1/friend/requests/${id}/ignore`);
}

/** 好友列表。 */
export function listFriends() {
  return apiClient.get<unknown, FriendItem[]>('/api/v1/friend/list');
}

/** 删除好友（双向）。 */
export function deleteFriend(friendId: IdLike) {
  return apiClient.delete<unknown, void>(`/api/v1/friend/${friendId}`);
}

/** 修改好友备注名。 */
export function updateFriendRemark(friendId: IdLike, remark: string) {
  return apiClient.put<unknown, void>(`/api/v1/friend/${friendId}/remark`, { remark });
}

/** 好友设置：加我是否需要验证。1=需验证(默认) 0=免验证。 */
export function updateFriendSettings(friendVerifyRequired: number) {
  return apiClient.put<unknown, void>('/api/v1/friend/settings', { friendVerifyRequired });
}
