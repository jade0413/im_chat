import { Badge, Tag } from 'antd';
import { AppAvatar } from '../../../components/AppAvatar';
import { useConvStore } from '../../../store/convStore';
import { useUserStore } from '../../../store/userStore';
import type { Conversation } from '../../../store/types';
import { formatChatTime } from '../../../utils/time';

const CONV_TYPE_C2C = 1;
const CONV_TYPE_CS = 3;

export function ConvItem({ conv }: { conv: Conversation }) {
  const activeConvId = useConvStore((state) => state.activeConvId);
  const setActive = useConvStore((state) => state.setActive);
  // C2C 会话：后端 title = peerUserId 字符串，从 userStore 取真实昵称/头像
  const peerProfile = useUserStore((state) =>
    conv.type === CONV_TYPE_C2C && conv.peerUserId
      ? state.users.get(conv.peerUserId)
      : undefined,
  );
  const displayTitle = peerProfile?.nickname || conv.title;
  const displayAvatar = peerProfile?.avatar ?? conv.avatar;
  const unread = unreadCount(conv.maxSeq, conv.readSeq);

  return (
    <button className={`conv-item ${activeConvId === conv.convId ? 'active' : ''}`} type="button" onClick={() => setActive(conv.convId)}>
      <Badge dot={unread > 0} offset={[-2, 34]}>
        <AppAvatar name={displayTitle} src={displayAvatar} />
      </Badge>
      <div style={{ minWidth: 0 }}>
        <div className="conv-name-line">
          <span className="conv-name">{displayTitle}</span>
          {conv.type === CONV_TYPE_CS && <Tag color={csStatusColor(conv.csStatus)}>{csStatusLabel(conv.csStatus)}</Tag>}
          {conv.type === CONV_TYPE_CS && conv.visitorOnline && <Tag color="green">访客在线</Tag>}
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

function csStatusLabel(status?: string): string {
  switch (status) {
    case '1': return '待接待';
    case '2': return '接待中';
    case '3': return '已结单';
    default: return '客服';
  }
}

function csStatusColor(status?: string): string {
  switch (status) {
    case '1': return 'orange';
    case '2': return 'processing';
    case '3': return 'default';
    default: return 'blue';
  }
}

function unreadCount(maxSeq: string, readSeq: string): number {
  const value = BigInt(maxSeq || 0) - BigInt(readSeq || 0);
  if (value <= 0n) {
    return 0;
  }
  return Number(value > 99n ? 100n : value);
}
