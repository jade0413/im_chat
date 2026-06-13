import { create } from 'zustand';

interface UiState {
  kickMessage: string | null;
  collapsedConvList: boolean;
  setKickMessage: (message: string | null) => void;
  setCollapsedConvList: (collapsed: boolean) => void;
}

export const useUiStore = create<UiState>((set) => ({
  kickMessage: null,
  collapsedConvList: false,
  setKickMessage: (kickMessage) => set({ kickMessage }),
  setCollapsedConvList: (collapsedConvList) => set({ collapsedConvList }),
}));
