import { MessageOutlined } from '@ant-design/icons';

export function EmptyState() {
  return (
    <div className="empty-state">
      <div className="empty-state-inner">
        <MessageOutlined style={{ fontSize: 34, color: '#9aa4b2', marginBottom: 12 }} />
        <div className="empty-state-title">选择一个会话</div>
        <div>消息同步完成后，会话会出现在左侧列表。</div>
      </div>
    </div>
  );
}
