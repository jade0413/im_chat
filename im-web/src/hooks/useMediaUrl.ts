import { useEffect, useState } from 'react';
import { getDownloadUrl } from '../api/file';

// 模块级缓存，避免同一个 objectKey 被重复请求
const urlCache = new Map<string, string>();

/**
 * 从后端获取对象的预签名下载 URL，带内存缓存。
 * 同一 objectKey 在页面生命周期内只请求一次。
 */
export function useMediaUrl(objectKey: string | undefined, options: { variant?: string } = {}) {
  const cacheKey = objectKey ? mediaCacheKey(objectKey, options.variant) : undefined;
  const [url, setUrl] = useState<string | undefined>(() =>
    cacheKey ? urlCache.get(cacheKey) : undefined,
  );
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (!objectKey || !cacheKey) return;
    if (urlCache.has(cacheKey)) {
      setUrl(urlCache.get(cacheKey));
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(undefined);
    getDownloadUrl(objectKey, options.variant)
      .then((presignedUrl) => {
        if (cancelled) return;
        urlCache.set(cacheKey, presignedUrl);
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
  }, [cacheKey, objectKey, options.variant]);

  return { url, loading, error };
}

function mediaCacheKey(objectKey: string, variant?: string): string {
  return variant ? `${objectKey}#${variant}` : objectKey;
}
