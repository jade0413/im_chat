import { useState } from 'react';
import { ImageViewer } from '../../../../components/ImageViewer';
import type { MessageContent } from '../../../../store/types';

export function ImageBubble({ content }: { content: Extract<MessageContent, { kind: 'image' }> }) {
  const [open, setOpen] = useState(false);
  const src = content.previewUrl;

  return (
    <>
      {src ? (
        <button type="button" style={{ border: 0, padding: 0, background: 'transparent', cursor: 'pointer' }} onClick={() => setOpen(true)}>
          <img
            alt=""
            src={src}
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
        <div style={{ width: 220, height: 132, display: 'grid', placeItems: 'center', borderRadius: 8, background: 'rgba(148, 163, 184, 0.18)' }}>
          图片消息
        </div>
      )}
      <ImageViewer open={open} src={src} onClose={() => setOpen(false)} />
    </>
  );
}
