import { useCallback, useMemo, useState } from 'react';
import { getMessageHistory } from '../api/message';
import { useMessageStore } from '../store/messageStore';
import { useUserStore } from '../store/userStore';
import { historyItemToChatMessage } from '../socket/mappers';
import type { ChatMessage } from '../store/types';

const EMPTY_MESSAGES: ChatMessage[] = [];

export function useMessages(convId: string | null, options: { enabled?: boolean } = {}) {
  const enabled = options.enabled ?? true;
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const messages = useMessageStore((state) => (convId ? (state.messages.get(convId) ?? EMPTY_MESSAGES) : EMPTY_MESSAGES));
  const hasMore = useMessageStore((state) => (enabled && convId ? (state.hasMore.get(convId) ?? true) : false));
  const prependHistory = useMessageStore((state) => state.prependHistory);
  // 直接读 store state，不订阅——避免 ensureUsers 内部 set() 触发订阅组件 re-render
  const ensureUsers = useUserStore.getState().ensureUsers;

  const oldestSeq = useMemo(() => messages.find((message) => message.seq)?.seq, [messages]);

  const loadHistory = useCallback(async () => {
    if (!enabled || !convId || loadingHistory || !hasMore) {
      return;
    }
    setLoadingHistory(true);
    setHistoryError(null);
    try {
      const page = await getMessageHistory(convId, { endSeq: oldestSeq, limit: 30 });
      const chatMessages = page.messages.map(historyItemToChatMessage);
      prependHistory(convId, chatMessages, page.hasMore);
      // L2：历史加载后一次性拉取发送方资料，不放在 reactive useEffect 里
      const senderIds = [...new Set(chatMessages.map((m) => m.sender.userId).filter(Boolean))];
      if (senderIds.length > 0) void ensureUsers(senderIds);
    } catch (error) {
      setHistoryError(error instanceof Error ? error.message : '消息记录加载失败');
    } finally {
      setLoadingHistory(false);
    }
  }, [enabled, convId, hasMore, loadingHistory, oldestSeq, prependHistory, ensureUsers]);

  return { messages, hasMore, loadingHistory, historyError, loadHistory };
}
