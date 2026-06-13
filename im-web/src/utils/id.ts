import Long from 'long';
import type { IdLike } from '../api/types';

export function idToString(value: unknown): string {
  if (value == null) {
    return '0';
  }
  if (Long.isLong(value)) {
    return value.toString();
  }
  if (typeof value === 'object' && 'low' in value && 'high' in value) {
    return Long.fromValue(value as any).toString();
  }
  return String(value);
}

export function idToLong(value: IdLike | bigint | Long): Long {
  if (Long.isLong(value)) {
    return value;
  }
  return Long.fromString(String(value), false);
}

export function idToNumber(value: IdLike): number {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
}

export function compareIdLike(a: IdLike | undefined, b: IdLike | undefined): number {
  const left = BigInt(String(a ?? 0));
  const right = BigInt(String(b ?? 0));
  if (left === right) {
    return 0;
  }
  return left > right ? 1 : -1;
}
