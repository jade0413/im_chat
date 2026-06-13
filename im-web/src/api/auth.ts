import { PLATFORM_WEB } from '../config';
import { apiClient } from './client';
import type { LoginRequest, RegisterRequest, TokenResponse } from './types';

export function login(account: string, password: string) {
  return apiClient.post<LoginRequest, TokenResponse>('/api/v1/auth/login', {
    account,
    password,
    platform: PLATFORM_WEB,
  });
}

export function register(account: string, password: string, nickname?: string) {
  return apiClient.post<RegisterRequest, TokenResponse>('/api/v1/auth/register', {
    account,
    password,
    nickname,
    platform: PLATFORM_WEB,
  });
}

export function refresh(refreshToken: string) {
  return apiClient.post<{ refreshToken: string }, TokenResponse>('/api/v1/auth/refresh', { refreshToken });
}
