import { useEffect, useState } from 'react';
import { getDownloadUrl } from '../api/file';

// 模块级缓存，避免同一个 objectKey 被重复请求
const urlCache = new Map<string, string>();

/**
 * 从后端获取对象的预签名下载 URL，带内存缓存。
 * 同一 objectKey 在页面生命周期内只请求一次。
 */
export function useMediaUrl(objectKey: string | undefined) {
  const [url, setUrl] = useState<string | undefined>(() =>
    objectKey ? urlCache.get(objectKey) : undefined,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (!objectKey) return;
    if (urlCache.has(objectKey)) {
      setUrl(urlCache.get(objectKey));
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(undefined);
    getDownloadUrl(objectKey)
      .then((presignedUrl) => {
        if (cancelled) return;
        urlCache.set(objectKey, presignedUrl);
        setUrl(presignedUrl);
      })
      .catch(() => {
        if (cancelled) return;
        setError('获取资源链接失败');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [objectKey]);

  return { url, loading, error };
}
