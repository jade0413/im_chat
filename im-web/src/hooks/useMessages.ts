import { useCallback, useMemo, useState } from 'react';
import { getMessageHistory } from '../api/message';
import { useMessageStore } from '../store/messageStore';
import { historyItemToChatMessage } from '../socket/mappers';

export function useMessages(convId: string | null) {
  const [loadingHistory, setLoadingHistory] = useState(false);
  const messages = useMessageStore((state) => (convId ? (state.messages.get(convId) ?? []) : []));
  const hasMore = useMessageStore((state) => (convId ? (state.hasMore.get(convId) ?? true) : false));
  const prependHistory = useMessageStore((state) => state.prependHistory);

  const oldestSeq = useMemo(() => messages.find((message) => message.seq)?.seq, [messages]);

  const loadHistory = useCallback(async () => {
    if (!convId || loadingHistory || !hasMore) {
      return;
    }
    setLoadingHistory(true);
    try {
      const page = await getMessageHistory(convId, { endSeq: oldestSeq, limit: 30 });
      prependHistory(convId, page.messages.map(historyItemToChatMessage), page.hasMore);
    } finally {
      setLoadingHistory(false);
    }
  }, [convId, hasMore, loadingHistory, oldestSeq, prependHistory]);

  return { messages, hasMore, loadingHistory, loadHistory };
}
