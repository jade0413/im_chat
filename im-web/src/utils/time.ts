import dayjs from 'dayjs';

export function formatChatTime(value?: string | number): string {
  if (!value) {
    return '';
  }
  const timestamp = Number(value);
  const date = timestamp > 10_000_000_000 ? dayjs(timestamp) : dayjs(timestamp * 1000);
  const now = dayjs();
  if (date.isSame(now, 'day')) {
    return date.format('HH:mm');
  }
  if (date.isSame(now.subtract(1, 'day'), 'day')) {
    return '昨天';
  }
  if (date.isSame(now, 'year')) {
    return date.format('M月D日');
  }
  return date.format('YYYY/M/D');
}

export function formatMessageClock(value?: string | number): string {
  if (!value) {
    return '';
  }
  const timestamp = Number(value);
  return dayjs(timestamp > 10_000_000_000 ? timestamp : timestamp * 1000).format('HH:mm');
}
