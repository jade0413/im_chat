import { useCallback, useState } from 'react';
import { App as AntApp, Avatar, Button, Input, List, Modal, Spin, Tag } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import { sendFriendRequest } from '../api/friend';
import { searchUsers } from '../api/user';
import type { UserPublicProfile } from '../api/types';
import { useAuthStore } from '../store/authStore';
import { useFriendStore } from '../store/friendStore';
import { idToString } from '../utils/id';

interface AddFriendModalProps {
  open: boolean;
  onClose: () => void;
}

export function AddFriendModal({ open, onClose }: AddFriendModalProps) {
  const { message } = AntApp.useApp();
  const selfId = useAuthStore((state) => state.user?.id);
  const loadFriends = useFriendStore((state) => state.loadFriends);
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<UserPublicProfile[]>([]);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<UserPublicProfile | null>(null);
  const [note, setNote] = useState('');
  const [sending, setSending] = useState(false);

  const handleSearch = useCallback(
    async (value: string) => {
      const kw = value.trim();
      if (!kw) {
        setResults([]);
        return;
      }
      setSearching(true);
      setSelected(null);
      try {
        const users = await searchUsers(kw);
        setResults(users.filter((u) => idToString(u.id) !== idToString(selfId ?? '')));
      } catch {
        message.error('搜索失败，请重试');
      } finally {
        setSearching(false);
      }
    },
    [message, selfId],
  );

  async function handleSend() {
    if (!selected) return;
    setSending(true);
    try {
      const res = await sendFriendRequest(selected.id, note.trim());
      switch (res.result) {
        case 'accepted':
          message.success('已添加为好友');
          void loadFriends();
          break;
        case 'already_friend':
          message.info('你们已经是好友');
          break;
        default:
          message.success('好友申请已发送，等待对方验证');
      }
      handleClose();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '发送失败，请重试');
    } finally {
      setSending(false);
    }
  }

  function handleClose() {
    setKeyword('');
    setResults([]);
    setSelected(null);
    setNote('');
    onClose();
  }

  return (
    <Modal title="添加好友" open={open} onCancel={handleClose} footer={null} width={420} destroyOnClose>
      <Input.Search
        placeholder="输入用户名或完整手机号"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        onSearch={handleSearch}
        loading={searching}
        enterButton
        autoFocus
        style={{ marginBottom: 12 }}
      />

      {selected ? (
        <div className="add-friend-confirm">
          <List.Item.Meta
            avatar={
              selected.avatar ? (
                <Avatar src={selected.avatar} />
              ) : (
                <Avatar icon={<UserOutlined />}>{selected.nickname.charAt(0)}</Avatar>
              )
            }
            title={selected.nickname}
            description={selected.username ? `@${selected.username}` : '未设置用户名'}
          />
          <Input.TextArea
            placeholder="附言（对方可见，可留空）"
            value={note}
            maxLength={128}
            showCount
            autoSize={{ minRows: 2, maxRows: 4 }}
            onChange={(e) => setNote(e.target.value)}
            style={{ margin: '12px 0' }}
          />
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button onClick={() => setSelected(null)}>返回</Button>
            <Button type="primary" loading={sending} onClick={handleSend}>
              发送申请
            </Button>
          </div>
        </div>
      ) : searching ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : (
        <List
          dataSource={results}
          locale={{ emptyText: keyword ? '未找到匹配用户' : '按用户名或完整手机号精确查找' }}
          renderItem={(user) => (
            <List.Item
              actions={[
                <Button key="add" type="link" onClick={() => setSelected(user)}>
                  添加
                </Button>,
              ]}
            >
              <List.Item.Meta
                avatar={
                  user.avatar ? (
                    <Avatar src={user.avatar} />
                  ) : (
                    <Avatar icon={<UserOutlined />}>{user.nickname.charAt(0)}</Avatar>
                  )
                }
                title={
                  <span>
                    {user.nickname}
                    {user.verifiedType > 0 && (
                      <Tag color="blue" style={{ marginLeft: 6 }}>
                        蓝V
                      </Tag>
                    )}
                  </span>
                }
                description={user.username ? `@${user.username}` : undefined}
              />
            </List.Item>
          )}
        />
      )}
    </Modal>
  );
}
