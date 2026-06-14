import { useCallback, useEffect, useState } from 'react';
import { App as AntApp, Avatar, Button, Drawer, Input, List, Popconfirm, Spin, Tag, Typography } from 'antd';
import { CrownOutlined, DeleteOutlined, PlusOutlined, UserOutlined } from '@ant-design/icons';
import { addGroupMembers, getGroupMembers, removeGroupMember, renameGroup } from '../../../api/group';
import { searchUsers } from '../../../api/user';
import type { GroupMemberItem, UserPublicProfile } from '../../../api/types';
import { useAuthStore } from '../../../store/authStore';
import { useConvStore } from '../../../store/convStore';
import { useUserStore } from '../../../store/userStore';
import { idToString } from '../../../utils/id';

interface GroupInfoDrawerProps {
  open: boolean;
  onClose: () => void;
  convId: string;
  groupId: string;
}

const ROLE_OWNER = 3;
const ROLE_ADMIN = 2;

export function GroupInfoDrawer({ open, convId, groupId, onClose }: GroupInfoDrawerProps) {
  const { message } = AntApp.useApp();
  const conv = useConvStore((state) => state.conversations.get(convId));
  const upsertConv = useConvStore((state) => state.upsertConv);
  const myUserId = useAuthStore((state) => String(state.user?.id ?? ''));
  const ensureUsers = useUserStore.getState().ensureUsers;
  const getUser = useUserStore((state) => state.getUser);

  const [members, setMembers] = useState<GroupMemberItem[]>([]);
  const [loadingMembers, setLoadingMembers] = useState(false);
  const [editingName, setEditingName] = useState(false);
  const [newName, setNewName] = useState('');
  const [renameSaving, setRenameSaving] = useState(false);

  // 添加成员
  const [addOpen, setAddOpen] = useState(false);
  const [addKeyword, setAddKeyword] = useState('');
  const [addResults, setAddResults] = useState<UserPublicProfile[]>([]);
  const [addSearching, setAddSearching] = useState(false);
  const [addSaving, setAddSaving] = useState(false);

  const myRole = members.find((m) => idToString(m.userId) === myUserId)?.role ?? 1;
  const canManage = myRole === ROLE_OWNER || myRole === ROLE_ADMIN;

  const loadMembers = useCallback(async () => {
    if (!groupId) return;
    setLoadingMembers(true);
    try {
      const list = await getGroupMembers(groupId);
      setMembers(list);
      // 批量预加载昵称
      const ids = list.map((m) => idToString(m.userId));
      void ensureUsers(ids);
    } catch {
      message.error('加载成员列表失败');
    } finally {
      setLoadingMembers(false);
    }
  }, [groupId, ensureUsers, message]);

  useEffect(() => {
    if (open) {
      void loadMembers();
      setNewName(conv?.title ?? '');
    }
  }, [open, loadMembers, conv?.title]);

  async function handleRename() {
    const name = newName.trim();
    if (!name || name === conv?.title) {
      setEditingName(false);
      return;
    }
    setRenameSaving(true);
    try {
      const updated = await renameGroup(groupId, name);
      if (conv) {
        upsertConv({ ...conv, title: updated.name });
      }
      message.success('群名称已更新');
      setEditingName(false);
    } catch {
      message.error('改名失败，请重试');
    } finally {
      setRenameSaving(false);
    }
  }

  async function handleRemoveMember(userId: string) {
    try {
      await removeGroupMember(groupId, userId);
      setMembers((prev) => prev.filter((m) => idToString(m.userId) !== userId));
      if (conv) {
        upsertConv({ ...conv, title: conv.title });
      }
      message.success('已移除成员');
    } catch {
      message.error('移除成员失败，请重试');
    }
  }

  async function handleAddSearch(kw: string) {
    const keyword = kw.trim();
    if (!keyword) { setAddResults([]); return; }
    setAddSearching(true);
    try {
      const users = await searchUsers(keyword);
      setAddResults(users);
    } catch {
      message.error('搜索失败');
    } finally {
      setAddSearching(false);
    }
  }

  async function handleAddMember(user: UserPublicProfile) {
    setAddSaving(true);
    try {
      await addGroupMembers(groupId, { userIds: [user.id] });
      message.success(`已添加 ${user.nickname}`);
      setAddOpen(false);
      setAddKeyword('');
      setAddResults([]);
      void loadMembers();
    } catch {
      message.error('添加成员失败，请重试');
    } finally {
      setAddSaving(false);
    }
  }

  function roleTag(role: number) {
    if (role === ROLE_OWNER) return <Tag color="gold" icon={<CrownOutlined />}>群主</Tag>;
    if (role === ROLE_ADMIN) return <Tag color="blue">管理员</Tag>;
    return null;
  }

  return (
    <Drawer
      title="群组信息"
      placement="right"
      width={340}
      open={open}
      onClose={onClose}
      styles={{ body: { padding: '16px 20px' } }}
    >
      {/* 群名 */}
      <div style={{ marginBottom: 20 }}>
        <div style={{ fontSize: 12, color: '#6b778c', marginBottom: 4 }}>群名称</div>
        {editingName ? (
          <div style={{ display: 'flex', gap: 8 }}>
            <Input
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              onPressEnter={handleRename}
              maxLength={64}
              autoFocus
            />
            <Button type="primary" size="small" onClick={handleRename} loading={renameSaving}>
              保存
            </Button>
            <Button size="small" onClick={() => { setEditingName(false); setNewName(conv?.title ?? ''); }}>
              取消
            </Button>
          </div>
        ) : (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Typography.Text strong style={{ fontSize: 15 }}>{conv?.title}</Typography.Text>
            {canManage && (
              <Button type="link" size="small" onClick={() => setEditingName(true)} style={{ padding: 0 }}>
                改名
              </Button>
            )}
          </div>
        )}
      </div>

      {/* 成员列表 */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 8 }}>
        <span style={{ fontSize: 12, color: '#6b778c' }}>
          成员 · {members.length} 人
        </span>
        {canManage && (
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            onClick={() => setAddOpen(true)}
          >
            添加
          </Button>
        )}
      </div>

      {loadingMembers ? (
        <div style={{ textAlign: 'center', padding: 20 }}>
          <Spin />
        </div>
      ) : (
        <List
          dataSource={members}
          renderItem={(member) => {
            const uid = idToString(member.userId);
            const profile = getUser(uid);
            const nickname = profile?.nickname || uid;
            const avatar = profile?.avatar;
            const isSelf = uid === myUserId;
            const isOwner = member.role === ROLE_OWNER;
            return (
              <List.Item
                style={{ padding: '6px 0' }}
                actions={
                  canManage && !isSelf && !isOwner
                    ? [
                        <Popconfirm
                          key="remove"
                          title={`移除 ${nickname}？`}
                          onConfirm={() => handleRemoveMember(uid)}
                          okText="移除"
                          cancelText="取消"
                        >
                          <Button type="text" size="small" icon={<DeleteOutlined />} danger />
                        </Popconfirm>,
                      ]
                    : undefined
                }
              >
                <List.Item.Meta
                  avatar={
                    avatar ? (
                      <Avatar src={avatar} />
                    ) : (
                      <Avatar icon={<UserOutlined />}>{nickname.charAt(0)}</Avatar>
                    )
                  }
                  title={
                    <span style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      {nickname}
                      {isSelf && <Tag style={{ marginLeft: 4 }}>我</Tag>}
                      {roleTag(member.role)}
                    </span>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}

      {/* 添加成员子面板 */}
      <Drawer
        title="添加成员"
        width={300}
        open={addOpen}
        onClose={() => { setAddOpen(false); setAddKeyword(''); setAddResults([]); }}
        styles={{ body: { padding: 16 } }}
      >
        <Input.Search
          placeholder="搜索用户昵称或账号"
          value={addKeyword}
          onChange={(e) => setAddKeyword(e.target.value)}
          onSearch={handleAddSearch}
          loading={addSearching}
          enterButton
          style={{ marginBottom: 12 }}
        />
        {addSearching ? (
          <div style={{ textAlign: 'center', padding: 16 }}><Spin /></div>
        ) : (
          <List
            dataSource={addResults}
            locale={{ emptyText: addKeyword ? '未找到匹配用户' : '输入昵称或账号搜索' }}
            renderItem={(user) => {
              const alreadyMember = members.some((m) => idToString(m.userId) === idToString(user.id));
              return (
                <List.Item
                  actions={[
                    <Button
                      key="add"
                      type="primary"
                      size="small"
                      loading={addSaving}
                      disabled={alreadyMember}
                      onClick={() => handleAddMember(user)}
                    >
                      {alreadyMember ? '已加入' : '添加'}
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
                    title={user.nickname}
                  />
                </List.Item>
              );
            }}
          />
        )}
      </Drawer>
    </Drawer>
  );
}
