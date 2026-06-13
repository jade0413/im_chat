import { AudioPlayer } from '../../../../components/AudioPlayer';
import type { MessageContent } from '../../../../store/types';

export function VoiceBubble({ content }: { content: Extract<MessageContent, { kind: 'voice' }> }) {
  return <AudioPlayer durationMs={content.durationMs} />;
}
