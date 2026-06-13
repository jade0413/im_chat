import { Badge } from 'antd';
import { AppAvatar } from '../../../components/AppAvatar';
import { useConvStore } from '../../../store/convStore';
import type { Conversation } from '../../../store/types';
import { formatChatTime } from '../../../utils/time';

export function ConvItem({ conv }: { conv: Conversation }) {
  const activeConvId = useConvStore((state) => state.activeConvId);
  const setActive = useConvStore((state) => state.setActive);
  const unread = unreadCount(conv.maxSeq, conv.readSeq);

  return (
    <button className={`conv-item ${activeConvId === conv.convId ? 'active' : ''}`} type="button" onClick={() => setActive(conv.convId)}>
      <Badge dot={unread > 0} offset={[-2, 34]}>
        <AppAvatar name={conv.title} src={conv.avatar} />
      </Badge>
      <div style={{ minWidth: 0 }}>
        <div className="conv-name-line">
          <span className="conv-name">{conv.title}</span>
          {conv.muted && <span style={{ color: '#9aa4b2', fontSize: 12 }}>免打扰</span>}
        </div>
        <div className="conv-preview">{conv.lastMsgAbstract || '暂无消息'}</div>
      </div>
      <div style={{ display: 'grid', justifyItems: 'end', gap: 6 }}>
        <span className="conv-time">{formatChatTime(conv.lastMsgTime)}</span>
        {unread > 0 && <span className="unread-dot">{unread > 99 ? '99+' : unread}</span>}
      </div>
    </button>
  );
}

function unreadCount(maxSeq: string, readSeq: string): number {
  const value = BigInt(maxSeq || 0) - BigInt(readSeq || 0);
  if (value <= 0n) {
    return 0;
  }
  return Number(value > 99n ? 100n : value);
}
