import { Alert, App as AntApp, Button, Drawer, Empty, Input, Segmented, Spin, Tag, Tooltip } from 'antd';
import { BugOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { ApiError } from '../../../api/client';
import { getAgentAvailability, listCsConversations } from '../../../api/cs';
import type { AgentAvailabilityResponse, CsConvItemResponse } from '../../../api/types';
import { updateAgentStatus } from '../../../api/user';
import { imSocket } from '../../../socket/ImSocket';
import { useAuthStore } from '../../../store/authStore';
import { useConvStore } from '../../../store/convStore';
import { useSocketStore } from '../../../store/socketStore';
import { useUiStore } from '../../../store/uiStore';
import type { Conversation } from '../../../store/types';
import { idToString } from '../../../utils/id';
import { NewChatModal } from '../../../components/NewChatModal';
import { CreateGroupModal } from '../../../components/CreateGroupModal';
import { ContactsPanel } from '../contacts/ContactsPanel';
import { ConvItem } from './ConvItem';

// conv.type: 1=C2C, 2=GROUP, 3=CS, 4=SYSTEM
const CONV_TYPE_C2C = 1;
const CONV_TYPE_GROUP = 2;
const CONV_TYPE_CS = 3;
const CONV_TYPE_SYSTEM = 4;
const CS_PAGE_SIZE = 30;

export function ConvListPanel() {
  const { message } = AntApp.useApp();
  const conversationMap = useConvStore((state) => state.conversations);
  const upsertConv = useConvStore((state) => state.upsertConv);
  const sessionUser = useAuthStore((state) => state.user);
  const setSessionUser = useAuthStore((state) => state.setUser);
  const activeTab = useUiStore((state) => state.activeTab);
  const csRefreshVersion = useUiStore((state) => state.csRefreshVersion);
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
  const [csItems, setCsItems] = useState<CsConvItemResponse[]>([]);
  const [csHasMore, setCsHasMore] = useState(false);
  const [csLoading, setCsLoading] = useState(false);
  const [csError, setCsError] = useState<string | null>(null);
  const [agentStatus, setAgentStatus] = useState<number>(0);
  const [agentStatusLoading, setAgentStatusLoading] = useState(false);
  const [agentAvailability, setAgentAvailability] = useState<AgentAvailabilityResponse | null>(null);

  useEffect(() => {
    if (activeTab === 'cs') {
      setAgentStatus(Number(sessionUser?.agentStatus ?? 0));
    }
  }, [activeTab, sessionUser?.agentStatus]);

  const refreshAvailability = useCallback(async () => {
    try {
      setAgentAvailability(await getAgentAvailability());
    } catch {
      setAgentAvailability(null);
    }
  }, []);

  const loadCsConversations = useCallback(
    async (append = false) => {
      setCsLoading(true);
      setCsError(null);
      try {
        const response = await listCsConversations({
          limit: CS_PAGE_SIZE,
          offset: append ? csItems.length : 0,
        });
        const nextItems = append ? [...csItems, ...response.convs] : response.convs;
        setCsItems(nextItems);
        setCsHasMore(response.hasMore);
        for (const item of response.convs) {
          const convId = idToString(item.convId);
          upsertConv(csItemToConversation(item, useConvStore.getState().conversations.get(convId)));
        }
      } catch (error) {
        setCsError(readableError(error));
      } finally {
        setCsLoading(false);
      }
    },
    [csItems, upsertConv],
  );

  useEffect(() => {
    if (activeTab !== 'cs') return;
    void loadCsConversations(false);
    void refreshAvailability();
  }, [activeTab, csRefreshVersion]); // eslint-disable-line react-hooks/exhaustive-deps

  const conversations = useMemo(() => {
    const kw = keyword.trim().toLowerCase();
    if (activeTab === 'cs') {
      return csItems
        .map((item) => {
          const convId = idToString(item.convId);
          return conversationMap.get(convId) ?? csItemToConversation(item);
        })
        .filter((conv) => {
          if (!kw) return true;
          return (
            conv.title.toLowerCase().includes(kw) ||
            conv.lastMsgAbstract.toLowerCase().includes(kw)
          );
        });
    }
    return Array.from(conversationMap.values())
      .filter((conv) => {
        // tab 过滤（SYSTEM 通知会话不进消息列表，由联系人「新的朋友」入口承载）
        if (activeTab === 'chats' && (conv.type === CONV_TYPE_CS || conv.type === CONV_TYPE_SYSTEM)) return false;
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
  }, [conversationMap, csItems, keyword, activeTab]);

  const panelTitle = activeTab === 'contacts' ? '联系人' : activeTab === 'groups' ? '群组' : activeTab === 'cs' ? '客服' : '消息';
  const searchPlaceholder = activeTab === 'contacts' ? '搜索联系人' : activeTab === 'groups' ? '搜索群组' : activeTab === 'cs' ? '搜索访客或消息' : '搜索会话';
  const emptyText = keyword ? '没有匹配的结果' : activeTab === 'contacts' ? '暂无联系人' : activeTab === 'groups' ? '暂无群组' : activeTab === 'cs' ? '暂无客服会话' : '暂无会话';

  async function handleAgentStatusChange(value: string | number) {
    const nextStatus = Number(value);
    setAgentStatusLoading(true);
    try {
      await updateAgentStatus(nextStatus);
      setAgentStatus(nextStatus);
      if (sessionUser) {
        setSessionUser({ ...sessionUser, agentStatus: nextStatus });
      }
      message.success(`坐席状态已切换为${agentStatusLabel(nextStatus)}`);
      await refreshAvailability();
    } catch (error) {
      message.error(readableError(error));
    } finally {
      setAgentStatusLoading(false);
    }
  }

  // 联系人 tab 走独立的通讯录面板（好友列表 / 新的朋友 / 添加好友，D40）
  if (activeTab === 'contacts') {
    return <ContactsPanel />;
  }

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
            {activeTab !== 'groups' && activeTab !== 'cs' && (
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
            {activeTab === 'cs' && (
              <Button
                type="text"
                size="small"
                icon={<ReloadOutlined />}
                onClick={() => loadCsConversations(false)}
                loading={csLoading}
                title="刷新客服会话"
                aria-label="刷新客服会话"
              />
            )}
          </div>
        </div>
        {connectionDetail && (
          <div className="socket-detail" title={connectionDetail}>
            {connectionDetail}
          </div>
        )}
        {activeTab === 'cs' && (
          <div className="cs-agent-toolbar">
            <Segmented
              size="small"
              value={agentStatus}
              disabled={agentStatusLoading}
              onChange={handleAgentStatusChange}
              options={[
                { label: '离线', value: 0 },
                { label: '在线', value: 1 },
                { label: '忙碌', value: 2 },
              ]}
            />
            <Tooltip title={agentAvailability ? `当前在线坐席 ${agentAvailability.onlineAgentCount} 人` : '坐席在线状态未知'}>
              <Tag color={agentAvailability?.available ? 'success' : 'default'}>
                {agentAvailability?.available ? '可接待' : '离线留言'}
              </Tag>
            </Tooltip>
            <Tag color={agentStatusHintColor(agentStatus)}>{agentStatusHint(agentStatus)}</Tag>
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
        {activeTab === 'cs' && csError && (
          <Alert
            showIcon
            type="warning"
            message="客服工作台不可用"
            description={csError}
            style={{ margin: 8 }}
          />
        )}
        {activeTab === 'cs' && csLoading && conversations.length === 0 ? (
          <div className="panel-loading"><Spin /></div>
        ) : conversations.length > 0 ? (
          conversations.map((conv) => <ConvItem key={conv.convId} conv={conv} />)
        ) : (
          <div style={{ padding: 16, color: '#6b778c' }}>
            <div style={{ marginBottom: 12 }}>{emptyText}</div>
            {!keyword && activeTab === 'chats' && (
              <Button size="small" icon={<ReloadOutlined />} onClick={() => imSocket.sendSyncReq()}>
                同步
              </Button>
            )}
            {!keyword && activeTab === 'cs' && (
              <Button size="small" icon={<ReloadOutlined />} onClick={() => loadCsConversations(false)} loading={csLoading}>
                刷新
              </Button>
            )}
          </div>
        )}
        {activeTab === 'cs' && conversations.length > 0 && csHasMore && (
          <div className="load-more-row">
            <Button size="small" onClick={() => loadCsConversations(true)} loading={csLoading}>
              加载更多
            </Button>
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

function csItemToConversation(item: CsConvItemResponse, existing?: Conversation): Conversation {
  return {
    convId: idToString(item.convId),
    type: CONV_TYPE_CS,
    title: item.visitorName || `访客 ${idToString(item.visitorUserId)}`,
    avatar: existing?.avatar,
    peerUserId: idToString(item.visitorUserId),
    groupId: undefined,
    maxSeq: idToString(item.maxSeq),
    readSeq: existing?.readSeq ?? '0',
    pinned: existing?.pinned ?? false,
    muted: existing?.muted ?? false,
    lastMsgAbstract: item.lastMsgAbstract || '等待访客消息',
    lastMsgTime: idToString(item.lastMsgTimeMs),
    csStatus: idToString(item.csStatus),
    visitorOnline: item.visitorOnline,
    visitorReadSeq: idToString(item.visitorReadSeq ?? 0),
    peerReadSeq: idToString(item.visitorReadSeq ?? existing?.peerReadSeq ?? 0),
  };
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '请求失败，请稍后重试';
}

function agentStatusLabel(status: number): string {
  switch (status) {
    case 1: return '在线';
    case 2: return '忙碌';
    default: return '离线';
  }
}

function agentStatusHint(status: number): string {
  switch (status) {
    case 1: return '可认领新访客';
    case 2: return '忙碌中，仅处理已认领';
    default: return '离线，不接新访客';
  }
}

function agentStatusHintColor(status: number): string {
  switch (status) {
    case 1: return 'green';
    case 2: return 'gold';
    default: return 'default';
  }
}

function statusLabel(status: string) {
  switch (status) {
    case 'connected': return '在线';
    case 'connecting': return '连接中';
    case 'reconnecting': return '重连中';
    case 'closed': return '已断开';
    case 'idle': return '未连接';
    case 'error': return '异常';
    default: return '未知';
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
