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
  const created = createUuid();
  window.localStorage.setItem(DEVICE_ID_KEY, created);
  return created;
}

export function createUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes);
  } else {
    for (let index = 0; index < bytes.length; index += 1) {
      bytes[index] = Math.floor(Math.random() * 256);
    }
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0'));
  return `${hex.slice(0, 4).join('')}-${hex.slice(4, 6).join('')}-${hex.slice(6, 8).join('')}-${hex
    .slice(8, 10)
    .join('')}-${hex.slice(10, 16).join('')}`;
}
