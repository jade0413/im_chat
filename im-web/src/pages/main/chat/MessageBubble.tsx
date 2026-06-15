import { App as AntApp, Button, Dropdown, Tooltip } from 'antd';
import { CheckOutlined, ClockCircleOutlined, CopyOutlined, DeleteOutlined, ReloadOutlined, WarningOutlined } from '@ant-design/icons';
import { AppAvatar } from '../../../components/AppAvatar';
import { useAuthStore } from '../../../store/authStore';
import { useUserStore } from '../../../store/userStore';
import { useMessageStore } from '../../../store/messageStore';
import { useConvStore } from '../../../store/convStore';
import { revokeMessage } from '../../../api/message';
import { sendFriendRequest } from '../../../api/friend';
import { imSocket } from '../../../socket/ImSocket';
import { compareIdLike } from '../../../utils/id';
import type { ChatMessage, MessageContent } from '../../../store/types';
import { formatMessageClock } from '../../../utils/time';
import { TextBubble } from './bubbles/TextBubble';
import { FileBubble } from './bubbles/FileBubble';
import { ImageBubble } from './bubbles/ImageBubble';
import { VoiceBubble } from './bubbles/VoiceBubble';
import { SystemBubble } from './bubbles/SystemBubble';

export function MessageBubble({ message }: { message: ChatMessage }) {
  const { message: antMessage } = AntApp.useApp();
  const userId = useAuthStore((state) => String(state.user?.id ?? ''));
  const isSelf = message.sender.userId === userId;
  const cachedProfile = useUserStore((state) => state.users.get(message.sender.userId));
  const displayName = cachedProfile?.nickname || message.sender.nickname;
  const displayAvatar = cachedProfile?.avatar ?? message.sender.avatar;
  const peerReadSeq = useConvStore((state) => state.conversations.get(message.convId)?.peerReadSeq);
  const peerUserId = useConvStore((state) => state.conversations.get(message.convId)?.peerUserId);
  const peerHasRead =
    isSelf &&
    message.status === 'sent' &&
    message.seq != null &&
    peerReadSeq != null &&
    compareIdLike(peerReadSeq, message.seq) >= 0;

  if (message.status === 'revoked' || message.content.kind === 'notification') {
    return <SystemBubble content={message.content} />;
  }

  const canRevoke = isSelf && message.status === 'sent' && message.seq;
  const isFailed = isSelf && message.status === 'failed';
  const failReason = isFailed ? sendFailReason(message.failCode) : undefined;
  const needAddFriend = isFailed && message.failCode === 2002 && !!peerUserId;

  const menuItems = [
    ...(message.content.kind === 'text'
      ? [
          {
            key: 'copy',
            icon: <CopyOutlined />,
            label: '复制文本',
            onClick: async () => {
              try {
                await navigator.clipboard.writeText(message.content.kind === 'text' ? message.content.text : '');
                void antMessage.success('已复制');
              } catch {
                void antMessage.error('复制失败');
              }
            },
          },
        ]
      : []),
    ...(canRevoke
      ? [
          {
            key: 'revoke',
            icon: <DeleteOutlined />,
            label: '撤回',
            danger: true,
            onClick: async () => {
              try {
                await revokeMessage(message.convId, message.seq!);
                useMessageStore.getState().revokeMessage(message.convId, message.seq!);
              } catch {
                void antMessage.error('撤回失败');
              }
            },
          },
        ]
      : []),
  ];

  /**
   * 时间+状态图标：Telegram 风格
   * - 文字气泡：内联在文字末尾（由 TextBubble 处理）
   * - 其他气泡：显示在内容下方，右对齐
   */
  const timeLabel = (
    <>
      <span>{formatMessageClock(message.sendTime)}</span>
      {isSelf && (
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2 }}>
          {statusIcon(message.status, peerHasRead)}
          {isFailed && (
            <Tooltip title={failReason ?? '重试发送'}>
              <Button
                type="text"
                size="small"
                danger
                icon={<ReloadOutlined />}
                style={{ padding: '0 2px', height: 'auto', lineHeight: 1 }}
                onClick={() => imSocket.retryMessage(message.convId, message.clientMsgId)}
              />
            </Tooltip>
          )}
          {needAddFriend && (
            <Button
              type="link"
              size="small"
              style={{ padding: '0 2px', height: 'auto', lineHeight: 1, fontSize: 11 }}
              onClick={async () => {
                try {
                  await sendFriendRequest(peerUserId!, '');
                  void antMessage.success('好友申请已发送');
                } catch {
                  void antMessage.error('好友申请发送失败');
                }
              }}
            >
              加好友
            </Button>
          )}
        </span>
      )}
    </>
  );

  return (
    <Dropdown menu={{ items: menuItems }} trigger={['contextMenu']} disabled={menuItems.length === 0}>
      <div className={`message-row ${isSelf ? 'self' : ''}`}>
        <AppAvatar name={displayName} src={displayAvatar} size={34} />
        <div className="message-stack">
          <div className="message-meta">{isSelf ? '我' : displayName}</div>
          <div className="message-bubble">
            {renderContent(message.content, timeLabel)}
          </div>
        </div>
      </div>
    </Dropdown>
  );
}

/** 发送失败原因：把 MSG_SEND_ACK.code 映射成可读提示（对应后端 ErrorCode）。 */
function sendFailReason(code?: number): string {
  switch (code) {
    case 2001:
      return '对方已将你拉黑，消息无法送达';
    case 2002:
      return '需要先添加对方为好友才能发送';
    default:
      return '发送失败，点击重试';
  }
}

/** 根据内容类型决定时间标签的渲染位置 */
function renderContent(content: MessageContent, timeLabel: React.ReactNode) {
  switch (content.kind) {
    case 'text':
      // 文字气泡：时间内联在文字末尾（Telegram time tail）
      return <TextBubble text={content.text} timeTail={timeLabel} />;

    case 'image':
      return (
        <>
          <ImageBubble content={content} />
          <div className="message-time-below">{timeLabel}</div>
        </>
      );

    case 'voice':
      return (
        <>
          <VoiceBubble content={content} />
          <div className="message-time-below">{timeLabel}</div>
        </>
      );

    case 'file':
    case 'video':
      return (
        <>
          <FileBubble content={content} />
          <div className="message-time-below">{timeLabel}</div>
        </>
      );

    case 'custom':
      return <TextBubble text={content.payload || `[${content.customType}]`} timeTail={timeLabel} />;

    default:
      return null;
  }
}

/**
 * 消息状态图标（Telegram 风格）
 *   sending  → ⏰ 小时钟（几乎不可见，体验等同于"立即显示"）
 *   failed   → ⚠️  红色警告（搭配外层重试按钮）
 *   sent     → ✓  灰色单勾（已送达服务器）
 *   sent+read→ ✓✓ 蓝色双勾（对方已读）
 */
function statusIcon(status: ChatMessage['status'], peerHasRead?: boolean) {
  const style11 = { fontSize: 10 } as const;
  switch (status) {
    case 'sending':
      return <ClockCircleOutlined style={{ ...style11, color: '#b0b8c1' }} />;
    case 'failed':
      return <WarningOutlined style={{ ...style11, color: '#ff4d4f' }} />;
    case 'sent':
      if (peerHasRead) {
        return (
          <span style={{ color: '#1677ff', display: 'inline-flex', alignItems: 'center' }}>
            <CheckOutlined style={style11} />
            <CheckOutlined style={{ ...style11, marginLeft: -3 }} />
          </span>
        );
      }
      return <CheckOutlined style={{ ...style11, color: '#9aa4b2' }} />;
    default:
      return null;
  }
}
