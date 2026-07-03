import { useState } from 'react';
import { confirmFile, presignFile, putPresignedObject } from '../api/file';
import { createImageThumbnail, getImageSize } from '../utils/image';

export interface UploadedObject {
  objectKey: string;
  thumbKey?: string;
  width?: number;
  height?: number;
}

export function useFileUpload() {
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);

  async function upload(file: File): Promise<UploadedObject> {
    setUploading(true);
    setProgress(0);
    try {
      let thumbKey: string | undefined;
      let width: number | undefined;
      let height: number | undefined;

      if (file.type.startsWith('image/')) {
        const size = await getImageSize(file);
        width = size.width;
        height = size.height;
        const thumb = await createImageThumbnail(file);
        thumbKey = await uploadObject({
          fileName: `${file.name}.thumb.jpg`,
          mime: 'image/jpeg',
          blob: thumb,
        });
      }

      const objectKey = await uploadObject({
        fileName: file.name,
        mime: file.type || 'application/octet-stream',
        blob: file,
        onProgress: setProgress,
      });
      return { objectKey, thumbKey, width, height };
    } finally {
      setUploading(false);
    }
  }

  return { upload, uploading, progress };
}

async function uploadObject({
  fileName,
  mime,
  blob,
  onProgress,
}: {
  fileName: string;
  mime: string;
  blob: Blob;
  onProgress?: (percent: number) => void;
}): Promise<string> {
  const presign = await presignFile({
    fileName,
    mime,
    size: blob.size,
    sha256: await sha256Hex(blob),
  });
  if (!presign.instant) {
    await putPresignedObject(presign.uploadUrl, blob, presign.requiredHeaders, onProgress);
    await confirmFile({ objectKey: presign.objectKey, size: blob.size, mime });
  } else {
    onProgress?.(100);
  }
  return presign.objectKey;
}

async function sha256Hex(blob: Blob): Promise<string | undefined> {
  if (!globalThis.crypto?.subtle) return undefined;
  const digest = await globalThis.crypto.subtle.digest('SHA-256', await blob.arrayBuffer());
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('');
}
