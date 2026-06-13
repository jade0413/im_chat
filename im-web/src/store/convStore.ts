import { create } from 'zustand';
import type { Conversation } from './types';

interface ConvState {
  conversations: Map<string, Conversation>;
  convListVersion: string;
  activeConvId: string | null;
  upsertConv: (conv: Conversation) => void;
  removeConv: (convId: string) => void;
  setActive: (convId: string | null) => void;
  setConvListVersion: (version: string) => void;
  updateReadSeq: (convId: string, readSeq: string) => void;
}

export const useConvStore = create<ConvState>((set) => ({
  conversations: new Map(),
  convListVersion: '0',
  activeConvId: null,

  upsertConv: (conv) =>
    set((state) => {
      const conversations = new Map(state.conversations);
      conversations.set(conv.convId, conv);
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
}));
