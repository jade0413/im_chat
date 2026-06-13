import { create } from 'zustand';
import type { ConnectionState } from './types';

interface SocketState {
  status: ConnectionState;
  lastError?: string;
  heartbeatIntervalSec: number;
  setStatus: (status: ConnectionState, lastError?: string) => void;
  setHeartbeatInterval: (seconds: number) => void;
}

export const useSocketStore = create<SocketState>((set) => ({
  status: 'idle',
  heartbeatIntervalSec: 30,
  setStatus: (status, lastError) => set({ status, lastError }),
  setHeartbeatInterval: (heartbeatIntervalSec) => set({ heartbeatIntervalSec }),
}));
