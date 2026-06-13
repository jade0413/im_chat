import { Button } from 'antd';
import { CaretRightOutlined } from '@ant-design/icons';

export function AudioPlayer({ durationMs }: { durationMs: number }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 180 }}>
      <Button shape="circle" size="small" icon={<CaretRightOutlined />} />
      <div style={{ flex: 1, height: 3, borderRadius: 999, background: 'currentColor', opacity: 0.36 }} />
      <span>{Math.max(1, Math.round(durationMs / 1000))}s</span>
    </div>
  );
}
