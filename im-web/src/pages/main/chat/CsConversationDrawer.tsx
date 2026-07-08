import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, App as AntApp, Button, Divider, Drawer, Empty, Input, List, Space, Spin, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { ApiError } from '../../../api/client';
import { createCsInternalNote, listCsInternalNotes } from '../../../api/cs';
import type { CsInternalNoteResponse } from '../../../api/types';
import { useAuthStore } from '../../../store/authStore';
import { useConvStore } from '../../../store/convStore';
import { idToString } from '../../../utils/id';
import { formatMessageClock } from '../../../utils/time';

interface CsConversationDrawerProps {
  convId: string;
  open: boolean;
  onClose: () => void;
}

export function CsConversationDrawer({ convId, open, onClose }: CsConversationDrawerProps) {
  const { message } = AntApp.useApp();
  const conv = useConvStore((state) => state.conversations.get(convId));
  const currentUserId = idToString(useAuthStore((state) => state.user?.id ?? ''));
  const [notes, setNotes] = useState<CsInternalNoteResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [content, setContent] = useState('');

  const canUseNotes = conv?.type === 3 && conv.csStatus !== '1';
  const noteCountText = useMemo(() => `${notes.length}/100`, [notes.length]);

  const loadNotes = useCallback(async () => {
    if (!canUseNotes) return;
    setLoading(true);
    setError(null);
    try {
      const response = await listCsInternalNotes(convId);
      setNotes(response.notes);
    } catch (err) {
      setError(readableError(err));
    } finally {
      setLoading(false);
    }
  }, [canUseNotes, convId]);

  useEffect(() => {
    if (!open) return;
    if (!canUseNotes) {
      setNotes([]);
      setError(null);
      return;
    }
    void loadNotes();
  }, [open, canUseNotes, loadNotes]);

  async function handleCreateNote() {
    const value = content.trim();
    if (!value) return;
    setSubmitting(true);
    try {
      const note = await createCsInternalNote(convId, { content: value });
      setNotes((current) => [...current, note]);
      setContent('');
      message.success('内部备注已保存');
    } catch (err) {
      message.error(readableError(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Drawer title="会话资料" placement="right" width={380} open={open} onClose={onClose}>
      {conv ? (
        <>
          <section className="cs-drawer-section">
            <Typography.Title level={5} className="cs-drawer-title">
              {conv.title}
            </Typography.Title>
            <Space size={[6, 6]} wrap>
              <Tag color={csStatusColor(conv.csStatus)}>{csStatusLabel(conv.csStatus)}</Tag>
              {conv.visitorOnline && <Tag color="success">访客在线</Tag>}
              {conv.peerUserId && <Tag>访客ID {conv.peerUserId}</Tag>}
            </Space>
            <div className="cs-drawer-meta">
              <div>
                <span>最近消息</span>
                <strong>{conv.lastMsgAbstract || '暂无消息'}</strong>
              </div>
              <div>
                <span>最近时间</span>
                <strong>{formatMessageClock(conv.lastMsgTime)}</strong>
              </div>
              <div>
                <span>访客已读</span>
                <strong>seq {conv.visitorReadSeq ?? conv.peerReadSeq ?? '0'}</strong>
              </div>
            </div>
          </section>

          <Divider />

          <section className="cs-drawer-section">
            <div className="cs-drawer-heading">
              <Typography.Title level={5} className="cs-drawer-title">
                内部备注
              </Typography.Title>
              <Typography.Text type="secondary">{noteCountText}</Typography.Text>
            </div>
            <Typography.Paragraph type="secondary" className="cs-drawer-help">
              备注只在坐席端可见，不会发送给访客。
            </Typography.Paragraph>

            {!canUseNotes ? (
              <Alert
                showIcon
                type="info"
                message="认领后可查看内部备注"
                description="未认领会话只展示队列摘要，不能查看完整记录或坐席协作内容。"
              />
            ) : (
              <>
                <div className="cs-note-composer">
                  <Input.TextArea
                    value={content}
                    onChange={(event) => setContent(event.target.value)}
                    autoSize={{ minRows: 3, maxRows: 6 }}
                    maxLength={2000}
                    showCount
                    placeholder="记录访客诉求、处理进展或交接信息"
                  />
                  <Button
                    type="primary"
                    icon={<PlusOutlined />}
                    loading={submitting}
                    disabled={!content.trim()}
                    onClick={handleCreateNote}
                  >
                    添加备注
                  </Button>
                </div>

                {error && (
                  <Alert className="cs-note-alert" showIcon type="warning" message="备注暂时不可用" description={error} />
                )}
                {loading ? (
                  <div className="cs-note-loading"><Spin /></div>
                ) : notes.length > 0 ? (
                  <List
                    className="cs-note-list"
                    dataSource={notes}
                    renderItem={(note) => (
                      <List.Item key={idToString(note.id)} className="cs-note-item">
                        <div className="cs-note-item-main">
                          <div className="cs-note-meta">
                            <span>{idToString(note.agentId) === currentUserId ? '我' : `坐席 ${idToString(note.agentId)}`}</span>
                            <span>{formatNoteTime(note.createdAtMs)}</span>
                          </div>
                          <div className="cs-note-content">{note.content}</div>
                        </div>
                      </List.Item>
                    )}
                  />
                ) : (
                  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无内部备注" />
                )}
              </>
            )}
          </section>
        </>
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="会话不存在" />
      )}
    </Drawer>
  );
}

function csStatusLabel(status?: string) {
  switch (status) {
    case '1': return '待接待';
    case '2': return '接待中';
    case '3': return '已结单';
    default: return '状态未知';
  }
}

function csStatusColor(status?: string) {
  switch (status) {
    case '1': return 'gold';
    case '2': return 'processing';
    case '3': return 'default';
    default: return 'default';
  }
}

function formatNoteTime(value: string | number) {
  const timestamp = Number(value);
  if (!timestamp) return '';
  return dayjs(timestamp > 10_000_000_000 ? timestamp : timestamp * 1000).format('YYYY/M/D HH:mm');
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
