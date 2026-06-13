import { Modal } from 'antd';

interface ImageViewerProps {
  open: boolean;
  src?: string;
  onClose: () => void;
}

export function ImageViewer({ open, src, onClose }: ImageViewerProps) {
  return (
    <Modal open={open} footer={null} centered onCancel={onClose} width="min(920px, 92vw)">
      {src && <img alt="" src={src} style={{ width: '100%', maxHeight: '78vh', objectFit: 'contain' }} />}
    </Modal>
  );
}
