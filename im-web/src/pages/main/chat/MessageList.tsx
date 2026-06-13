import { useEffect, useRef } from 'react';
import { Spin } from 'antd';
import { useVirtualizer } from '@tanstack/react-virtual';
import { useMessages } from '../../../hooks/useMessages';
import { useInfiniteScroll } from '../../../hooks/useInfiniteScroll';
import { MessageBubble } from './MessageBubble';

export function MessageList({ convId }: { convId: string }) {
  const parentRef = useRef<HTMLDivElement>(null);
  const { messages, loadingHistory, loadHistory } = useMessages(convId);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 82,
    overscan: 10,
  });

  useInfiniteScroll(parentRef, loadHistory);

  useEffect(() => {
    if (messages.length > 0) {
      virtualizer.scrollToIndex(messages.length - 1, { align: 'end' });
    }
  }, [messages.length, virtualizer]);

  return (
    <div ref={parentRef} className="message-list">
      {loadingHistory && (
        <div style={{ textAlign: 'center', marginBottom: 12 }}>
          <Spin size="small" />
        </div>
      )}
      {messages.length === 0 ? (
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
