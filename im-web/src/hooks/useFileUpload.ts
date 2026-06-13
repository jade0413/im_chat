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
        const thumbPresign = await presignFile({
          fileName: `${file.name}.thumb.jpg`,
          mime: 'image/jpeg',
          size: thumb.size,
        });
        await putPresignedObject(thumbPresign.uploadUrl, thumb, thumbPresign.requiredHeaders);
        await confirmFile({ objectKey: thumbPresign.objectKey, size: thumb.size, mime: 'image/jpeg' });
        thumbKey = thumbPresign.objectKey;
      }

      const presign = await presignFile({
        fileName: file.name,
        mime: file.type || 'application/octet-stream',
        size: file.size,
      });
      await putPresignedObject(presign.uploadUrl, file, presign.requiredHeaders, setProgress);
      await confirmFile({ objectKey: presign.objectKey, size: file.size, mime: file.type });
      return { objectKey: presign.objectKey, thumbKey, width, height };
    } finally {
      setUploading(false);
    }
  }

  return { upload, uploading, progress };
}
