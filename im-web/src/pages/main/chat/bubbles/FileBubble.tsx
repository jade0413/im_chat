import { useState } from 'react';
import { Button } from 'antd';
import { DownloadOutlined, FileOutlined, LoadingOutlined, VideoCameraOutlined } from '@ant-design/icons';
import { getDownloadUrl } from '../../../../api/file';
import type { MessageContent } from '../../../../store/types';
import { formatFileSize } from '../../../../utils/file';

type FileContent = Extract<MessageContent, { kind: 'file' | 'video' }>;

export function FileBubble({ content }: { content: FileContent }) {
  const isVideo = content.kind === 'video';
  const [downloading, setDownloading] = useState(false);

  async function handleDownload() {
    if (!content.objectKey) return;
    setDownloading(true);
    try {
      const url = await getDownloadUrl(content.objectKey);
      const a = document.createElement('a');
      a.href = url;
      a.download = content.fileName;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } catch {
      // 静默失败，浏览器会显示网络错误
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="file-bubble">
      {isVideo ? <VideoCameraOutlined style={{ fontSize: 24 }} /> : <FileOutlined style={{ fontSize: 24 }} />}
      <div style={{ minWidth: 0, flex: 1 }}>
        <div className="file-name">{content.fileName}</div>
        <div className="file-size">{formatFileSize(content.size ?? 0)}</div>
      </div>
      <Button
        type="text"
        size="small"
        icon={downloading ? <LoadingOutlined /> : <DownloadOutlined />}
        onClick={handleDownload}
        disabled={downloading}
        aria-label="下载"
      />
    </div>
  );
}
