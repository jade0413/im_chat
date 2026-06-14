import { apiClient } from './client';
import type {
  AgentAvailabilityResponse,
  AgentConvListResponse,
  CreateCsInternalNoteRequest,
  CsConvItemResponse,
  CsInternalNoteListResponse,
  CsInternalNoteResponse,
  WidgetConfigResponse,
} from './types';

export function listCsConversations(params?: { limit?: number; offset?: number }) {
  return apiClient.get<unknown, AgentConvListResponse>('/api/v1/cs/conversations', {
    params: {
      limit: params?.limit ?? 20,
      offset: params?.offset ?? 0,
    },
  });
}

export function getCsConversation(convId: string) {
  return apiClient.get<unknown, CsConvItemResponse>(`/api/v1/cs/conversations/${convId}`);
}

export function claimCsConversation(convId: string) {
  return apiClient.post<unknown, void>(`/api/v1/cs/conversations/${convId}/claim`);
}

export function resolveCsConversation(convId: string) {
  return apiClient.post<unknown, void>(`/api/v1/cs/conversations/${convId}/resolve`);
}

export function listCsInternalNotes(convId: string) {
  return apiClient.get<unknown, CsInternalNoteListResponse>(`/api/v1/cs/conversations/${convId}/notes`);
}

export function createCsInternalNote(convId: string, request: CreateCsInternalNoteRequest) {
  return apiClient.post<unknown, CsInternalNoteResponse>(`/api/v1/cs/conversations/${convId}/notes`, request);
}

export function getWidgetConfig() {
  return apiClient.get<unknown, WidgetConfigResponse>('/api/v1/cs/widget/config');
}

export function getAgentAvailability() {
  return apiClient.get<unknown, AgentAvailabilityResponse>('/api/v1/cs/widget/availability');
}
