import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import JSONbig from 'json-bigint';
import { API_BASE_URL, REFRESH_TOKEN_KEY, TENANT_ID } from '../config';
import type { ApiEnvelope, TokenResponse } from './types';

export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly traceId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type AccessTokenGetter = () => string | null;
type AccessTokenSetter = (tokens: TokenResponse) => void;
type AuthFailureHandler = () => void;

let getAccessToken: AccessTokenGetter = () => null;
let applyRefreshedToken: AccessTokenSetter = () => undefined;
let handleAuthFailure: AuthFailureHandler = () => undefined;
let refreshPromise: Promise<TokenResponse> | null = null;
const json = JSONbig({ storeAsString: true });

export function bindAuthHandlers(options: {
  getAccessToken: AccessTokenGetter;
  applyRefreshedToken: AccessTokenSetter;
  handleAuthFailure: AuthFailureHandler;
}) {
  getAccessToken = options.getAccessToken;
  applyRefreshedToken = options.applyRefreshedToken;
  handleAuthFailure = options.handleAuthFailure;
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
  transformResponse: [parseJsonSafely],
  headers: {
    'X-Tenant-Id': String(TENANT_ID),
  },
});

apiClient.interceptors.request.use((config) => {
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['X-Tenant-Id'] = String(TENANT_ID);
  return config;
});

apiClient.interceptors.response.use(
  (response) => unwrapEnvelope(response.data),
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    if (error.response?.status === 401 && config && !config._retry) {
      config._retry = true;
      try {
        const tokens = await refreshAccessToken();
        applyRefreshedToken(tokens);
        config.headers.Authorization = `Bearer ${tokens.accessToken}`;
        return apiClient(config);
      } catch (refreshError) {
        handleAuthFailure();
        throw refreshError;
      }
    }

    const envelope = error.response?.data;
    if (envelope) {
      throw new ApiError(envelope.code, envelope.message, envelope.traceId);
    }
    throw error;
  },
);

function unwrapEnvelope<T>(payload: ApiEnvelope<T> | T): T {
  if (payload && typeof payload === 'object' && 'code' in payload && 'message' in payload) {
    const envelope = payload as ApiEnvelope<T>;
    if (envelope.code !== 0) {
      throw new ApiError(envelope.code, envelope.message, envelope.traceId);
    }
    return envelope.data;
  }
  return payload as T;
}

async function refreshAccessToken(): Promise<TokenResponse> {
  if (!refreshPromise) {
    const refreshToken = window.localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!refreshToken) {
      throw new ApiError(401, '登录状态已过期，请重新登录');
    }
    refreshPromise = axios
      .post<ApiEnvelope<TokenResponse>>(
        `${API_BASE_URL}/api/v1/auth/refresh`,
        { refreshToken },
        { headers: { 'X-Tenant-Id': String(TENANT_ID) }, transformResponse: [parseJsonSafely] },
      )
      .then((response) => unwrapEnvelope(response.data))
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

function parseJsonSafely(data: unknown) {
  if (typeof data !== 'string' || data.length === 0) {
    return data;
  }
  try {
    return json.parse(data);
  } catch {
    return data;
  }
}
