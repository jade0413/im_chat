import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App as AntApp, Button, Input, Spin, Tag } from 'antd';
import { CustomerServiceOutlined, SendOutlined } from '@ant-design/icons';
import { AppAvatar } from '../../components/AppAvatar';
import { createUuid } from '../../config';
import { enterVisitorWidget, getVisitorAgentAvailability, getVisitorWidgetConfig } from '../../api/visitor';
import type { AgentAvailabilityResponse, WidgetConfigResponse, WidgetSessionResponse } from '../../api/types';
import { VisitorSocket } from '../../socket/VisitorSocket';
import type { ChatMessage, ConnectionState } from '../../store/types';
import { formatMessageClock } from '../../utils/time';
import { idToString } from '../../utils/id';

const VISITOR_TOKEN_KEY = 'im_visitor_token';

export function VisitorPage() {
  const { message: antMessage } = AntApp.useApp();
  const [config, setConfig] = useState<WidgetConfigResponse | null>(null);
  const [availability, setAvailability] = useState<AgentAvailabilityResponse | null>(null);
  const [session, setSession] = useState<WidgetSessionResponse | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [connState, setConnState] = useState<ConnectionState>('idle');
  const [connMessage, setConnMessage] = useState('');
  const socketRef = useRef<VisitorSocket | null>(null);
  const listRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let disposed = false;
    const socket = new VisitorSocket({
      onStatus: (state, message) => {
        if (disposed) return;
        setConnState(state);
        setConnMessage(message ?? '');
      },
      onMessage: (item) => {
        if (disposed) return;
        setMessages((prev) => mergeMessage(prev, item));
      },
      onAck: (clientMsgId, patch) => {
        if (disposed) return;
        setMessages((prev) =>
          prev.map((item) => (item.clientMsgId === clientMsgId ? { ...item, ...patch } : item)),
        );
      },
      onError: (message) => {
        if (disposed) return;
        void antMessage.error(message);
      },
    });
    socketRef.current = socket;

    async function bootstrap() {
      setLoading(true);
      try {
        const visitorToken = getOrCreateVisitorToken();
        const [nextConfig, nextAvailability, nextSession] = await Promise.all([
          getVisitorWidgetConfig(),
          getVisitorAgentAvailability(),
          enterVisitorWidget(visitorToken),
        ]);
        if (disposed) return;
        setConfig(nextConfig);
        setAvailability(nextAvailability);
        setSession(nextSession);
        socket.connect({
          accessToken: nextSession.accessToken,
          conversationId: idToString(nextSession.conversationId),
          visitorId: idToString(nextSession.visitorId),
          displayName: nextSession.displayName,
          visitorToken,
        });
      } catch (error) {
        if (!disposed) {
          setConnState('error');
          setConnMessage(error instanceof Error ? error.message : String(error));
        }
      } finally {
        if (!disposed) {
          setLoading(false);
        }
      }
    }

    void bootstrap();
    return () => {
      disposed = true;
      socket.disconnect();
      socketRef.current = null;
    };
  }, [antMessage]);

  useEffect(() => {
    const el = listRef.current;
    if (el) {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    }
  }, [messages.length]);

  const visitorId = idToString(session?.visitorId);
  const headerTitle = config?.displayName || '在线客服';
  const canSend = connState === 'connected' && Boolean(session);
  const statusTag = useMemo(() => {
    if (connState === 'connected') return <Tag color="green">已连接</Tag>;
    if (connState === 'connecting') return <Tag color="processing">连接中</Tag>;
    if (connState === 'error') return <Tag color="red">连接失败</Tag>;
    return <Tag>未连接</Tag>;
  }, [connState]);

  function handleSend() {
    const text = input.trim();
    if (!text || !socketRef.current) return;
    const optimistic = socketRef.current.sendText(text);
    if (optimistic) {
      setMessages((prev) => mergeMessage(prev, optimistic));
      setInput('');
    }
  }

  return (
    <div className="visitor-page">
      <div className="visitor-shell">
        <div className="visitor-card">
          <div className="visitor-header" style={{ background: config?.color || '#1677ff' }}>
            <div className="visitor-header-main">
              <AppAvatar name={headerTitle} size={38} />
              <div>
                <div className="visitor-title">{headerTitle}</div>
                <div className="visitor-subtitle">
                  {availability?.available
                    ? `${availability.onlineAgentCount} 名坐席可接待`
                    : config?.offlineMsg || '当前暂无坐席在线'}
                </div>
              </div>
            </div>
            {statusTag}
          </div>

          {loading ? (
            <div className="visitor-loading">
              <Spin />
            </div>
          ) : connState === 'error' ? (
            <div className="visitor-error">
              <Alert
                type="error"
                showIcon
                message="游客客服入口不可用"
                description={connMessage || '请确认后端服务和网关已启动'}
              />
            </div>
          ) : (
            <>
              <div ref={listRef} className="visitor-message-list">
                {messages.length === 0 && (
                  <div className="visitor-welcome">
                    <CustomerServiceOutlined />
                    <div>{config?.welcomeMsg || '欢迎！有什么可以帮您？'}</div>
                  </div>
                )}
                {messages.map((item) => (
                  <VisitorMessageBubble key={item.clientMsgId} message={item} isSelf={item.sender.userId === visitorId} />
                ))}
              </div>
              <div className="visitor-input">
                <Input.TextArea
                  value={input}
                  autoSize={{ minRows: 1, maxRows: 4 }}
                  placeholder={canSend ? '输入消息...' : '正在连接客服...'}
                  disabled={!canSend}
                  onChange={(event) => setInput(event.target.value)}
                  onPressEnter={(event) => {
                    if (!event.shiftKey) {
                      event.preventDefault();
                      handleSend();
                    }
                  }}
                />
                <Button type="primary" icon={<SendOutlined />} disabled={!input.trim() || !canSend} onClick={handleSend}>
                  发送
                </Button>
              </div>
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function VisitorMessageBubble({ message, isSelf }: { message: ChatMessage; isSelf: boolean }) {
  const text = message.content.kind === 'text' ? message.content.text : '[暂不支持的消息]';
  return (
    <div className={`visitor-message-row ${isSelf ? 'self' : ''}`}>
      <AppAvatar name={isSelf ? '我' : message.sender.nickname} src={message.sender.avatar} size={30} />
      <div className="visitor-message-stack">
        <div className="visitor-message-meta">{isSelf ? '我' : message.sender.nickname}</div>
        <div className="visitor-message-bubble">
          <span>{text}</span>
          <span className="visitor-message-time">
            {formatMessageClock(message.sendTime)}
            {isSelf && <span>{message.status === 'sending' ? ' · 发送中' : message.status === 'failed' ? ' · 失败' : ' · 已发送'}</span>}
          </span>
        </div>
      </div>
    </div>
  );
}

function getOrCreateVisitorToken(): string {
  const existing = window.localStorage.getItem(VISITOR_TOKEN_KEY);
  if (existing) {
    return existing;
  }
  const created = createUuid();
  window.localStorage.setItem(VISITOR_TOKEN_KEY, created);
  return created;
}

function mergeMessage(list: ChatMessage[], item: ChatMessage): ChatMessage[] {
  const index = list.findIndex((existing) => existing.clientMsgId === item.clientMsgId);
  if (index >= 0) {
    const next = list.slice();
    next[index] = { ...next[index], ...item };
    return sortMessages(next);
  }
  return sortMessages([...list, item]);
}

function sortMessages(list: ChatMessage[]): ChatMessage[] {
  return list.slice().sort((a, b) => Number(a.sendTime || 0) - Number(b.sendTime || 0));
}
