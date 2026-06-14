import { create } from 'zustand';

export type NavTab = 'chats' | 'contacts' | 'groups';

interface UiState {
  kickMessage: string | null;
  collapsedConvList: boolean;
  /** 左侧导航激活 tab */
  activeTab: NavTab;
  setKickMessage: (message: string | null) => void;
  setCollapsedConvList: (collapsed: boolean) => void;
  setActiveTab: (tab: NavTab) => void;
}

export const useUiStore = create<UiState>((set) => ({
  kickMessage: null,
  collapsedConvList: false,
  activeTab: 'chats',
  setKickMessage: (kickMessage) => set({ kickMessage }),
  setCollapsedConvList: (collapsedConvList) => set({ collapsedConvList }),
  setActiveTab: (activeTab) => set({ activeTab }),
}));
