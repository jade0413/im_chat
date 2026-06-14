import axios, { AxiosError, type AxiosInstance, type InternalAxiosRequestConfig } from 'axios';
import JSONbig from 'json-bigint';
import { API_BASE_URL, REFRESH_TOKEN_KEY, TENANT_ID } from '../config';
import { useSocketStore } from '../store/socketStore';
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
const requestStartedAt = new WeakMap<InternalAxiosRequestConfig, number>();

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
  requestStartedAt.set(config, Date.now());
  const token = getAccessToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  config.headers['X-Tenant-Id'] = String(TENANT_ID);
  useSocketStore.getState().addLog(
    'info',
    `HTTP 请求 ${String(config.method ?? 'GET').toUpperCase()} ${config.url ?? ''}`,
    formatHttpPayload(config.params, config.data),
  );
  return config;
});

apiClient.interceptors.response.use(
  (response) => {
    const duration = response.config ? Date.now() - (requestStartedAt.get(response.config) ?? Date.now()) : 0;
    useSocketStore.getState().addLog(
      'info',
      `HTTP 响应 ${response.status} ${String(response.config.method ?? 'GET').toUpperCase()} ${response.config.url ?? ''} ${duration}ms`,
      formatHttpPayload(undefined, response.data),
    );
    return unwrapEnvelope(response.data);
  },
  async (error: AxiosError<ApiEnvelope<unknown>>) => {
    const config = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const duration = config ? Date.now() - (requestStartedAt.get(config) ?? Date.now()) : 0;
    useSocketStore.getState().addLog(
      'error',
      `HTTP 错误 ${error.response?.status ?? 'NO_RESPONSE'} ${String(config?.method ?? 'GET').toUpperCase()} ${config?.url ?? ''} ${duration}ms`,
      formatHttpPayload(config?.params, error.response?.data ?? error.message),
    );
    if (error.response?.status === 401 && config && !config._retry) {
      config._retry = true;
      try {
        const tokens = await refreshAccessToken();
        applyRefreshedToken(tokens);
        config.headers.Authorization = `Bearer ${tokens.accessToken}`;
        return apiClient(config);
      } catch (refreshError) {
        // 只有 refresh token 本身被明确拒绝（HTTP 401）才触发登出。
        // 网络错误 / 服务器 5xx 不清除本地会话，让用户下次打开时自动重试。
        const refreshStatus = (refreshError as AxiosError)?.response?.status;
        if (refreshStatus === 401 || refreshStatus === 403) {
          handleAuthFailure();
        }
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

/**
 * 直接调用 refresh 接口，绕开 apiClient 拦截器。
 * authStore.refreshFromStorage 必须用这个，否则：
 *   authApi.refresh → apiClient → 401 → 拦截器再次尝试 refresh → 死循环 + 双重 logout
 */
export async function refreshTokenDirect(refreshToken: string): Promise<TokenResponse> {
  const response = await axios.post<ApiEnvelope<TokenResponse>>(
    `${API_BASE_URL}/api/v1/auth/refresh`,
    { refreshToken },
    {
      headers: { 'X-Tenant-Id': String(TENANT_ID) },
      transformResponse: [parseJsonSafely],
    },
  );
  return unwrapEnvelope(response.data) as TokenResponse;
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

function formatHttpPayload(params: unknown, data: unknown) {
  const payload = {
    params: redact(params),
    data: redact(data),
  };
  return safeStringify(payload);
}

function redact(value: unknown): unknown {
  if (value == null) {
    return value;
  }
  if (typeof value === 'string') {
    return value.length > 500 ? `${value.slice(0, 500)}...` : value;
  }
  if (Array.isArray(value)) {
    return value.map(redact);
  }
  if (typeof value !== 'object') {
    return value;
  }
  const source = value as Record<string, unknown>;
  const output: Record<string, unknown> = {};
  for (const [key, item] of Object.entries(source)) {
    if (/token|password|authorization/i.test(key)) {
      output[key] = '[REDACTED]';
    } else {
      output[key] = redact(item);
    }
  }
  return output;
}

function safeStringify(value: unknown) {
  try {
    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}
