import { useEffect, useLayoutEffect, useMemo, useRef } from 'react';
import { Spin } from 'antd';
import { useMessages } from '../../../hooks/useMessages';
import { useInfiniteScroll } from '../../../hooks/useInfiniteScroll';
import { useConvStore } from '../../../store/convStore';
import { compareIdLike } from '../../../utils/id';
import { MessageBubble } from './MessageBubble';
import { TimeDivider, TIME_GAP_MS } from './TimeDivider';
import type { ChatMessage } from '../../../store/types';

type ListItem =
  | { type: 'message'; message: ChatMessage }
  | { type: 'divider'; timestamp: string };

export function MessageList({ convId }: { convId: string }) {
  const parentRef = useRef<HTMLDivElement>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const { messages, hasMore, loadingHistory, loadHistory, loadLatest } = useMessages(convId);
  const convMaxSeq = useConvStore((state) => state.conversations.get(convId)?.maxSeq ?? '0');

  // F2：进入会话时若无消息则加载历史；若本地消息落后于 convMaxSeq 则补齐
  useEffect(() => {
    const localMaxSeq = messages.length > 0 ? (messages[messages.length - 1].seq ?? '0') : '0';
    const hasGap = compareIdLike(convMaxSeq, localMaxSeq) > 0;
    if (messages.length === 0 && hasMore) {
      void loadHistory();
    } else if (hasGap) {
      void loadLatest();
    }
    // 切换会话时立即滚到底
    scrollToBottom('auto');
  }, [convId]); // eslint-disable-line react-hooks/exhaustive-deps

  // 在超过 5 分钟间隔的消息之间插入时间分割线
  const items = useMemo<ListItem[]>(() => {
    const result: ListItem[] = [];
    let prevTime = 0;
    for (const message of messages) {
      const ts = Number(message.sendTime);
      if (ts - prevTime > TIME_GAP_MS) {
        result.push({ type: 'divider', timestamp: message.sendTime });
      }
      result.push({ type: 'message', message });
      prevTime = ts;
    }
    return result;
  }, [messages]);

  const lastMessageSignature = useMemo(() => {
    const last = messages[messages.length - 1];
    if (!last) return '';
    return `${messages.length}:${last.clientMsgId}`;
  }, [messages]);

  /**
   * 只在“最后一条消息变化”时滚到底。
   * 加载历史是往顶部 prepend，最后一条不变，不能把用户强行拉到底。
   */
  useLayoutEffect(() => {
    if (lastMessageSignature) {
      scrollToBottom('auto');
    }
  }, [lastMessageSignature]);

  // 上拉加载更多历史
  useInfiniteScroll(parentRef, loadHistory);

  function scrollToBottom(behavior: ScrollBehavior = 'smooth') {
    const el = parentRef.current;
    if (el) {
      el.scrollTo({ top: el.scrollHeight, behavior });
    }
  }

  return (
    <div ref={parentRef} className="message-list">
      {loadingHistory && (
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <Spin size="small" />
        </div>
      )}
      {items.length === 0 && !loadingHistory ? (
        <div className="empty-state">
          <div className="empty-state-inner">
            <div className="empty-state-title">还没有消息</div>
            <div>发送第一条消息后，这里会开始展示会话记录。</div>
          </div>
        </div>
      ) : (
        items.map((item) =>
          item.type === 'divider' ? (
            <TimeDivider key={`d-${item.timestamp}`} timestamp={item.timestamp} />
          ) : (
            <MessageBubble key={item.message.clientMsgId} message={item.message} />
          ),
        )
      )}
      <div ref={endRef} aria-hidden="true" />
    </div>
  );
}
