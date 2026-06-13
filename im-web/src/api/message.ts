import { apiClient } from './client';
import type { IdLike, MessageHistoryResponse } from './types';

export function getMessageHistory(convId: IdLike, params: { endSeq?: IdLike; limit?: number } = {}) {
  return apiClient.get<unknown, MessageHistoryResponse>(`/api/v1/convs/${convId}/messages`, {
    params: {
      end_seq: params.endSeq,
      limit: params.limit ?? 30,
    },
  });
}

export function revokeMessage(convId: IdLike, seq: IdLike) {
  return apiClient.post<unknown, void>(`/api/v1/convs/${convId}/messages/${seq}/revoke`);
}
