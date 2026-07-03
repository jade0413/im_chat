import { useState } from 'react';
import { App as AntApp, Button, Progress } from 'antd';
import { DownloadOutlined, FileOutlined, LoadingOutlined } from '@ant-design/icons';
import { downloadObject } from '../../../../api/file';
import type { MessageContent } from '../../../../store/types';
import { saveBlob } from '../../../../utils/download';
import { formatFileSize } from '../../../../utils/file';

type FileContent = Extract<MessageContent, { kind: 'file' }>;

export function FileBubble({ content }: { content: FileContent }) {
  const { message } = AntApp.useApp();
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState<number | undefined>(undefined);

  async function handleDownload() {
    if (!content.objectKey) return;
    setDownloading(true);
    setProgress(undefined);
    try {
      const { blob } = await downloadObject(content.objectKey, {
        onProgress: (next) => setProgress(next.percent),
      });
      saveBlob(blob, content.fileName);
      setProgress(100);
    } catch (error) {
      void message.error(error instanceof Error ? error.message : '文件下载失败');
    } finally {
      setDownloading(false);
    }
  }

  return (
    <div className="file-bubble">
      <FileOutlined style={{ fontSize: 24 }} />
      <div className="file-main">
        <div className="file-name">{content.fileName}</div>
        <div className="file-size">{formatFileSize(content.size ?? 0)}</div>
        {downloading && (
          <Progress
            percent={progress}
            size="small"
            showInfo={progress !== undefined}
            status="active"
          />
        )}
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
