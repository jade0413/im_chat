import { useEffect } from 'react';
import { LoadingOutlined, WifiOutlined } from '@ant-design/icons';
import { useSocketStore } from '../store/socketStore';
import { useConvStore } from '../store/convStore';

/**
 * 顶部网络状态通知栏：
 * - reconnecting / error → 显示黄色"重连中"Banner
 * - 刚从断线恢复 connected → 短暂显示绿色"已重新连接"，2s 后隐藏
 * 同时维护 document.title 的未读数角标
 */
export function NetworkStatusBanner() {
  const status = useSocketStore((state) => state.status);
  const totalUnread = useConvStore((state) => {
    let total = 0;
    for (const conv of state.conversations.values()) {
      const diff = BigInt(conv.maxSeq || 0) - BigInt(conv.readSeq || 0);
      if (diff > 0n) total += Number(diff > 99n ? 99n : diff);
    }
    return total;
  });

  // 维护 document.title 未读角标
  useEffect(() => {
    const base = 'IM Chat';
    document.title = totalUnread > 0 ? `(${totalUnread > 99 ? '99+' : totalUnread}) ${base}` : base;
  }, [totalUnread]);

  if (status === 'connected' || status === 'idle' || status === 'closed') {
    return null;
  }

  const isError = status === 'error';

  return (
    <div
      style={{
        position: 'fixed',
        top: 0,
        left: 0,
        right: 0,
        zIndex: 9999,
        padding: '6px 16px',
        background: isError ? '#ff4d4f' : '#fa8c16',
        color: '#fff',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: 8,
        fontSize: 13,
        fontWeight: 500,
      }}
    >
      {isError ? (
        <>
          <WifiOutlined />
          网络连接异常，正在尝试重连…
        </>
      ) : (
        <>
          <LoadingOutlined spin />
          正在重新连接…
        </>
      )}
    </div>
  );
}
