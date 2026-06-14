import { useState, useCallback } from 'react';
import { App as AntApp, Avatar, Button, Checkbox, Input, List, Modal, Spin } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import { createGroup } from '../api/group';
import { searchUsers } from '../api/user';
import type { UserPublicProfile } from '../api/types';
import { useConvStore } from '../store/convStore';
import { idToString } from '../utils/id';

interface CreateGroupModalProps {
  open: boolean;
  onClose: () => void;
}

export function CreateGroupModal({ open, onClose }: CreateGroupModalProps) {
  const [groupName, setGroupName] = useState('');
  const [keyword, setKeyword] = useState('');
  const [results, setResults] = useState<UserPublicProfile[]>([]);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [selectedProfiles, setSelectedProfiles] = useState<Map<string, UserPublicProfile>>(new Map());
  const [searching, setSearching] = useState(false);
  const [creating, setCreating] = useState(false);
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

  function toggleSelect(user: UserPublicProfile) {
    const userId = idToString(user.id);
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.add(userId);
      }
      return next;
    });
    setSelectedProfiles((prev) => {
      const next = new Map(prev);
      if (next.has(userId)) {
        next.delete(userId);
      } else {
        next.set(userId, user);
      }
      return next;
    });
  }

  const handleCreate = useCallback(async () => {
    const name = groupName.trim();
    if (!name) {
      message.warning('请输入群名称');
      return;
    }
    if (selected.size === 0) {
      message.warning('请至少选择一位成员');
      return;
    }
    setCreating(true);
    try {
      const group = await createGroup({ name, memberUserIds: Array.from(selected) });
      const convId = idToString(group.convId);
      upsertConv({
        convId,
        type: 2, // GROUP
        title: group.name,
        groupId: idToString(group.groupId),
        maxSeq: '0',
        readSeq: '0',
        pinned: false,
        muted: false,
        lastMsgAbstract: '',
      });
      setActive(convId);
      handleClose();
    } catch {
      message.error('创建群组失败，请重试');
    } finally {
      setCreating(false);
    }
  }, [groupName, selected, upsertConv, setActive, message]);

  function handleClose() {
    setGroupName('');
    setKeyword('');
    setResults([]);
    setSelected(new Set());
    setSelectedProfiles(new Map());
    onClose();
  }

  return (
    <Modal
      title="创建群组"
      open={open}
      onCancel={handleClose}
      onOk={handleCreate}
      okText="创建"
      cancelText="取消"
      confirmLoading={creating}
      width={440}
      destroyOnClose
    >
      <div style={{ marginBottom: 12 }}>
        <Input
          placeholder="群名称（必填）"
          value={groupName}
          onChange={(e) => setGroupName(e.target.value)}
          maxLength={64}
        />
      </div>

      {/* 已选成员预览 */}
      {selectedProfiles.size > 0 && (
        <div style={{ marginBottom: 8, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
          {Array.from(selectedProfiles.values()).map((u) => (
            <div
              key={idToString(u.id)}
              style={{ display: 'flex', alignItems: 'center', gap: 4, background: '#f0f5ff', borderRadius: 4, padding: '2px 8px', fontSize: 13 }}
            >
              {u.avatar ? (
                <Avatar src={u.avatar} size={18} />
              ) : (
                <Avatar icon={<UserOutlined />} size={18}>
                  {u.nickname.charAt(0)}
                </Avatar>
              )}
              <span>{u.nickname}</span>
            </div>
          ))}
        </div>
      )}

      <Input.Search
        placeholder="搜索用户昵称或账号"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        onSearch={handleSearch}
        loading={searching}
        enterButton
        style={{ marginBottom: 8 }}
      />

      {searching ? (
        <div style={{ textAlign: 'center', padding: 16 }}>
          <Spin />
        </div>
      ) : (
        <List
          dataSource={results}
          style={{ maxHeight: 260, overflowY: 'auto' }}
          locale={{ emptyText: keyword ? '未找到匹配用户' : '输入昵称或账号搜索' }}
          renderItem={(user) => {
            const userId = idToString(user.id);
            const checked = selected.has(userId);
            return (
              <List.Item
                style={{ cursor: 'pointer', padding: '6px 4px' }}
                onClick={() => toggleSelect(user)}
              >
                <Checkbox checked={checked} style={{ marginRight: 8 }} />
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
                  title={user.nickname}
                />
              </List.Item>
            );
          }}
        />
      )}

      {selected.size > 0 && (
        <div style={{ marginTop: 8, color: '#6b778c', fontSize: 12 }}>
          已选 {selected.size} 位成员（含你自己共 {selected.size + 1} 人）
        </div>
      )}

      {/* "建群"按钮也可用键盘快捷提交，此处没有把 form 多包一层，用 Modal onOk 即可 */}
      <Button
        type="link"
        size="small"
        onClick={() => {
          setResults([]);
          setKeyword('');
        }}
        style={{ padding: 0, marginTop: 4 }}
      >
        清空搜索
      </Button>
    </Modal>
  );
}
