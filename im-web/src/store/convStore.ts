import { create } from 'zustand';
import type { Conversation } from './types';

interface ConvState {
  conversations: Map<string, Conversation>;
  convListVersion: string;
  activeConvId: string | null;
  /** 合并更新：新值为 undefined 时保留原值，防止部分更新丢失 groupId/peerUserId 等字段 */
  upsertConv: (conv: Conversation) => void;
  removeConv: (convId: string) => void;
  setActive: (convId: string | null) => void;
  setConvListVersion: (version: string) => void;
  updateReadSeq: (convId: string, readSeq: string) => void;
  /** READ_NOTIFY 触发：记录对端已读位置，供已读回执 UI 使用 */
  updatePeerReadSeq: (convId: string, peerReadSeq: string) => void;
  /** 乐观地将 readSeq 标记为 maxSeq（进入会话时立即消除红点），服务端 READ_NOTIFY 随后确认 */
  markRead: (convId: string) => void;
}

/** 合并两个会话对象，next 的 undefined 值不会覆盖 existing 中的有效值 */
function mergeConv(existing: Conversation | undefined, next: Conversation): Conversation {
  if (!existing) return next;
  const merged = { ...existing } as Record<string, unknown>;
  for (const [k, v] of Object.entries(next)) {
    if (v !== undefined) merged[k] = v;
  }
  return merged as unknown as Conversation;
}

export const useConvStore = create<ConvState>((set) => ({
  conversations: new Map(),
  convListVersion: '0',
  activeConvId: null,

  upsertConv: (conv) =>
    set((state) => {
      const conversations = new Map(state.conversations);
      conversations.set(conv.convId, mergeConv(state.conversations.get(conv.convId), conv));
      return { conversations };
    }),

  removeConv: (convId) =>
    set((state) => {
      const conversations = new Map(state.conversations);
      conversations.delete(convId);
      return {
        conversations,
        activeConvId: state.activeConvId === convId ? null : state.activeConvId,
      };
    }),

  setActive: (activeConvId) => set({ activeConvId }),
  setConvListVersion: (convListVersion) => set({ convListVersion }),

  updateReadSeq: (convId, readSeq) =>
    set((state) => {
      const conversations = new Map(state.conversations);
      const current = conversations.get(convId);
      if (current) {
        conversations.set(convId, { ...current, readSeq });
      }
      return { conversations };
    }),

  updatePeerReadSeq: (convId, peerReadSeq) =>
    set((state) => {
      const conversations = new Map(state.conversations);
      const current = conversations.get(convId);
      if (current) {
        conversations.set(convId, { ...current, peerReadSeq });
      }
      return { conversations };
    }),

  markRead: (convId) =>
    set((state) => {
      const conversations = new Map(state.conversations);
      const current = conversations.get(convId);
      if (current && current.maxSeq && current.readSeq !== current.maxSeq) {
        conversations.set(convId, { ...current, readSeq: current.maxSeq });
      }
      return { conversations };
    }),
}));
