export function formatFileSize(bytes?: number): string {
  if (!bytes) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = bytes;
  let index = 0;
  while (size >= 1024 && index < units.length - 1) {
    size /= 1024;
    index += 1;
  }
  return `${size.toFixed(size >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

export function isImage(mime?: string): boolean {
  return Boolean(mime?.startsWith('image/'));
}

export function isVideo(mime?: string): boolean {
  return Boolean(mime?.startsWith('video/'));
}
