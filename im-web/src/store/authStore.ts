import { create } from 'zustand';
import { bindAuthHandlers } from '../api/client';
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
}

function normalizeUser(user: Awaited<ReturnType<typeof getCurrentUser>>): SessionUser {
  return {
    ...user,
    id: idToString(user.id),
    tenantId: idToString(user.tenantId),
  };
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
      const tokens = await authApi.refresh(refreshToken);
      get().applyTokens(tokens);
      await get().loadCurrentUser();
      return true;
    } catch {
      get().logout();
      return false;
    } finally {
      set({ loading: false, bootstrapped: true });
    }
  },

  logout: () => {
    window.localStorage.removeItem(REFRESH_TOKEN_KEY);
    set({ user: null, accessToken: null, refreshToken: null, bootstrapped: true });
  },
}));

bindAuthHandlers({
  getAccessToken: () => useAuthStore.getState().accessToken,
  applyRefreshedToken: (tokens) => useAuthStore.getState().applyTokens(tokens),
  handleAuthFailure: () => useAuthStore.getState().logout(),
});
