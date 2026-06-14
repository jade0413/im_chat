/** 相邻消息时间间隔超过此值时插入时间分割线（5 分钟） */
export const TIME_GAP_MS = 5 * 60 * 1000;

export function TimeDivider({ timestamp }: { timestamp: string | number }) {
  const ts = Number(timestamp);
  const date = new Date(ts);
  const now = new Date();
  const isToday =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();

  const timeStr = date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
  const dateStr = isToday
    ? timeStr
    : `${date.getMonth() + 1}月${date.getDate()}日 ${timeStr}`;

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: 8,
        padding: '8px 16px',
        color: 'var(--color-text-subtle, #9aa4b2)',
        fontSize: 12,
      }}
    >
      <div style={{ flex: 1, height: 1, background: 'currentColor', opacity: 0.2 }} />
      <span>{dateStr}</span>
      <div style={{ flex: 1, height: 1, background: 'currentColor', opacity: 0.2 }} />
    </div>
  );
}
