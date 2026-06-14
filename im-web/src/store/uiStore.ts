import { create } from 'zustand';

export type NavTab = 'chats' | 'contacts' | 'groups' | 'cs';

interface UiState {
  kickMessage: string | null;
  collapsedConvList: boolean;
  /** 左侧导航激活 tab */
  activeTab: NavTab;
  csRefreshVersion: number;
  setKickMessage: (message: string | null) => void;
  setCollapsedConvList: (collapsed: boolean) => void;
  setActiveTab: (tab: NavTab) => void;
  requestCsRefresh: () => void;
}

export const useUiStore = create<UiState>((set) => ({
  kickMessage: null,
  collapsedConvList: false,
  activeTab: 'chats',
  csRefreshVersion: 0,
  setKickMessage: (kickMessage) => set({ kickMessage }),
  setCollapsedConvList: (collapsedConvList) => set({ collapsedConvList }),
  setActiveTab: (activeTab) => set({ activeTab }),
  requestCsRefresh: () => set((state) => ({ csRefreshVersion: state.csRefreshVersion + 1 })),
}));
