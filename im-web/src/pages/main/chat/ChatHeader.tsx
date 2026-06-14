import { useState } from 'react';
import { Button, Dropdown } from 'antd';
import { ArrowLeftOutlined, MoreOutlined, SettingOutlined, SyncOutlined } from '@ant-design/icons';
import { AppAvatar } from '../../../components/AppAvatar';
import { imSocket } from '../../../socket/ImSocket';
import { useConvStore } from '../../../store/convStore';
import { useUserStore } from '../../../store/userStore';
import { GroupInfoDrawer } from './GroupInfoDrawer';

const CONV_TYPE_C2C = 1;
const CONV_TYPE_GROUP = 2;

export function ChatHeader({ convId }: { convId: string }) {
  const conv = useConvStore((state) => state.conversations.get(convId));
  const setActive = useConvStore((state) => state.setActive);
  const [groupInfoOpen, setGroupInfoOpen] = useState(false);
  // C2C：订阅 userStore，昵称加载完成后自动刷新标题
  const peerProfile = useUserStore((state) =>
    conv?.type === CONV_TYPE_C2C && conv.peerUserId
      ? state.users.get(conv.peerUserId)
      : undefined,
  );

  if (!conv) {
    return null;
  }

  const isGroup = conv.type === CONV_TYPE_GROUP;
  const displayTitle = peerProfile?.nickname || conv.title;
  const displayAvatar = peerProfile?.avatar ?? conv.avatar;

  const menuItems = [
    { key: 'sync', icon: <SyncOutlined />, label: '同步消息', onClick: () => imSocket.sendSyncReq() },
    ...(isGroup
      ? [{ key: 'group-info', icon: <SettingOutlined />, label: '群设置', onClick: () => setGroupInfoOpen(true) }]
      : []),
  ];

  return (
    <>
      <header className="chat-header">
        <div className="chat-header-title-line">
          <Button className="mobile-back" type="text" icon={<ArrowLeftOutlined />} onClick={() => setActive(null)} />
          <AppAvatar name={displayTitle} src={displayAvatar} size={38} />
          <div style={{ minWidth: 0 }}>
            <div className="chat-title">{displayTitle}</div>
            <div className="chat-subtitle">{convSubtitle(conv.type, conv.csStatus)}</div>
          </div>
        </div>
        <Dropdown menu={{ items: menuItems }} trigger={['click']}>
          <Button type="text" icon={<MoreOutlined />} aria-label="更多" />
        </Dropdown>
      </header>

      {isGroup && conv.groupId && (
        <GroupInfoDrawer
          open={groupInfoOpen}
          onClose={() => setGroupInfoOpen(false)}
          convId={convId}
          groupId={conv.groupId}
        />
      )}
    </>
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
