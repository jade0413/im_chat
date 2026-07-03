import { useState } from 'react';
import { App as AntApp, Button, Modal, Progress, Spin, Tooltip } from 'antd';
import { DownloadOutlined, LoadingOutlined, PlayCircleFilled, VideoCameraOutlined } from '@ant-design/icons';
import { downloadObject } from '../../../../api/file';
import { useMediaUrl } from '../../../../hooks/useMediaUrl';
import type { MessageContent } from '../../../../store/types';
import { saveBlob } from '../../../../utils/download';
import { formatFileSize } from '../../../../utils/file';

type VideoContent = Extract<MessageContent, { kind: 'video' }>;

export function VideoBubble({ content }: { content: VideoContent }) {
  const { message } = AntApp.useApp();
  const [open, setOpen] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [progress, setProgress] = useState<number | undefined>(undefined);
  const thumbKey = content.thumbKey || undefined;
  const { url: thumbUrl, loading: thumbLoading } = useMediaUrl(content.previewUrl ? undefined : thumbKey);
  const { url: playbackUrl, loading: playbackLoading } = useMediaUrl(
    content.previewUrl ? undefined : content.objectKey,
    { variant: 'playback' },
  );
  const coverSrc = content.previewUrl ?? thumbUrl;
  const videoSrc = content.previewUrl ?? playbackUrl;

  async function handleDownload() {
    if (!content.objectKey) return;
    setDownloading(true);
    setProgress(undefined);
    try {
      const { blob } = await downloadObject(content.objectKey, {
        variant: 'playback',
        onProgress: (next) => setProgress(next.percent),
      });
      saveBlob(blob, content.fileName || 'video.mp4');
      setProgress(100);
    } catch (error) {
      void message.error(error instanceof Error ? error.message : '视频下载失败');
    } finally {
      setDownloading(false);
    }
  }

  return (
    <>
      <div className="video-bubble">
        <button
          type="button"
          className="video-cover"
          onClick={() => setOpen(true)}
          aria-label="播放视频"
        >
          {coverSrc ? (
            <img src={coverSrc} alt="" />
          ) : thumbLoading ? (
            <Spin size="small" />
          ) : (
            <VideoCameraOutlined style={{ fontSize: 30 }} />
          )}
          <span className="video-play">
            <PlayCircleFilled />
          </span>
          {content.durationMs ? <span className="video-duration">{formatDuration(content.durationMs)}</span> : null}
        </button>
        <div className="video-info-row">
          <div className="video-title">{content.fileName || '视频'}</div>
          <Tooltip title="下载视频">
            <Button
              type="text"
              size="small"
              icon={downloading ? <LoadingOutlined /> : <DownloadOutlined />}
              onClick={handleDownload}
              disabled={downloading}
              aria-label="下载视频"
            />
          </Tooltip>
        </div>
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
      <Modal
        open={open}
        footer={null}
        centered
        width="min(860px, 92vw)"
        onCancel={() => setOpen(false)}
        destroyOnClose
      >
        <div className="video-player-modal">
          {videoSrc ? (
            <video src={videoSrc} poster={coverSrc} controls autoPlay />
          ) : playbackLoading ? (
            <Spin />
          ) : (
            <div>视频加载失败</div>
          )}
        </div>
      </Modal>
    </>
  );
}

function formatDuration(durationMs: number): string {
  const totalSeconds = Math.max(0, Math.round(durationMs / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}
