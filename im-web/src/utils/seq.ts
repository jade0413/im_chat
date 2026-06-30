import { compareIdLike } from './id';

interface Sequenced {
  seq?: string | null;
}

export function maxSeq(left: string | undefined, right: string): string {
  return compareIdLike(left ?? '0', right) >= 0 ? left ?? '0' : right;
}

export function hasSeqGap(currentSeq: string, incomingSeq: string): boolean {
  const current = BigInt(currentSeq || '0');
  const incoming = BigInt(incomingSeq || '0');
  return incoming > current + 1n;
}

export function advanceContiguousSeq(currentSeq: string, incomingSeq: string): string {
  const current = BigInt(currentSeq || '0');
  const incoming = BigInt(incomingSeq || '0');
  if (incoming <= current) {
    return currentSeq || '0';
  }
  return incoming === current + 1n ? incomingSeq : currentSeq || '0';
}

export function contiguousSeqFromMessages(messages: Iterable<Sequenced> | undefined, baseSeq = '0'): string {
  let syncSeq = baseSeq || '0';
  const sorted = Array.from(messages ?? [])
    .filter((message): message is { seq: string } => typeof message.seq === 'string' && message.seq.length > 0)
    .sort((a, b) => compareIdLike(a.seq, b.seq));
  for (const message of sorted) {
    syncSeq = advanceContiguousSeq(syncSeq, message.seq);
  }
  return syncSeq;
}

export function resolveLocalSyncSeq(
  storedSyncSeq: string | undefined,
  messages: Iterable<Sequenced> | undefined,
): string {
  if (storedSyncSeq && storedSyncSeq !== '0') {
    return storedSyncSeq;
  }
  return contiguousSeqFromMessages(messages, '0');
}
