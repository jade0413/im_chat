import { Button, Popover } from 'antd';
import { SmileOutlined } from '@ant-design/icons';

const emojis = ['😀', '😂', '👍', '🙏', '🎉', '✅', '🔥', '💡', '📌', '❤️'];

export function EmojiPicker({ onSelect }: { onSelect: (emoji: string) => void }) {
  return (
    <Popover
      trigger="click"
      placement="topLeft"
      content={
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 32px)', gap: 4 }}>
          {emojis.map((emoji) => (
            <button
              key={emoji}
              type="button"
              aria-label={emoji}
              style={{ border: 0, background: 'transparent', cursor: 'pointer', fontSize: 18, height: 32 }}
              onClick={() => onSelect(emoji)}
            >
              {emoji}
            </button>
          ))}
        </div>
      }
    >
      <Button type="text" icon={<SmileOutlined />} aria-label="表情" />
    </Popover>
  );
}
