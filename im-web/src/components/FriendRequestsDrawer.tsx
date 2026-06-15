import { useEffect, useState } from 'react';
import { App as AntApp, Avatar, Button, Drawer, Empty, List, Segmented, Space, Spin, Tag } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import {
  acceptFriendRequest,
  ignoreFriendRequest,
  listFriendRequests,
  rejectFriendRequest,
} from '../api/friend';
import type { FriendRequestItem } from '../api/types';
import { useFriendStore } from '../store/friendStore';

interface FriendRequestsDrawerProps {
  open: boolean;
  onClose: () => void;
}

const STATUS_META: Record<number, { label: string; color: string }> = {
  0: { label: '待处理', color: 'processing' },
  1: { label: '已同意', color: 'success' },
  2: { label: '已拒绝', color: 'default' },
  3: { label: '已忽略', color: 'default' },
};

export function FriendRequestsDrawer({ open, onClose }: FriendRequestsDrawerProps) {
  const { message } = AntApp.useApp();
  const incoming = useFriendStore((state) => state.incoming);
  const loadingRequests = useFriendStore((state) => state.loadingRequests);
  const loadRequests = useFriendStore((state) => state.loadRequests);
  const loadFriends = useFriendStore((state) => state.loadFriends);
  const [role, setRole] = useState<'incoming' | 'outgoing'>('incoming');
  const [outgoing, setOutgoing] = useState<FriendRequestItem[]>([]);
  const [outgoingLoading, setOutgoingLoading] = useState(false);
  const [actingId, setActingId] = useState<string | null>(null);

  useEffect(() => {
    if (!open) return;
    void loadRequests();
  }, [open, loadRequests]);

  useEffect(() => {
    if (!open || role !== 'outgoing') return;
    setOutgoingLoading(true);
    listFriendRequests('outgoing', 50)
      .then(setOutgoing)
      .catch(() => message.error('加载失败'))
      .finally(() => setOutgoingLoading(false));
  }, [open, role, message]);

  async function act(
    id: FriendRequestItem['requestId'],
    fn: (id: FriendRequestItem['requestId']) => Promise<void>,
    successText: string,
  ) {
    setActingId(String(id));
    try {
      await fn(id);
      message.success(successText);
      await loadRequests();
      await loadFriends();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '操作失败');
    } finally {
      setActingId(null);
    }
  }

  const items = role === 'incoming' ? incoming : outgoing;
  const loading = role === 'incoming' ? loadingRequests : outgoingLoading;

  return (
    <Drawer title="新的朋友" placement="right" width={400} open={open} onClose={onClose}>
      <Segmented
        block
        value={role}
        onChange={(v) => setRole(v as 'incoming' | 'outgoing')}
        options={[
          { label: '收到的申请', value: 'incoming' },
          { label: '我发出的', value: 'outgoing' },
        ]}
        style={{ marginBottom: 12 }}
      />

      {loading && items.length === 0 ? (
        <div style={{ textAlign: 'center', padding: 32 }}>
          <Spin />
        </div>
      ) : items.length === 0 ? (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无申请" />
      ) : (
        <List
          dataSource={items}
          renderItem={(req) => {
            const meta = STATUS_META[req.status] ?? STATUS_META[0];
            const busy = actingId === String(req.requestId);
            const canAct = role === 'incoming' && req.status === 0;
            return (
              <List.Item>
                <List.Item.Meta
                  avatar={
                    req.peerAvatar ? (
                      <Avatar src={req.peerAvatar} />
                    ) : (
                      <Avatar icon={<UserOutlined />}>{req.peerNickname.charAt(0)}</Avatar>
                    )
                  }
                  title={
                    <span>
                      {req.peerNickname}
                      {req.peerUsername && (
                        <span style={{ color: '#8c8c8c', fontWeight: 400, marginLeft: 6 }}>
                          @{req.peerUsername}
                        </span>
                      )}
                    </span>
                  }
                  description={
                    <div>
                      <div style={{ color: '#595959' }}>{req.note || '请求添加你为好友'}</div>
                      <div style={{ marginTop: 6 }}>
                        {canAct ? (
                          <Space>
                            <Button
                              type="primary"
                              size="small"
                              loading={busy}
                              onClick={() => act(req.requestId, acceptFriendRequest, '已添加为好友')}
                            >
                              同意
                            </Button>
                            <Button
                              size="small"
                              loading={busy}
                              onClick={() => act(req.requestId, rejectFriendRequest, '已拒绝')}
                            >
                              拒绝
                            </Button>
                            <Button
                              size="small"
                              type="text"
                              loading={busy}
                              onClick={() => act(req.requestId, ignoreFriendRequest, '已忽略')}
                            >
                              忽略
                            </Button>
                          </Space>
                        ) : (
                          <Tag color={meta.color}>
                            {meta.label}
                            {req.autoAccepted && req.status === 1 ? '（免验证）' : ''}
                          </Tag>
                        )}
                      </div>
                    </div>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}
    </Drawer>
  );
}
