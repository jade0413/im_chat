import { Button, Input, Tag } from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMemo } from 'react';
import { imSocket } from '../../../socket/ImSocket';
import { useConvStore } from '../../../store/convStore';
import { useSocketStore } from '../../../store/socketStore';
import { ConvItem } from './ConvItem';

export function ConvListPanel() {
  const conversationMap = useConvStore((state) => state.conversations);
  const conversations = useMemo(
    () =>
      Array.from(conversationMap.values()).sort(
        (a, b) =>
          Number(b.pinned) - Number(a.pinned) ||
          Number(b.lastMsgTime ?? 0) - Number(a.lastMsgTime ?? 0),
      ),
    [conversationMap],
  );
  const status = useSocketStore((state) => state.status);

  return (
    <section className="conv-panel">
      <header className="conv-panel-header">
        <div className="conv-title-row">
          <h2 className="section-title">消息</h2>
          <Tag color={status === 'connected' ? 'success' : status === 'reconnecting' ? 'processing' : 'default'}>
            {statusLabel(status)}
          </Tag>
        </div>
        <Input prefix={<SearchOutlined />} placeholder="搜索会话" allowClear />
      </header>
      <div className="conv-list">
        {conversations.length > 0 ? (
          conversations.map((conv) => <ConvItem key={conv.convId} conv={conv} />)
        ) : (
          <div style={{ padding: 16, color: '#6b778c' }}>
            <div style={{ marginBottom: 12 }}>暂无会话</div>
            <Button size="small" icon={<ReloadOutlined />} onClick={() => imSocket.sendSyncReq()}>
              同步
            </Button>
          </div>
        )}
      </div>
    </section>
  );
}

function statusLabel(status: string) {
  switch (status) {
    case 'connected':
      return '在线';
    case 'connecting':
      return '连接中';
    case 'reconnecting':
      return '重连中';
    case 'error':
      return '异常';
    default:
      return '离线';
  }
}
