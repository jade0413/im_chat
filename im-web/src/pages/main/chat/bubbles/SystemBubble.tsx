import type { MessageContent } from '../../../../store/types';

export function SystemBubble({ content }: { content: MessageContent }) {
  return <div className="system-message">{systemText(content)}</div>;
}

function systemText(content: MessageContent): string {
  if (content.kind !== 'notification') {
    return '';
  }
  switch (content.eventType) {
    case 'message.revoked':
      return '一条消息已撤回';
    case 'message.unsupported':
      return '收到一条暂不支持的消息';
    case 'message.empty':
      return '收到一条空消息';
    default:
      return content.payload || '系统通知';
  }
}
