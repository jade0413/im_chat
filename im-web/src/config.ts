export const TENANT_ID = Number(import.meta.env.VITE_TENANT_ID ?? '1');
export const APP_VERSION = import.meta.env.VITE_APP_VERSION ?? '0.1.0-web';
export const PLATFORM_WEB = 5;

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
export const WS_URL =
  import.meta.env.VITE_WS_URL ??
  `${window.location.protocol === 'https:' ? 'wss' : 'ws'}://${window.location.host}/ws`;

export const REFRESH_TOKEN_KEY = 'im_web_refresh_token';
export const DEVICE_ID_KEY = 'im_web_device_id';

export function getOrCreateDeviceId(): string {
  const existing = window.localStorage.getItem(DEVICE_ID_KEY);
  if (existing) {
    return existing;
  }
  const created = crypto.randomUUID();
  window.localStorage.setItem(DEVICE_ID_KEY, created);
  return created;
}
