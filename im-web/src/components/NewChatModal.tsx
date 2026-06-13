import { useState, useCallback } from 'react';
import { App as AntApp, Avatar, Input, List, Modal, Spin } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import { openC2cConv } from '../api/conv';
import { searchUsers } from '../api/user';
import type { UserPublicProfile } from '../api/types';
import { useConvStore } from '../store/convStore';
import { idToString } from '../utils/id';

interface NewChatModalProps {
  open: boolean;
  onClose: () => void;
}

export function NewChatModal({ open, onClose }: NewChatModalProps) {
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<UserPublicProfile[]>([]);
  const [searching, setSearching] = useState(false);
  const [opening, setOpening] = useState<string | null>(null);
  const { message } = AntApp.useApp();
  const upsertConv = useConvStore((state) => state.upsertConv);
  const setActive = useConvStore((state) => state.setActive);

  const handleSearch = useCallback(async (value: string) => {
    const kw = value.trim();
    if (!kw) {
      setResults([]);
      return;
    }
    setSearching(true);
    try {
      const users = await searchUsers(kw);
      setResults(users);
    } catch {
      message.error('搜索失败，请重试');
    } finally {
      setSearching(false);
    }
  }, [message]);

  const handleOpenChat = useCallback(async (user: UserPublicProfile) => {
    const userId = idToString(user.id);
    setOpening(userId);
    try {
      const conv = await openC2cConv(user.id);
      const convId = idToString(conv.convId);
      upsertConv({
        convId,
        type: conv.type,
        title: user.nickname,
        avatar: user.avatar,
        peerUserId: userId,
        maxSeq: idToString(conv.maxSeq),
        readSeq: idToString(conv.readSeq),
        pinned: false,
        muted: false,
        lastMsgAbstract: '',
      });
      setActive(convId);
      handleClose();
    } catch {
      message.error('打开会话失败，请重试');
    } finally {
      setOpening(null);
    }
  }, [upsertConv, setActive, message]);

  function handleClose() {
    setKeyword('');
    setResults([]);
    onClose();
  }

  return (
    <Modal
      title="发起单聊"
      open={open}
      onCancel={handleClose}
      footer={null}
      width={400}
      destroyOnClose
    >
      <Input.Search
        placeholder="搜索用户昵称或账号"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        onSearch={handleSearch}
        loading={searching}
        enterButton
        autoFocus
        style={{ marginBottom: 12 }}
      />
      {searching ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : (
        <List
          dataSource={results}
          locale={{ emptyText: keyword ? '未找到匹配用户' : '输入昵称或账号搜索' }}
          renderItem={(user) => {
            const userId = idToString(user.id);
            return (
              <List.Item
                style={{ cursor: 'pointer', padding: '8px 4px' }}
                onClick={() => handleOpenChat(user)}
              >
                <List.Item.Meta
                  avatar={
                    user.avatar ? (
                      <Avatar src={user.avatar} />
                    ) : (
                      <Avatar icon={<UserOutlined />}>
                        {user.nickname.charAt(0)}
                      </Avatar>
                    )
                  }
                  title={
                    <span>
                      {user.nickname}
                      {opening === userId && (
                        <Spin size="small" style={{ marginLeft: 8 }} />
                      )}
                    </span>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}
    </Modal>
  );
}
