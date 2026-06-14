import { useCallback, useMemo, useState } from 'react';
import { getMessageHistory } from '../api/message';
import { useMessageStore } from '../store/messageStore';
import { useUserStore } from '../store/userStore';
import { historyItemToChatMessage } from '../socket/mappers';
import type { ChatMessage } from '../store/types';

const EMPTY_MESSAGES: ChatMessage[] = [];

export function useMessages(convId: string | null) {
  const [loadingHistory, setLoadingHistory] = useState(false);
  const messages = useMessageStore((state) => (convId ? (state.messages.get(convId) ?? EMPTY_MESSAGES) : EMPTY_MESSAGES));
  const hasMore = useMessageStore((state) => (convId ? (state.hasMore.get(convId) ?? true) : false));
  const prependHistory = useMessageStore((state) => state.prependHistory);
  const appendMessages = useMessageStore((state) => state.appendMessages);
  // 直接读 store state，不订阅——避免 ensureUsers 内部 set() 触发订阅组件 re-render
  const ensureUsers = useUserStore.getState().ensureUsers;

  const oldestSeq = useMemo(() => messages.find((message) => message.seq)?.seq, [messages]);

  const loadHistory = useCallback(async () => {
    if (!convId || loadingHistory || !hasMore) {
      return;
    }
    setLoadingHistory(true);
    try {
      const page = await getMessageHistory(convId, { endSeq: oldestSeq, limit: 30 });
      const chatMessages = page.messages.map(historyItemToChatMessage);
      prependHistory(convId, chatMessages, page.hasMore);
      // L2：历史加载后一次性拉取发送方资料，不放在 reactive useEffect 里
      const senderIds = [...new Set(chatMessages.map((m) => m.sender.userId).filter(Boolean))];
      if (senderIds.length > 0) void ensureUsers(senderIds);
    } finally {
      setLoadingHistory(false);
    }
  }, [convId, hasMore, loadingHistory, oldestSeq, prependHistory, ensureUsers]);

  /**
   * 加载最新 N 条消息（不传 endSeq）——用于离线重连后补齐 SYNC 遗漏的新消息。
   * 不使用 prependHistory，改用 appendMessages 合并，保留已有旧消息。
   */
  const loadLatest = useCallback(async () => {
    if (!convId || loadingHistory) {
      return;
    }
    setLoadingHistory(true);
    try {
      const page = await getMessageHistory(convId, { limit: 30 });
      const chatMessages = page.messages.map(historyItemToChatMessage);
      if (chatMessages.length > 0) {
        appendMessages(convId, chatMessages);
        const senderIds = [...new Set(chatMessages.map((m) => m.sender.userId).filter(Boolean))];
        if (senderIds.length > 0) void ensureUsers(senderIds);
      }
    } finally {
      setLoadingHistory(false);
    }
  }, [convId, loadingHistory, appendMessages, ensureUsers]);

  return { messages, hasMore, loadingHistory, loadHistory, loadLatest };
}
