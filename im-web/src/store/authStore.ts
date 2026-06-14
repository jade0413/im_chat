import { create } from 'zustand';
import { bindAuthHandlers, refreshTokenDirect } from '../api/client';
import * as authApi from '../api/auth';
import { getCurrentUser } from '../api/user';
import { REFRESH_TOKEN_KEY } from '../config';
import { idToString } from '../utils/id';
import type { TokenResponse } from '../api/types';
import type { SessionUser } from './types';

interface AuthState {
  user: SessionUser | null;
  accessToken: string | null;
  refreshToken: string | null;
  bootstrapped: boolean;
  loading: boolean;
  login: (account: string, password: string) => Promise<void>;
  register: (account: string, password: string, nickname?: string) => Promise<void>;
  refreshFromStorage: () => Promise<boolean>;
  logout: () => void;
  applyTokens: (tokens: TokenResponse) => void;
  loadCurrentUser: () => Promise<void>;
  /** 资料更新后本地同步 session user */
  setUser: (user: SessionUser) => void;
}

function normalizeUser(user: Awaited<ReturnType<typeof getCurrentUser>>): SessionUser {
  return {
    ...user,
    id: idToString(user.id),
    tenantId: idToString(user.tenantId),
  };
}

/**
 * refresh token 是否被明确拒绝（即 token 真正失效，需要重新登录）。
 * 网络错误 / 服务器 5xx 不算——用户不应因为临时故障而丢失会话。
 */
function isTokenInvalid(error: unknown): boolean {
  if (error && typeof error === 'object') {
    // AxiosError：HTTP 401/403
    const status = (error as { response?: { status?: number } }).response?.status;
    if (status === 401 || status === 403) return true;
  }
  // 其余（网络超时、5xx）不视为 token 失效
  return false;
}

export const useAuthStore = create<AuthState>((set, get) => ({
  user: null,
  accessToken: null,
  refreshToken: window.localStorage.getItem(REFRESH_TOKEN_KEY),
  bootstrapped: false,
  loading: false,

  applyTokens: (tokens) => {
    window.localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    set({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
  },

  loadCurrentUser: async () => {
    const user = await getCurrentUser();
    set({ user: normalizeUser(user) });
  },

  login: async (account, password) => {
    set({ loading: true });
    try {
      const tokens = await authApi.login(account, password);
      get().applyTokens(tokens);
      await get().loadCurrentUser();
    } finally {
      set({ loading: false, bootstrapped: true });
    }
  },

  register: async (account, password, nickname) => {
    set({ loading: true });
    try {
      const tokens = await authApi.register(account, password, nickname);
      get().applyTokens(tokens);
      await get().loadCurrentUser();
    } finally {
      set({ loading: false, bootstrapped: true });
    }
  },

  refreshFromStorage: async () => {
    const refreshToken = window.localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      set({ bootstrapped: true });
      return false;
    }
    set({ loading: true });
    try {
      // 直连调用，绕开 apiClient 拦截器（避免 401 时再次触发 refresh → 死循环 + 双重 logout）
      const tokens = await refreshTokenDirect(refreshToken);
      get().applyTokens(tokens);
      await get().loadCurrentUser();
      return true;
    } catch (error) {
      if (isTokenInvalid(error)) {
        // refresh token 被服务端明确拒绝（401/403）→ 真正登出
        get().logout();
      } else {
        // 网络超时 / 服务器 5xx：不删除 refresh token，保留会话。
        // 用户下次打开 App 或网络恢复后会自动重试。
        set({ accessToken: null, user: null });
      }
      return false;
    } finally {
      set({ loading: false, bootstrapped: true });
    }
  },

  logout: () => {
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
    set({ user: null, accessToken: null, refreshToken: null, bootstrapped: true });
  },

  setUser: (user) => set({ user }),
}));

bindAuthHandlers({
  getAccessToken: () => useAuthStore.getState().accessToken,
  applyRefreshedToken: (tokens) => useAuthStore.getState().applyTokens(tokens),
  handleAuthFailure: () => useAuthStore.getState().logout(),
});
