import { apiClient } from './client';
import type { UserProfile } from './types';

export function getCurrentUser() {
  return apiClient.get<unknown, UserProfile>('/api/v1/users/me');
}
