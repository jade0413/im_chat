import { Avatar } from 'antd';
import type { AvatarProps } from 'antd';

interface AppAvatarProps extends AvatarProps {
  name?: string;
  src?: string;
}

export function AppAvatar({ name, src, size = 40, ...props }: AppAvatarProps) {
  const text = (name || 'IM').trim().slice(0, 1).toUpperCase();
  return (
    <Avatar className="app-avatar" src={src} size={size} {...props}>
      {text}
    </Avatar>
  );
}
