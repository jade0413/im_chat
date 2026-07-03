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
  isAgent?: boolean;
  agentStatus?: number;
  status: number;
  createdAt: string;
  /** D42 可分享的对外标识 */
  username?: string | null;
  /** D40 加我是否需要验证：1=需验证(默认) 0=免验证 */
  friendVerifyRequired?: number;
}

export type IdLike = string | number;

/** 其他用户的公开资料（不含账号）。username 是可分享的对外标识（D42）。 */
export interface UserPublicProfile {
  id: IdLike;
  nickname: string;
  avatar?: string;
  userType: number;
  verifiedType: number;
  username?: string | null;
}

/** 好友关系条目（GET /api/v1/friend/list）。 */
export interface FriendItem {
  userId: IdLike;
  remark: string;
  nickname: string;
  avatar?: string;
  username?: string | null;
}

/** 好友申请条目（GET /api/v1/friend/requests）。status: 0待处理 1已同意 2已拒绝 3已忽略。 */
export interface FriendRequestItem {
  requestId: IdLike;
  fromUserId: IdLike;
  toUserId: IdLike;
  note: string;
  status: number;
  autoAccepted: boolean;
  createTime: IdLike;
  peerUserId: IdLike;
  peerNickname: string;
  peerAvatar?: string;
  peerUsername?: string | null;
}

/** 发起申请结果（D40）。 */
export interface SendFriendRequestResult {
  result: 'pending' | 'accepted' | 'already_friend' | 'ok' | string;
  requestId?: IdLike | null;
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
  objectKey?: string;
  thumbKey?: string;
  fileName?: string;
  mime?: string;
  size?: number;
  durationMs?: number;
  width?: number;
  height?: number;
  codec?: string;
}

export interface PresignFileRequest {
  fileName: string;
  mime: string;
  size: number;
  durationMs?: number;
  sha256?: string;
}

export interface PresignFileResponse {
  fileId: IdLike;
  objectKey: string;
  uploadUrl: string;
  expiresAt: number;
  requiredHeaders?: Record<string, string>;
  instant?: boolean;
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

export interface DownloadFileResponse {
  objectKey: string;
  url: string;
  expiresAt: number;
  transformed: boolean;
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
  convId: IdLike;
  memberCount: number;
  changedUserIds: IdLike[];
}

/** 群成员列表条目（GET /api/v1/groups/:groupId/members） */
export interface GroupMemberItem {
  userId: IdLike;
  /** 1=成员 2=管理员 3=群主 */
  role: number;
  joinedAt: string;
}

export interface CsConvItemResponse {
  convId: IdLike;
  /** 1=open 2=assigned 3=resolved */
  csStatus: number;
  agentId: IdLike;
  visitorUserId: IdLike;
  visitorName: string;
  visitorOnline?: boolean;
  visitorReadSeq?: IdLike;
  lastMsgTimeMs: IdLike;
  lastMsgAbstract: string;
  maxSeq: IdLike;
}

export interface AgentConvListResponse {
  convs: CsConvItemResponse[];
  hasMore: boolean;
}

export interface CsInternalNoteResponse {
  id: IdLike;
  convId: IdLike;
  agentId: IdLike;
  content: string;
  createdAtMs: IdLike;
}

export interface CsInternalNoteListResponse {
  notes: CsInternalNoteResponse[];
}

export interface CreateCsInternalNoteRequest {
  content: string;
}

export interface WidgetConfigResponse {
  color: string;
  welcomeMsg: string;
  offlineMsg: string;
  displayName: string;
  position: 'bottom-right' | 'bottom-left' | string;
  poweredBy: boolean;
}

export interface AgentAvailabilityResponse {
  available: boolean;
  onlineAgentCount: number;
}

export interface WidgetSessionResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  conversationId: IdLike;
  visitorId: IdLike;
  displayName: string;
  isNewConversation: boolean;
  /** 1=open 2=assigned */
  csStatus: number;
}
