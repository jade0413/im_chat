export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
  timestamp: number;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  refreshExpiresIn: number;
}

export interface LoginRequest {
  account: string;
  password: string;
  platform: number;
}

export interface RegisterRequest extends LoginRequest {
  nickname?: string;
}

export interface UserProfile {
  id: IdLike;
  tenantId: IdLike;
  account: string;
  nickname: string;
  avatar?: string;
  userType: number;
  verifiedType: number;
  status: number;
  createdAt: string;
}

export type IdLike = string | number;

/** 其他用户的公开资料（不含账号）。 */
export interface UserPublicProfile {
  id: IdLike;
  nickname: string;
  avatar?: string;
  userType: number;
  verifiedType: number;
}

export interface MessageHistoryResponse {
  convId: IdLike;
  readSeq: IdLike;
  messages: MessageItemResponse[];
  hasMore: boolean;
}

export interface MessageItemResponse {
  convId: IdLike;
  seq: IdLike;
  serverMsgId: IdLike;
  clientMsgId: string;
  senderId: IdLike;
  sendTime: IdLike;
  msgType: number;
  status: number;
  revokeReason: number;
  text: string;
}

export interface PresignFileRequest {
  fileName: string;
  mime: string;
  size: number;
  durationMs?: number;
}

export interface PresignFileResponse {
  fileId: IdLike;
  objectKey: string;
  uploadUrl: string;
  expiresAt: number;
  requiredHeaders?: Record<string, string>;
}

export interface ConfirmFileRequest {
  objectKey: string;
  size?: number;
  mime?: string;
}

export interface FileMetaResponse {
  fileId: IdLike;
  objectKey: string;
  mime: string;
  size: number;
  durationMs?: number;
  status: number;
}

export interface CreateGroupRequest {
  name: string;
  memberUserIds: IdLike[];
}

export interface GroupResponse {
  groupId: IdLike;
  convId: IdLike;
  name: string;
  ownerId: IdLike;
  memberCount: number;
}

export interface AddGroupMembersRequest {
  userIds: IdLike[];
}

export interface GroupMemberChangeResponse {
  groupId: IdLike;
  memberCount: number;
}
