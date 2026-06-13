import { apiClient } from './client';
import type { IdLike } from './types';

export interface ConvInfoResponse {
  convId: IdLike;
  type: number;
  title: string;
  avatar?: string;
  peerUserId?: IdLike;
  groupId?: IdLike;
  maxSeq: IdLike;
  readSeq: IdLike;
}

/**
 * 打开或创建与指定用户的单聊会话（D17 开放式单聊，幂等）。
 * 返回会话基本信息，包含 convId，可直接用于跳转聊天界面。
 */
export function openC2cConv(toUserId: IdLike) {
  return apiClient.post<unknown, ConvInfoResponse>('/api/v1/convs/c2c', null, {
    params: { toUserId },
  });
}
