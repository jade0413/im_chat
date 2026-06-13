import { AppAvatar } from '../../../components/AppAvatar';
import { useAuthStore } from '../../../store/authStore';
import type { ChatMessage } from '../../../store/types';
import { formatMessageClock } from '../../../utils/time';
import { TextBubble } from './bubbles/TextBubble';
import { FileBubble } from './bubbles/FileBubble';
import { ImageBubble } from './bubbles/ImageBubble';
import { VoiceBubble } from './bubbles/VoiceBubble';
import { SystemBubble } from './bubbles/SystemBubble';

export function MessageBubble({ message }: { message: ChatMessage }) {
  const userId = useAuthStore((state) => String(state.user?.id ?? ''));
  const isSelf = message.sender.userId === userId;

  if (message.status === 'revoked' || message.content.kind === 'notification') {
    return <SystemBubble content={message.content} />;
  }

  return (
    <div className={`message-row ${isSelf ? 'self' : ''}`}>
      <AppAvatar name={message.sender.nickname} src={message.sender.avatar} size={34} />
      <div className="message-stack">
        <div className="message-meta">{isSelf ? '我' : message.sender.nickname}</div>
        <div className="message-bubble">{renderContent(message)}</div>
        <div className="message-footer">
          <span>{formatMessageClock(message.sendTime)}</span>
          {isSelf && <span>{statusLabel(message.status)}</span>}
        </div>
      </div>
    </div>
  );
}

function renderContent(message: ChatMessage) {
  const content = message.content;
  switch (content.kind) {
    case 'text':
      return <TextBubble text={content.text} />;
    case 'image':
      return <ImageBubble content={content} />;
    case 'voice':
      return <VoiceBubble content={content} />;
    case 'file':
    case 'video':
      return <FileBubble content={content} />;
    case 'custom':
      return <TextBubble text={content.payload || `[${content.customType}]`} />;
    default:
      return null;
  }
}

function statusLabel(status: ChatMessage['status']) {
  switch (status) {
    case 'sending':
      return '发送中';
    case 'failed':
      return '失败';
    case 'sent':
      return '已发送';
    default:
      return '';
  }
}
