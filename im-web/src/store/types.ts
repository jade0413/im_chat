import type { IdLike, UserProfile } from '../api/types';

export type ConnectionState = 'idle' | 'connecting' | 'connected' | 'reconnecting' | 'closed' | 'error';
export type MessageStatus = 'sending' | 'sent' | 'failed' | 'revoked';
export type ContentKind = 'text' | 'image' | 'voice' | 'file' | 'video' | 'notification' | 'custom';

export interface SessionUser extends UserProfile {
  id: IdLike;
  tenantId: IdLike;
}

export interface Conversation {
  convId: string;
  type: number;
  title: string;
  avatar?: string;
  peerUserId?: string;
  groupId?: string;
  maxSeq: string;
  /** 本端已读位置（用于未读角标计算） */
  readSeq: string;
  /** 对端已读位置（用于已读回执展示）；READ_NOTIFY 更新 */
  peerReadSeq?: string;
  pinned: boolean;
  muted: boolean;
  lastMsgAbstract: string;
  lastMsgTime?: string;
  csStatus?: string;
  visitorOnline?: boolean;
  visitorReadSeq?: string;
}

export interface SenderInfo {
  userId: string;
  nickname: string;
  avatar?: string;
  verifiedType?: number;
  userType?: number;
}

export type MessageContent =
  | { kind: 'text'; text: string }
  | { kind: 'image'; objectKey: string; thumbKey?: string; width?: number; height?: number; size?: number; mime?: string; previewUrl?: string }
  | { kind: 'voice'; objectKey: string; durationMs: number; size?: number; codec?: string }
  | { kind: 'file'; objectKey: string; fileName: string; size?: number; mime?: string }
  | { kind: 'video'; objectKey: string; fileName: string; size?: number; mime?: string; previewUrl?: string }
  | { kind: 'notification'; eventType: string; payload?: string }
  | { kind: 'custom'; customType: string; payload?: string };

export interface ChatMessage {
  clientMsgId: string;
  serverMsgId?: string;
  seq?: string;
  convId: string;
  sender: SenderInfo;
  content: MessageContent;
  sendTime: string;
  status: MessageStatus;
  readSeq?: string;
}
