import { create } from 'zustand';
import { listFriendRequests, listFriends } from '../api/friend';
import type { FriendItem, FriendRequestItem } from '../api/types';

interface FriendState {
  friends: FriendItem[];
  incoming: FriendRequestItem[];
  loadingFriends: boolean;
  loadingRequests: boolean;
  /** 待处理（status=0）的收到申请数，用于联系人红点 */
  pendingCount: number;
  loadFriends: () => Promise<void>;
  loadRequests: () => Promise<void>;
}

export const useFriendStore = create<FriendState>((set) => ({
  friends: [],
  incoming: [],
  loadingFriends: false,
  loadingRequests: false,
  pendingCount: 0,

  loadFriends: async () => {
    set({ loadingFriends: true });
    try {
      const friends = await listFriends();
      set({ friends });
    } finally {
      set({ loadingFriends: false });
    }
  },

  loadRequests: async () => {
    set({ loadingRequests: true });
    try {
      const incoming = await listFriendRequests('incoming', 50);
      set({
        incoming,
        pendingCount: incoming.filter((r) => r.status === 0).length,
      });
    } finally {
      set({ loadingRequests: false });
    }
  },
}));
