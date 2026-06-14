import { useEffect } from 'react';
import { imSocket } from '../../../socket/ImSocket';
import { useConvStore } from '../../../store/convStore';
import { ChatHeader } from './ChatHeader';
import { InputBar } from './InputBar';
import { MessageList } from './MessageList';

export function ChatPanel({ convId }: { convId: string }) {
  const conv = useConvStore((state) => state.conversations.get(convId));
  const markRead = useConvStore((state) => state.markRead);

  // 进入/切换会话时：乐观消除未读红点 + 上报已读给服务端
  useEffect(() => {
    markRead(convId);
    // 发送已读上报，让服务端同步到其他端；maxSeq 可能尚未到位，取当前值
    const maxSeq = conv?.maxSeq;
    if (maxSeq && maxSeq !== '0') {
      imSocket.sendReadReport(convId, maxSeq);
    }
  }, [convId]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <section className="chat-shell">
      <ChatHeader convId={convId} />
      <MessageList convId={convId} />
      <InputBar convId={convId} />
    </section>
  );
}
