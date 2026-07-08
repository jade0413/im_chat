import { useState } from 'react';
import { App as AntApp, Button, Dropdown } from 'antd';
import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  FileTextOutlined,
  MoreOutlined,
  SettingOutlined,
  SyncOutlined,
  UserSwitchOutlined,
} from '@ant-design/icons';
import { ApiError } from '../../../api/client';
import { claimCsConversation, getCsConversation, resolveCsConversation } from '../../../api/cs';
import { AppAvatar } from '../../../components/AppAvatar';
import { imSocket } from '../../../socket/ImSocket';
import { useAuthStore } from '../../../store/authStore';
import { useConvStore } from '../../../store/convStore';
import { useUiStore } from '../../../store/uiStore';
import { useUserStore } from '../../../store/userStore';
import { idToString } from '../../../utils/id';
import { CsConversationDrawer } from './CsConversationDrawer';
import { GroupInfoDrawer } from './GroupInfoDrawer';

const CONV_TYPE_C2C = 1;
const CONV_TYPE_GROUP = 2;
const CONV_TYPE_CS = 3;

export function ChatHeader({ convId }: { convId: string }) {
  const { message } = AntApp.useApp();
  const conv = useConvStore((state) => state.conversations.get(convId));
  const setActive = useConvStore((state) => state.setActive);
  const upsertConv = useConvStore((state) => state.upsertConv);
  const agentStatus = useAuthStore((state) => Number(state.user?.agentStatus ?? 0));
  const requestCsRefresh = useUiStore((state) => state.requestCsRefresh);
  const [groupInfoOpen, setGroupInfoOpen] = useState(false);
  const [csInfoOpen, setCsInfoOpen] = useState(false);
  const [actionLoading, setActionLoading] = useState<'claim' | 'resolve' | null>(null);
  // C2C：订阅 userStore，昵称加载完成后自动刷新标题
  const peerProfile = useUserStore((state) =>
    conv?.type === CONV_TYPE_C2C && conv.peerUserId
      ? state.users.get(conv.peerUserId)
      : undefined,
  );

  if (!conv) {
    return null;
  }

  const currentConv = conv;
  const isGroup = currentConv.type === CONV_TYPE_GROUP;
  const isCs = currentConv.type === CONV_TYPE_CS;
  const displayTitle = peerProfile?.nickname || currentConv.title;
  const displayAvatar = peerProfile?.avatar ?? currentConv.avatar;

  async function refreshCsConv(fallbackStatus?: string) {
    try {
      const item = await getCsConversation(convId);
      upsertConv({
        ...currentConv,
        title: item.visitorName || currentConv.title,
        peerUserId: idToString(item.visitorUserId),
        maxSeq: idToString(item.maxSeq),
        lastMsgAbstract: item.lastMsgAbstract || currentConv.lastMsgAbstract,
        lastMsgTime: idToString(item.lastMsgTimeMs),
        csStatus: idToString(item.csStatus),
        visitorOnline: item.visitorOnline,
        visitorReadSeq: idToString(item.visitorReadSeq ?? 0),
        peerReadSeq: idToString(item.visitorReadSeq ?? currentConv.peerReadSeq ?? 0),
      });
    } catch {
      if (fallbackStatus) {
        upsertConv({ ...currentConv, csStatus: fallbackStatus });
      }
    } finally {
      requestCsRefresh();
    }
  }

  async function handleClaim() {
    setActionLoading('claim');
    try {
      await claimCsConversation(convId);
      message.success('已认领会话');
      await refreshCsConv('2');
    } catch (error) {
      message.error(readableError(error));
    } finally {
      setActionLoading(null);
    }
  }

  async function handleResolve() {
    setActionLoading('resolve');
    try {
      await resolveCsConversation(convId);
      message.success('会话已结单');
      await refreshCsConv('3');
    } catch (error) {
      message.error(readableError(error));
    } finally {
      setActionLoading(null);
    }
  }

  const menuItems = [
    { key: 'sync', icon: <SyncOutlined />, label: '同步消息', onClick: () => imSocket.sendSyncReq() },
    ...(isCs
      ? [
          {
            key: 'cs-info',
            icon: <FileTextOutlined />,
            label: '会话资料',
            onClick: () => setCsInfoOpen(true),
          },
          {
            key: 'claim',
            icon: <UserSwitchOutlined />,
            label: agentStatus === 1 ? '认领会话' : '切换在线后认领',
            disabled: currentConv.csStatus !== '1' || actionLoading !== null || agentStatus !== 1,
            onClick: handleClaim,
          },
          {
            key: 'resolve',
            icon: <CheckCircleOutlined />,
            label: '结单',
            disabled: currentConv.csStatus !== '2' || actionLoading !== null,
            onClick: handleResolve,
          },
        ]
      : []),
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
            <div className="chat-subtitle">{convSubtitle(currentConv)}</div>
          </div>
        </div>
        <Dropdown menu={{ items: menuItems }} trigger={['click']}>
          <Button type="text" icon={<MoreOutlined />} aria-label="更多" />
        </Dropdown>
      </header>

      {isGroup && currentConv.groupId && (
        <GroupInfoDrawer
          open={groupInfoOpen}
          onClose={() => setGroupInfoOpen(false)}
          convId={convId}
          groupId={currentConv.groupId}
        />
      )}
      {isCs && <CsConversationDrawer open={csInfoOpen} onClose={() => setCsInfoOpen(false)} convId={convId} />}
    </>
  );
}

function convSubtitle(conv: import('../../../store/types').Conversation) {
  if (conv.type === 2) {
    return '群聊';
  }
  if (conv.type === 3) {
    const base = `客服会话 · ${csStatusLabel(conv.csStatus)}`;
    return conv.visitorOnline ? `${base} · 访客在线` : base;
  }
  if (conv.type === 4) {
    return '系统通知';
  }
  return '单聊';
}

function csStatusLabel(status?: string) {
  switch (status) {
    case '1': return '待接待';
    case '2': return '接待中';
    case '3': return '已结单';
    default: return '状态未知';
  }
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '操作失败，请稍后重试';
}
