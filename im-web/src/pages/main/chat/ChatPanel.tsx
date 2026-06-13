import { ChatHeader } from './ChatHeader';
import { InputBar } from './InputBar';
import { MessageList } from './MessageList';

export function ChatPanel({ convId }: { convId: string }) {
  return (
    <section className="chat-shell">
      <ChatHeader convId={convId} />
      <MessageList convId={convId} />
      <InputBar convId={convId} />
    </section>
  );
}
