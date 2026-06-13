import { create } from 'zustand';
import { batchGetUsers } from '../api/user';
import type { UserPublicProfile } from '../api/types';
import { idToString } from '../utils/id';

interface UserStoreState {
  /** userId (string) → 公开资料缓存 */
  users: Map<string, UserPublicProfile>;
  /** 正在拉取中的 userId 集合，防止重复请求 */
  fetching: Set<string>;
  setUsers: (profiles: UserPublicProfile[]) => void;
  /** 确保指定 userId 列表的资料已缓存，缺失的批量拉取。 */
  ensureUsers: (userIds: string[]) => Promise<void>;
  getUser: (userId: string) => UserPublicProfile | undefined;
}

export const useUserStore = create<UserStoreState>((set, get) => ({
  users: new Map(),
  fetching: new Set(),

  setUsers: (profiles) =>
    set((state) => {
      const users = new Map(state.users);
      for (const profile of profiles) {
        users.set(idToString(profile.id), profile);
      }
      return { users };
    }),

  ensureUsers: async (userIds) => {
    const state = get();
    const missing = userIds.filter(
      (id) => !state.users.has(id) && !state.fetching.has(id),
    );
    if (missing.length === 0) return;

    // 标记为拉取中
    set((s) => {
      const fetching = new Set(s.fetching);
      for (const id of missing) fetching.add(id);
      return { fetching };
    });

    try {
      const profiles = await batchGetUsers(missing);
      get().setUsers(profiles);
    } catch {
      // 拉取失败静默忽略：历史消息仍显示 userId 兜底
    } finally {
      set((s) => {
        const fetching = new Set(s.fetching);
        for (const id of missing) fetching.delete(id);
        return { fetching };
      });
    }
  },

  getUser: (userId) => get().users.get(userId),
}));
