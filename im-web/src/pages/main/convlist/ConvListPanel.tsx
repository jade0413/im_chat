import { Button, Drawer, Empty, Input, Tag, Tooltip } from 'antd';
import { BugOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMemo, useState } from 'react';
import { imSocket } from '../../../socket/ImSocket';
import { useConvStore } from '../../../store/convStore';
import { useSocketStore } from '../../../store/socketStore';
import { useUiStore } from '../../../store/uiStore';
import { NewChatModal } from '../../../components/NewChatModal';
import { CreateGroupModal } from '../../../components/CreateGroupModal';
import { ConvItem } from './ConvItem';

// conv.type: 1=C2C, 2=GROUP, 3=CS
const CONV_TYPE_C2C = 1;
const CONV_TYPE_GROUP = 2;

export function ConvListPanel() {
  const conversationMap = useConvStore((state) => state.conversations);
  const activeTab = useUiStore((state) => state.activeTab);
  const status = useSocketStore((state) => state.status);
  const lastError = useSocketStore((state) => state.lastError);
  const lastEvent = useSocketStore((state) => state.lastEvent);
  const logs = useSocketStore((state) => state.logs);
  const clearLogs = useSocketStore((state) => state.clearLogs);
  const connectionDetail = lastError || lastEvent;
  const [keyword, setKeyword] = useState('');
  const [newChatOpen, setNewChatOpen] = useState(false);
  const [newGroupOpen, setNewGroupOpen] = useState(false);
  const [debugOpen, setDebugOpen] = useState(false);

  const conversations = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    return Array.from(conversationMap.values())
      .filter((conv) => {
        // tab 过滤
        if (activeTab === 'contacts' && conv.type !== CONV_TYPE_C2C) return false;
        if (activeTab === 'groups' && conv.type !== CONV_TYPE_GROUP) return false;
        // 关键字过滤
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
  }, [conversationMap, keyword, activeTab]);

  const panelTitle = activeTab === 'contacts' ? '联系人' : activeTab === 'groups' ? '群组' : '消息';
  const searchPlaceholder = activeTab === 'contacts' ? '搜索联系人' : activeTab === 'groups' ? '搜索群组' : '搜索会话';
  const emptyText = keyword ? '没有匹配的结果' : activeTab === 'contacts' ? '暂无联系人' : activeTab === 'groups' ? '暂无群组' : '暂无会话';

  return (
    <section className="conv-panel">
      <header className="conv-panel-header">
        <div className="conv-title-row">
          <h2 className="section-title">{panelTitle}</h2>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Tooltip title={lastError || lastEvent || '暂无连接事件'}>
              <Tag color={status === 'connected' ? 'success' : status === 'reconnecting' ? 'processing' : status === 'error' ? 'error' : 'default'}>
                {statusLabel(status)}
              </Tag>
            </Tooltip>
            <Button
              type="text"
              size="small"
              icon={<BugOutlined />}
              onClick={() => setDebugOpen(true)}
              title="连接日志"
              aria-label="连接日志"
            />
            {activeTab !== 'groups' && (
              <Button
                type="text"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => setNewChatOpen(true)}
                title="发起单聊"
                aria-label="发起单聊"
              />
            )}
            {(activeTab === 'groups' || activeTab === 'chats') && (
              <Button
                type="text"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => setNewGroupOpen(true)}
                title="创建群组"
                aria-label="创建群组"
              />
            )}
          </div>
        </div>
        {connectionDetail && (
          <div className="socket-detail" title={connectionDetail}>
            {connectionDetail}
          </div>
        )}
        <Input
          prefix={<SearchOutlined />}
          placeholder={searchPlaceholder}
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
            <div style={{ marginBottom: 12 }}>{emptyText}</div>
            {!keyword && activeTab === 'chats' && (
              <Button size="small" icon={<ReloadOutlined />} onClick={() => imSocket.sendSyncReq()}>
                同步
              </Button>
            )}
          </div>
        )}
      </div>
      <NewChatModal open={newChatOpen} onClose={() => setNewChatOpen(false)} />
      <CreateGroupModal open={newGroupOpen} onClose={() => setNewGroupOpen(false)} />
      <Drawer
        title="连接调试日志"
        placement="right"
        width={420}
        open={debugOpen}
        onClose={() => setDebugOpen(false)}
        extra={
          <Button size="small" icon={<DeleteOutlined />} onClick={clearLogs}>
            清空
          </Button>
        }
      >
        {logs.length ? (
          <div className="debug-log-list">
            {logs.map((log) => (
              <div className={`debug-log-item ${log.level}`} key={log.id}>
                <div className="debug-log-meta">
                  <span>{formatLogTime(log.time)}</span>
                  <Tag color={log.level === 'error' ? 'error' : log.level === 'warn' ? 'warning' : 'processing'}>
                    {log.level}
                  </Tag>
                </div>
                <div className="debug-log-message">{log.message}</div>
                {log.detail && <pre className="debug-log-detail">{log.detail}</pre>}
              </div>
            ))}
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无日志" />
        )}
      </Drawer>
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

function formatLogTime(time: number) {
  const date = new Date(time);
  return date.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}
