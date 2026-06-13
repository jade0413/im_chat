import { Button, Dropdown } from 'antd';
import { ArrowLeftOutlined, MoreOutlined, SyncOutlined } from '@ant-design/icons';
import { AppAvatar } from '../../../components/AppAvatar';
import { imSocket } from '../../../socket/ImSocket';
import { useConvStore } from '../../../store/convStore';

export function ChatHeader({ convId }: { convId: string }) {
  const conv = useConvStore((state) => state.conversations.get(convId));
  const setActive = useConvStore((state) => state.setActive);

  if (!conv) {
    return null;
  }

  return (
    <header className="chat-header">
      <div className="chat-header-title-line">
        <Button className="mobile-back" type="text" icon={<ArrowLeftOutlined />} onClick={() => setActive(null)} />
        <AppAvatar name={conv.title} src={conv.avatar} size={38} />
        <div style={{ minWidth: 0 }}>
          <div className="chat-title">{conv.title}</div>
          <div className="chat-subtitle">{convSubtitle(conv.type, conv.csStatus)}</div>
        </div>
      </div>
      <Dropdown
        menu={{
          items: [
            { key: 'sync', icon: <SyncOutlined />, label: '同步消息', onClick: () => imSocket.sendSyncReq() },
          ],
        }}
        trigger={['click']}
      >
        <Button type="text" icon={<MoreOutlined />} aria-label="更多" />
      </Dropdown>
    </header>
  );
}

function convSubtitle(type: number, csStatus?: string) {
  if (type === 2) {
    return '群聊';
  }
  if (type === 3) {
    return `客服会话${csStatus ? ` · ${csStatus}` : ''}`;
  }
  return '单聊';
}
