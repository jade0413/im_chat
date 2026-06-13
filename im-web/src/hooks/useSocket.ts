import { useEffect } from 'react';
import { imSocket } from '../socket/ImSocket';
import { useAuthStore } from '../store/authStore';

export function useSocket() {
  const accessToken = useAuthStore((state) => state.accessToken);

  useEffect(() => {
    if (!accessToken) {
      imSocket.disconnect();
      return;
    }
    imSocket.connect(accessToken);
    return () => imSocket.disconnect();
  }, [accessToken]);

  return imSocket;
}
