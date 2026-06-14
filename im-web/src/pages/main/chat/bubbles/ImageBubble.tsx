import { useState } from 'react';
import { Spin } from 'antd';
import { ImageViewer } from '../../../../components/ImageViewer';
import { useMediaUrl } from '../../../../hooks/useMediaUrl';
import type { MessageContent } from '../../../../store/types';

export function ImageBubble({ content }: { content: Extract<MessageContent, { kind: 'image' }> }) {
  const [open, setOpen] = useState(false);
  // 乐观消息时有 previewUrl（本地 blob URL），已发送消息需要从后端取预签名 URL
  const thumbKey = content.thumbKey || content.objectKey;
  const { url: remoteUrl, loading } = useMediaUrl(content.previewUrl ? undefined : thumbKey);
  const { url: fullRemoteUrl } = useMediaUrl(content.previewUrl ? undefined : content.objectKey);
  const displaySrc = content.previewUrl ?? remoteUrl;
  const fullSrc = content.previewUrl ?? fullRemoteUrl;

  return (
    <>
      {loading && !displaySrc ? (
        <div
          style={{
            width: 220,
            height: 132,
            display: 'grid',
            placeItems: 'center',
            borderRadius: 8,
            background: 'rgba(148, 163, 184, 0.18)',
          }}
        >
          <Spin size="small" />
        </div>
      ) : displaySrc ? (
        <button
          type="button"
          style={{ border: 0, padding: 0, background: 'transparent', cursor: 'pointer' }}
          onClick={() => setOpen(true)}
        >
          <img
            alt=""
            src={displaySrc}
            style={{
              display: 'block',
              maxWidth: 260,
              maxHeight: 220,
              borderRadius: 8,
              objectFit: 'cover',
            }}
          />
        </button>
      ) : (
        <div
          style={{
            width: 220,
            height: 132,
            display: 'grid',
            placeItems: 'center',
            borderRadius: 8,
            background: 'rgba(148, 163, 184, 0.18)',
          }}
        >
          图片加载失败
        </div>
      )}
      <ImageViewer open={open} src={fullSrc} onClose={() => setOpen(false)} />
    </>
  );
}
