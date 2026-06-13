import { Button, Input, Tag } from 'antd';
import { PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { imSocket } from '../../../socket/ImSocket';
import { useConvStore } from '../../../store/convStore';
import { useSocketStore } from '../../../store/socketStore';
import { NewChatModal } from '../../../components/NewChatModal';
import { ConvItem } from './ConvItem';

export function ConvListPanel() {
  const conversationMap = useConvStore((state) => state.conversations);
  const status = useSocketStore((state) => state.status);
  const [keyword, setKeyword] = useState('');
  const [newChatOpen, setNewChatOpen] = useState(false);

  const conversations = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return Array.from(conversationMap.values())
      .filter((conv) => {
        if (!kw) return true;
        return (
          conv.title.toLowerCase().includes(kw) ||
          conv.lastMsgAbstract.toLowerCase().includes(kw)
        );
      })
      .sort(
        (a, b) =>
          Number(b.pinned) - Number(a.pinned) ||
          Number(b.lastMsgTime ?? 0) - Number(a.lastMsgTime ?? 0),
      );
  }, [conversationMap, keyword]);

  return (
    <section className="conv-panel">
      <header className="conv-panel-header">
        <div className="conv-title-row">
          <h2 className="section-title">消息</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Tag color={status === 'connected' ? 'success' : status === 'reconnecting' ? 'processing' : 'default'}>
              {statusLabel(status)}
            </Tag>
            <Button
              type="text"
              size="small"
              icon={<PlusOutlined />}
              onClick={() => setNewChatOpen(true)}
              title="发起单聊"
              aria-label="发起单聊"
            />
          </div>
        </div>
        <Input
          prefix={<SearchOutlined />}
          placeholder="搜索会话"
          allowClear
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
        />
      </header>
      <div className="conv-list">
        {conversations.length > 0 ? (
          conversations.map((conv) => <ConvItem key={conv.convId} conv={conv} />)
        ) : (
          <div style={{ padding: 16, color: '#6b778c' }}>
            <div style={{ marginBottom: 12 }}>
              {keyword ? '没有匹配的会话' : '暂无会话'}
            </div>
            {!keyword && (
              <Button size="small" icon={<ReloadOutlined />} onClick={() => imSocket.sendSyncReq()}>
                同步
              </Button>
            )}
          </div>
        )}
      </div>
      <NewChatModal open={newChatOpen} onClose={() => setNewChatOpen(false)} />
    </section>
  );
}

function statusLabel(status: string) {
  switch (status) {
    case 'connected': return '在线';
    case 'connecting': return '连接中';
    case 'reconnecting': return '重连中';
    case 'error': return '异常';
    default: return '离线';
  }
}
