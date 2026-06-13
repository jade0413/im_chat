import { useEffect, useRef } from 'react';
import { Spin } from 'antd';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useMessages } from '../../../hooks/useMessages';
import { useInfiniteScroll } from '../../../hooks/useInfiniteScroll';
import { MessageBubble } from './MessageBubble';

export function MessageList({ convId }: { convId: string }) {
  const parentRef = useRef<HTMLDivElement>(null);
  const { messages, hasMore, loadingHistory, loadHistory } = useMessages(convId);

  // F2：进入会话时若无消息则立即加载历史
  useEffect(() => {
    if (messages.length === 0 && hasMore) {
      void loadHistory();
    }
    // convId 变化时重置滚动位置
    parentRef.current?.scrollTo({ top: parentRef.current.scrollHeight });
  }, [convId]); // eslint-disable-line react-hooks/exhaustive-deps

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 82,
    overscan: 10,
  });

  useInfiniteScroll(parentRef, loadHistory);

  // 新消息/会话切换时滚动到底部。
  // ⚠️ virtualizer 每次内部状态变化都是新引用，放进 deps 会死循环：
  //    scrollToIndex → virtualizer 内部 setState → 新引用 → effect 重跑 → 无限循环。
  //    改用 ref 持有最新实例，effect 只依赖 messages.length。
  const virtualizerRef = useRef(virtualizer);
  virtualizerRef.current = virtualizer;
  useEffect(() => {
    if (messages.length > 0) {
      virtualizerRef.current.scrollToIndex(messages.length - 1, { align: 'end' });
    }
  }, [messages.length]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div ref={parentRef} className="message-list">
      {loadingHistory && (
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <Spin size="small" />
        </div>
      )}
      {messages.length === 0 && !loadingHistory ? (
        <div className="empty-state">
          <div className="empty-state-inner">
            <div className="empty-state-title">还没有消息</div>
            <div>发送第一条消息后，这里会开始展示会话记录。</div>
          </div>
        </div>
      ) : (
        <div style={{ height: virtualizer.getTotalSize(), position: 'relative' }}>
          {virtualizer.getVirtualItems().map((item) => (
            <div
              key={item.key}
              data-index={item.index}
              ref={virtualizer.measureElement}
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                width: '100%',
                transform: `translateY(${item.start}px)`,
              }}
            >
              <MessageBubble message={messages[item.index]} />
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
