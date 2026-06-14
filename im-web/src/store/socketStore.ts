import { create } from 'zustand';
import type { ConnectionState } from './types';

export interface SocketLogEntry {
  id: number;
  time: number;
  level: 'info' | 'warn' | 'error';
  message: string;
  detail?: string;
}

interface SocketState {
  status: ConnectionState;
  lastError?: string;
  lastEvent?: string;
  lastUpdatedAt?: number;
  logs: SocketLogEntry[];
  heartbeatIntervalSec: number;
  setStatus: (status: ConnectionState, lastError?: string) => void;
  setEvent: (lastEvent: string, lastError?: string) => void;
  addLog: (level: SocketLogEntry['level'], message: string, detail?: string) => void;
  clearLogs: () => void;
  setHeartbeatInterval: (seconds: number) => void;
}

const MAX_LOGS = 200;

function appendLog(logs: SocketLogEntry[], level: SocketLogEntry['level'], message: string, detail?: string) {
  const next = [
    {
      id: Date.now() + Math.random(),
      time: Date.now(),
      level,
      message,
      detail,
    },
    ...logs,
  ];
  return next.slice(0, MAX_LOGS);
}

export const useSocketStore = create<SocketState>((set) => ({
  status: 'idle',
  logs: [],
  heartbeatIntervalSec: 30,
  setStatus: (status, lastError) =>
    set((state) => ({
      status,
      lastError,
      lastUpdatedAt: Date.now(),
      logs: appendLog(state.logs, lastError ? 'warn' : 'info', `状态：${status}`, lastError),
    })),
  setEvent: (lastEvent, lastError) =>
    set((state) => ({
      lastEvent,
      lastError,
      lastUpdatedAt: Date.now(),
      logs: appendLog(state.logs, lastError ? 'warn' : 'info', lastEvent, lastError),
    })),
  addLog: (level, message, detail) =>
    set((state) => ({
      logs: appendLog(state.logs, level, message, detail),
    })),
  clearLogs: () => set({ logs: [] }),
  setHeartbeatInterval: (heartbeatIntervalSec) => set({ heartbeatIntervalSec }),
}));
