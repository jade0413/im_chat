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
    case 'group.created':
      return parseGroupNotification(content.payload, (p) => `${p.name || '群组'} 已创建`);
    case 'group.member_added':
      return parseGroupNotification(content.payload, (p) => {
        const ids = (p.user_ids as unknown[]) ?? [];
        return ids.length === 1
          ? `新成员加入了群组`
          : `${ids.length} 位新成员加入了群组`;
      });
    case 'group.member_removed':
      return '有成员已退出群组';
    case 'group.name_changed':
      return parseGroupNotification(content.payload, (p) =>
        p.new ? `群名称已改为「${p.new}」` : '群名称已更新',
      );
    case 'friend.request':
      return parseGroupNotification(content.payload, (p) => {
        const name = (p.from_nickname as string) || '有人';
        const note = (p.note as string) || '';
        return note ? `${name} 请求加你为好友：${note}` : `${name} 请求加你为好友`;
      });
    case 'friend.accepted':
      return parseGroupNotification(
        content.payload,
        (p) => `${(p.to_nickname as string) || '对方'} 已通过你的好友申请`,
      );
    case 'friend.added':
      return parseGroupNotification(
        content.payload,
        (p) => `${(p.from_nickname as string) || '有人'} 已添加你为好友`,
      );
    case 'cs.pending':
      return parseGroupNotification(content.payload, (p) => {
        const count = Number(p.count ?? 0);
        return count > 0 ? `你有 ${count} 个待接待会话` : '你有待接待会话';
      });
    default:
      return '系统通知';
  }
}

function parseGroupNotification(
  payload: string | undefined,
  formatter: (p: Record<string, unknown>) => string,
): string {
  if (!payload) return '系统通知';
  try {
    return formatter(JSON.parse(payload) as Record<string, unknown>);
  } catch {
    return '系统通知';
  }
}
