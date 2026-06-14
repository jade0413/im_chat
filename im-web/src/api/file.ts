import { apiClient } from './client';
import type { ConfirmFileRequest, FileMetaResponse, PresignFileRequest, PresignFileResponse } from './types';

export function presignFile(request: PresignFileRequest) {
  return apiClient.post<PresignFileRequest, PresignFileResponse>('/api/v1/files/presign', request);
}

export function getDownloadUrl(objectKey: string): Promise<string> {
  return apiClient.get<unknown, string>('/api/v1/files/download', { params: { key: objectKey } });
}

export function confirmFile(request: ConfirmFileRequest) {
  return apiClient.post<ConfirmFileRequest, FileMetaResponse>('/api/v1/files/confirm', request);
}

export async function putPresignedObject(
  uploadUrl: string,
  file: Blob,
  headers: Record<string, string> = {},
  onProgress?: (percent: number) => void,
) {
  await new Promise<void>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('PUT', uploadUrl);
    Object.entries(headers).forEach(([key, value]) => xhr.setRequestHeader(key, value));
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        reject(new Error(`文件上传失败：HTTP ${xhr.status}`));
      }
    };
    xhr.onerror = () => reject(new Error('文件上传网络异常'));
    xhr.send(file);
  });
}
