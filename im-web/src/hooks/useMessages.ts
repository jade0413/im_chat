import { useCallback, useMemo, useState } from 'react';
import { getMessageHistory } from '../api/message';
import { useMessageStore } from '../store/messageStore';
import { useUserStore } from '../store/userStore';
import { historyItemToChatMessage } from '../socket/mappers';

export function useMessages(convId: string | null) {
  const [loadingHistory, setLoadingHistory] = useState(false);
  const messages = useMessageStore((state) => (convId ? (state.messages.get(convId) ?? []) : []));
  const hasMore = useMessageStore((state) => (convId ? (state.hasMore.get(convId) ?? true) : false));
  const prependHistory = useMessageStore((state) => state.prependHistory);
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

  return { messages, hasMore, loadingHistory, loadHistory };
}
