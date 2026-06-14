interface TextBubbleProps {
  text: string;
  /** Telegram time tail：内联在文字末尾的时间+状态图标 */
  timeTail?: React.ReactNode;
}

/**
 * 文字气泡
 *
 * 实现 Telegram "time tail" 效果：
 * - 短消息：时间紧跟在文字同行末尾，如 "Hi  12:34 ✓"
 * - 长消息：时间浮在最后一行右端，如 "…结尾  12:34 ✓"
 *
 * 原理：<span> 保留 white-space: pre-wrap 处理换行，
 * timeTail 紧随其后作为 inline-flex 元素参与同一行文本流。
 */
export function TextBubble({ text, timeTail }: TextBubbleProps) {
  return (
    <div className="text-bubble">
      <span style={{ whiteSpace: 'pre-wrap' }}>{text}</span>
      {timeTail != null && (
        <span className="text-bubble-tail" aria-hidden="true">
          {timeTail}
        </span>
      )}
    </div>
  );
}
