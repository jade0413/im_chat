import { apiClient } from './client';
import type {
  ConfirmFileRequest,
  DownloadFileResponse,
  FileMetaResponse,
  PresignFileRequest,
  PresignFileResponse,
} from './types';

export function presignFile(request: PresignFileRequest) {
  return apiClient.post<PresignFileRequest, PresignFileResponse>('/api/v1/files/presign', request);
}

export interface DownloadProgress {
  loaded: number;
  total?: number;
  percent?: number;
}

export function getDownloadUrl(objectKey: string, variant?: string): Promise<string> {
  return apiClient.get<unknown, string>('/api/v1/files/download', {
    params: {
      key: objectKey,
      variant,
    },
  });
}

export function getDownloadInfo(objectKey: string, variant?: string): Promise<DownloadFileResponse> {
  return apiClient.get<unknown, DownloadFileResponse>('/api/v1/files/download-info', {
    params: {
      key: objectKey,
      variant,
    },
  });
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

export async function downloadObject(
  objectKey: string,
  options: {
    variant?: string;
    onProgress?: (progress: DownloadProgress) => void;
  } = {},
): Promise<{ blob: Blob; info: DownloadFileResponse }> {
  const info = await getDownloadInfo(objectKey, options.variant);
  const blob = await downloadUrlAsBlob(info.url, options.onProgress);
  return { blob, info };
}

export function downloadUrlAsBlob(
  url: string,
  onProgress?: (progress: DownloadProgress) => void,
): Promise<Blob> {
  return new Promise<Blob>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('GET', url);
    xhr.responseType = 'blob';
    xhr.onprogress = (event) => {
      if (!onProgress) return;
      const total = event.lengthComputable ? event.total : undefined;
      onProgress({
        loaded: event.loaded,
        total,
        percent: total ? Math.round((event.loaded / total) * 100) : undefined,
      });
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(xhr.response as Blob);
      } else {
        reject(new Error(`文件下载失败：HTTP ${xhr.status}`));
      }
    };
    xhr.onerror = () => reject(new Error('文件下载网络异常'));
    xhr.send();
  });
}
