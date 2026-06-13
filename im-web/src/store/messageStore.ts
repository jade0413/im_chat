import { create } from 'zustand';
import type { ChatMessage } from './types';
import { compareIdLike } from '../utils/id';

interface MessageState {
  messages: Map<string, ChatMessage[]>;
  hasMore: Map<string, boolean>;
  appendMessages: (convId: string, messages: ChatMessage[]) => void;
  prependHistory: (convId: string, messages: ChatMessage[], hasMore: boolean) => void;
  addOptimistic: (message: ChatMessage) => void;
  updateByClientMsgId: (convId: string, clientMsgId: string, patch: Partial<ChatMessage>) => void;
  revokeMessage: (convId: string, seq: string) => void;
  clear: () => void;
}

export const useMessageStore = create<MessageState>((set) => ({
  messages: new Map(),
  hasMore: new Map(),

  appendMessages: (convId, incoming) =>
    set((state) => ({
      messages: upsertMessageBatch(state.messages, convId, incoming),
    })),

  prependHistory: (convId, incoming, hasMore) =>
    set((state) => {
      const messages = upsertMessageBatch(state.messages, convId, incoming);
      const hasMoreMap = new Map(state.hasMore);
      hasMoreMap.set(convId, hasMore);
      return { messages, hasMore: hasMoreMap };
    }),

  addOptimistic: (message) =>
    set((state) => ({
      messages: upsertMessageBatch(state.messages, message.convId, [message]),
    })),

  updateByClientMsgId: (convId, clientMsgId, patch) =>
    set((state) => {
      const messages = new Map(state.messages);
      const list = messages.get(convId) ?? [];
      messages.set(
        convId,
        list.map((message) => (message.clientMsgId === clientMsgId ? { ...message, ...patch } : message)),
      );
      return { messages };
    }),

  revokeMessage: (convId, seq) =>
    set((state) => {
      const messages = new Map(state.messages);
      const list = messages.get(convId) ?? [];
      messages.set(
        convId,
        list.map((message) =>
          message.seq === seq
            ? { ...message, status: 'revoked', content: { kind: 'notification', eventType: 'message.revoked' } }
            : message,
        ),
      );
      return { messages };
    }),

  clear: () => set({ messages: new Map(), hasMore: new Map() }),
}));

function upsertMessageBatch(source: Map<string, ChatMessage[]>, convId: string, incoming: ChatMessage[]) {
  const messages = new Map(source);
  const byKey = new Map<string, ChatMessage>();
  for (const item of messages.get(convId) ?? []) {
    byKey.set(messageKey(item), item);
  }
  for (const item of incoming) {
    const key = messageKey(item);
    byKey.set(key, { ...byKey.get(key), ...item });
  }
  messages.set(
    convId,
    Array.from(byKey.values()).sort((a, b) => {
      if (a.seq || b.seq) {
        return compareIdLike(a.seq, b.seq);
      }
      return Number(a.sendTime) - Number(b.sendTime);
    }),
  );
  return messages;
}

function messageKey(message: ChatMessage): string {
  return message.serverMsgId ?? `${message.convId}:${message.seq ?? message.clientMsgId}`;
}
