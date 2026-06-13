import { FileOutlined, VideoCameraOutlined } from '@ant-design/icons';
import type { MessageContent } from '../../../../store/types';
import { formatFileSize } from '../../../../utils/file';

type FileContent = Extract<MessageContent, { kind: 'file' | 'video' }>;

export function FileBubble({ content }: { content: FileContent }) {
  const isVideo = content.kind === 'video';
  return (
    <div className="file-bubble">
      {isVideo ? <VideoCameraOutlined style={{ fontSize: 24 }} /> : <FileOutlined style={{ fontSize: 24 }} />}
      <div style={{ minWidth: 0, flex: 1 }}>
        <div className="file-name">{content.fileName}</div>
        <div className="file-size">{formatFileSize(content.size)}</div>
      </div>
    </div>
  );
}
