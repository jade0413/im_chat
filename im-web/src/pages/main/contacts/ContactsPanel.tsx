import { useEffect, useMemo, useState } from 'react';
import { App as AntApp, Avatar, Badge, Button, Input, Spin, Tooltip } from 'antd';
import { SearchOutlined, UserAddOutlined, UsergroupAddOutlined } from '@ant-design/icons';
import { openC2cConv } from '../../../api/conv';
import type { FriendItem } from '../../../api/types';
import { AddFriendModal } from '../../../components/AddFriendModal';
import { FriendRequestsDrawer } from '../../../components/FriendRequestsDrawer';
import { useConvStore } from '../../../store/convStore';
import { useFriendStore } from '../../../store/friendStore';
import { useUiStore } from '../../../store/uiStore';
import { idToString } from '../../../utils/id';

function friendDisplayName(f: FriendItem): string {
  return f.remark?.trim() || f.nickname;
}

export function ContactsPanel() {
  const { message } = AntApp.useApp();
  const friends = useFriendStore((state) => state.friends);
  const loadingFriends = useFriendStore((state) => state.loadingFriends);
  const pendingCount = useFriendStore((state) => state.pendingCount);
  const loadFriends = useFriendStore((state) => state.loadFriends);
  const loadRequests = useFriendStore((state) => state.loadRequests);
  const upsertConv = useConvStore((state) => state.upsertConv);
  const setActive = useConvStore((state) => state.setActive);
  const setActiveTab = useUiStore((state) => state.setActiveTab);

  const [keyword, setKeyword] = useState('');
  const [addOpen, setAddOpen] = useState(false);
  const [requestsOpen, setRequestsOpen] = useState(false);
  const [opening, setOpening] = useState<string | null>(null);

  useEffect(() => {
    void loadFriends();
    void loadRequests();
  }, [loadFriends, loadRequests]);

  const filtered = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    const list = [...friends].sort((a, b) => friendDisplayName(a).localeCompare(friendDisplayName(b), 'zh'));
    if (!kw) return list;
    return list.filter(
      (f) =>
        friendDisplayName(f).toLowerCase().includes(kw) ||
        (f.username ?? '').toLowerCase().includes(kw),
    );
  }, [friends, keyword]);

  async function openChat(friend: FriendItem) {
    const userId = idToString(friend.userId);
    setOpening(userId);
    try {
      const conv = await openC2cConv(friend.userId);
      const convId = idToString(conv.convId);
      upsertConv({
        convId,
        type: conv.type,
        title: friendDisplayName(friend),
        avatar: friend.avatar,
        peerUserId: userId,
        maxSeq: idToString(conv.maxSeq),
        readSeq: idToString(conv.readSeq),
        pinned: false,
        muted: false,
        lastMsgAbstract: '',
      });
      setActive(convId);
      setActiveTab('chats'); // 从通讯录发消息 → 切回消息 tab 显示聊天
    } catch {
      message.error('打开会话失败，请重试');
    } finally {
      setOpening(null);
    }
  }

  return (
    <section className="conv-panel">
      <header className="conv-panel-header">
        <div className="conv-title-row">
          <h2 className="section-title">通讯录</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Tooltip title="添加好友">
              <Button
                type="text"
                size="small"
                icon={<UserAddOutlined />}
                aria-label="添加好友"
                onClick={() => setAddOpen(true)}
              />
            </Tooltip>
          </div>
        </div>
        <Input
          prefix={<SearchOutlined />}
          placeholder="搜索好友"
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </header>

      <div className="conv-list">
        <button type="button" className="contacts-entry" onClick={() => setRequestsOpen(true)}>
          <Badge count={pendingCount} size="small" offset={[2, 2]}>
            <span className="contacts-entry-icon new-friend">
              <UsergroupAddOutlined />
            </span>
          </Badge>
          <span className="contacts-entry-label">新的朋友</span>
          <span className="contacts-entry-hint">{pendingCount > 0 ? `${pendingCount} 条待处理` : ''}</span>
        </button>

        <div className="contacts-section-title">我的好友 · {friends.length}</div>

        {loadingFriends && friends.length === 0 ? (
          <div className="panel-loading"><Spin /></div>
        ) : filtered.length === 0 ? (
          <div style={{ padding: 16, color: '#6b778c' }}>
            {keyword ? '没有匹配的好友' : '还没有好友，点右上角添加'}
          </div>
        ) : (
          filtered.map((f) => {
            const userId = idToString(f.userId);
            return (
              <button
                key={userId}
                type="button"
                className="contacts-friend-row"
                onClick={() => openChat(f)}
                disabled={opening === userId}
              >
                {f.avatar ? (
                  <Avatar src={f.avatar} size={40} />
                ) : (
                  <Avatar size={40} style={{ backgroundColor: '#1677ff' }}>
                    {friendDisplayName(f).charAt(0)}
                  </Avatar>
                )}
                <div className="contacts-friend-meta">
                  <span className="contacts-friend-name">{friendDisplayName(f)}</span>
                  {f.username && <span className="contacts-friend-sub">@{f.username}</span>}
                </div>
                {opening === userId && <Spin size="small" />}
              </button>
            );
          })
        )}
      </div>

      <AddFriendModal open={addOpen} onClose={() => setAddOpen(false)} />
      <FriendRequestsDrawer open={requestsOpen} onClose={() => setRequestsOpen(false)} />
    </section>
  );
}
