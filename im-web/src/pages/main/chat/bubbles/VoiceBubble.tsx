import { AudioPlayer } from '../../../../components/AudioPlayer';
import { useMediaUrl } from '../../../../hooks/useMediaUrl';
import type { MessageContent } from '../../../../store/types';

export function VoiceBubble({ content }: { content: Extract<MessageContent, { kind: 'voice' }> }) {
  const { url } = useMediaUrl(content.objectKey);
  return <AudioPlayer durationMs={content.durationMs} src={url} />;
}
