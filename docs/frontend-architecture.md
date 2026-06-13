# IM 前端架构设计文档

> 版本：v1.0 | 日期：2026-06-13 | 作者：Claude（前端架构师角色）
>
> 目标：基于现有后端 API + WebSocket 协议，设计一套可运行、可扩展、可继续二开的前端基础版本。
> 参考风格：Telegram / Discord / 飞书 / 企业微信 的简洁三栏布局。

---

## 0. 目标与范围

### MVP 功能范围

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 注册 / 登录 / JWT 续签 | P0 | 账号密码 MVP；验证码二阶段 |
| 会话列表（C2C + 群聊） | P0 | 含未读角标、最后一条消息 |
| 发送文字消息 | P0 | 含 Emoji |
| 发送图片 | P0 | 客户端压缩缩略图 + MinIO 直传 |
| 发送文件 / 视频 | P1 | FileContent；video/* MIME 白名单需配置 |
| 发送语音 | P1 | 浏览器录音 API + MinIO 直传 |
| 消息撤回 | P1 | 2 分钟窗口 |
| 已读回执 | P1 | 绿色已读标记 |
| 群聊（建群、成员管理） | P1 | ≤500 人 |
| 离线消息同步 | P0 | 重连后 SYNC_REQ 拉齐 |
| 多端互踢提示 | P1 | KICK 帧 → 提示重新登录 |
| CS Widget（嵌入版） | P2 | 独立轻量包，不依赖主 App |

### 不在 MVP 范围

- 好友申请审批流程（按 D17 当前开放单聊）
- Typing 指示器
- 消息引用/Reaction
- 移动端离线推送（APNs/FCM）
- WebRTC 视频通话
- 消息全文搜索

---

## 1. 技术选型

### 主 App（im-web）

| 类别 | 选型 | 理由 |
|------|------|------|
| 框架 | **React 18 + TypeScript** | 生态最成熟，类型安全，Codex 生成质量高 |
| 构建 | **Vite 5** | 冷启动秒级，HMR 极快 |
| UI 组件 | **Ant Design 5.x** | 企业级，中文友好，内置 Upload/Modal/Dropdown 等IM 常用组件 |
| 状态管理 | **Zustand 4** | 轻量、无样板，IM 多 store 场景更清爽 |
| 路由 | **React Router v6** | 官方标准，嵌套路由支持好 |
| HTTP | **Axios + axios-interceptors** | 自动注入 JWT、处理 401 刷新 |
| WebSocket | **原生 WebSocket + protobufjs 7** | 二进制帧，与后端协议完全匹配 |
| 虚拟列表 | **@tanstack/react-virtual** | 大量消息场景不卡顿 |
| 样式 | **Tailwind CSS 3 + Ant Design token** | Tailwind 做布局间距，Ant Design 做组件样式 |
| 文件上传 | **原生 fetch + XMLHttpRequest** | MinIO 预签名直传，无需中间服务 |
| 录音 | **MediaRecorder API** | 浏览器原生，输出 audio/webm(opus) |
| 图片处理 | **Canvas API** | 客户端生成缩略图（200×200）|
| 时间 | **dayjs** | 轻量替代 moment |
| 国际化 | **react-i18next**（预留，MVP 只做中文）| — |
| 代码规范 | **ESLint + Prettier + Husky** | — |

### CS Widget（im-widget）

| 类别 | 选型 |
|------|------|
| 框架 | **Preact 10**（体积 ≈ 3KB，React API 兼容） |
| 构建 | **Vite（library mode）**，输出单文件 `widget.js` |
| 样式 | CSS-in-JS（行内样式，避免污染宿主页面） |
| 通信 | 同后端 REST API（无 WebSocket，访客端轻量）→ 收消息用轮询或 SSE（二阶段 WS） |

---

## 2. 项目结构

```
im-chat/
├── im-web/                   # 主 IM App
│   ├── index.html
│   ├── vite.config.ts
│   ├── tailwind.config.ts
│   ├── tsconfig.json
│   ├── package.json
│   ├── public/
│   │   └── favicon.ico
│   └── src/
│       ├── main.tsx              # 入口
│       ├── App.tsx               # 路由根
│       │
│       ├── proto/                # protobufjs 运行时 + 生成的 TS 类型
│       │   ├── generated/        # protoc-gen-ts 或 pbjs 生成的 .ts
│       │   ├── index.ts          # 统一导出
│       │   └── codec.ts          # Frame encode/decode 封装
│       │
│       ├── api/                  # HTTP 层
│       │   ├── client.ts         # axios 实例 + 拦截器
│       │   ├── auth.ts           # /api/v1/auth/*
│       │   ├── user.ts           # /api/v1/user/*
│       │   ├── group.ts          # /api/v1/groups/*
│       │   ├── message.ts        # /api/v1/convs/{convId}/messages/*（历史分页）
│       │   ├── file.ts           # /api/v1/files/* + MinIO 直传
│       │   └── types.ts          # API 响应 TypeScript 类型
│       │
│       ├── socket/               # WebSocket 层
│       │   ├── ImSocket.ts       # WS 连接管理（单例）
│       │   ├── handlers.ts       # 各 Cmd 的 dispatch handler
│       │   └── reconnect.ts      # 指数退避重连逻辑
│       │
│       ├── store/                # Zustand stores
│       │   ├── authStore.ts      # 用户信息、tokens
│       │   ├── socketStore.ts    # WS 连接状态
│       │   ├── convStore.ts      # 会话列表
│       │   ├── messageStore.ts   # 消息（按 conv_id 分片缓存）
│       │   └── uiStore.ts        # UI 状态（侧栏、Modal 等）
│       │
│       ├── hooks/                # 自定义 Hooks
│       │   ├── useSocket.ts      # 订阅 WS 事件
│       │   ├── useMessages.ts    # 分页加载 + 追加实时消息
│       │   ├── useFileUpload.ts  # 上传流程封装
│       │   ├── useRecorder.ts    # 语音录制
│       │   └── useInfiniteScroll.ts
│       │
│       ├── pages/
│       │   ├── auth/
│       │   │   ├── LoginPage.tsx
│       │   │   └── RegisterPage.tsx
│       │   └── main/
│       │       ├── MainLayout.tsx        # 三栏布局容器
│       │       ├── sidebar/
│       │       │   └── NavSidebar.tsx    # 左侧导航图标栏
│       │       ├── convlist/
│       │       │   ├── ConvListPanel.tsx # 会话列表面板
│       │       │   ├── ConvItem.tsx      # 单条会话
│       │       │   └── SearchBar.tsx
│       │       ├── chat/
│       │       │   ├── ChatPanel.tsx     # 右侧聊天区
│       │       │   ├── ChatHeader.tsx    # 标题栏（对方名字/群名/成员数）
│       │       │   ├── MessageList.tsx   # 虚拟滚动消息列表
│       │       │   ├── MessageBubble.tsx # 消息气泡（按类型分支渲染）
│       │       │   ├── InputBar.tsx      # 输入框工具栏
│       │       │   └── bubbles/
│       │       │       ├── TextBubble.tsx
│       │       │       ├── ImageBubble.tsx
│       │       │       ├── VoiceBubble.tsx
│       │       │       ├── FileBubble.tsx
│       │       │       ├── VideoBubble.tsx
│       │       │       └── SystemBubble.tsx  # NotificationContent 灰条
│       │       └── group/
│       │           ├── GroupInfoPanel.tsx
│       │           └── MemberList.tsx
│       │
│       ├── components/           # 通用 UI 组件
│       │   ├── Avatar.tsx
│       │   ├── Badge.tsx
│       │   ├── ImageViewer.tsx   # 图片预览（Ant Design Modal）
│       │   ├── AudioPlayer.tsx   # 语音播放进度条
│       │   ├── EmojiPicker.tsx
│       │   └── KickDialog.tsx    # 被踢下线提示框
│       │
│       └── utils/
│           ├── time.ts           # dayjs 格式化
│           ├── file.ts           # MIME 判断、大小格式化
│           ├── image.ts          # canvas 缩略图生成
│           └── snowflake.ts      # client_msg_id 生成（UUID）
│
└── im-widget/                # CS Widget 独立包
    ├── vite.config.ts
    ├── package.json
    └── src/
        ├── index.ts          # 入口：挂载 widget
        ├── Widget.tsx        # 气泡按钮 + 聊天面板
        ├── api.ts            # 调用 /api/v1/cs/widget/sessions
        └── style.ts          # 行内样式对象
```

---

## 3. 界面设计

### 3.1 布局结构（三栏）

```
┌──────────────────────────────────────────────────────────────────┐
│  NavSidebar │      ConvListPanel       │      ChatPanel           │
│   (64px)    │        (280px)           │      (flex: 1)           │
│             │                          │                          │
│  [聊天]图标  │  🔍 搜索框               │  ChatHeader              │
│  [联系人]   │  ─────────────────────  │  ─────────────────────── │
│  [群组]     │  ● 张三      未读 3      │                          │
│             │    你：好的              │  MessageList             │
│             │    12:30                 │  （虚拟滚动）            │
│             │  ─────────────────────  │                          │
│  [设置]     │  ● 产品群    未读 12     │  ─────────────────────── │
│  [退出]     │    李四：明天开会通知    │  InputBar                │
│             │    昨天                  │  [😀][📎][🎤]  [输入框]  [发送] │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 配色方案（参考飞书 / 企业微信）

```css
/* 主色 */
--color-primary:        #1890FF;   /* 蓝色，主按钮 / 选中状态 */
--color-primary-hover:  #40A9FF;
--color-primary-light:  #E6F4FF;   /* 选中会话背景 */

/* 中性色 */
--color-bg:             #F5F5F5;   /* 整体背景 */
--color-surface:        #FFFFFF;   /* 卡片/面板 */
--color-border:         #E8E8E8;
--color-text-primary:   #1A1A1A;
--color-text-secondary: #8C8C8C;
--color-text-hint:      #BFBFBF;

/* 消息气泡 */
--bubble-self-bg:       #1890FF;   /* 自己发的消息 */
--bubble-self-text:     #FFFFFF;
--bubble-other-bg:      #FFFFFF;   /* 对方消息 */
--bubble-other-text:    #1A1A1A;

/* 状态 */
--color-online:         #52C41A;   /* 在线绿点 */
--color-unread:         #FF4D4F;   /* 未读红角标 */
```

### 3.3 消息气泡设计

```
[对方消息]                    [自己消息]
┌─────────────────────────┐  ┌─────────────────────────┐
│ 😊 张三                 │  │                   我 😊 │
│ ╔══════════════════╗    │  │    ╔══════════════════╗  │
│ ║ 你好，请问有什么  ║    │  │    ║ 好的，稍后给你   ║  │
│ ║ 可以帮您？       ║    │  │    ║ 发送文件。       ║  │
│ ╚══════════════════╝    │  │    ╚══════════════════╝  │
│              14:30  ✓✓  │  │  14:31  ✓✓              │
└─────────────────────────┘  └─────────────────────────┘

图片气泡：              语音气泡：
┌─────────────────┐     ┌──────────────────────────┐
│ [缩略图预览]    │     │ ▶  ────────────── 0:23  │
│ 点击全屏预览    │     └──────────────────────────┘
└─────────────────┘

文件气泡：              撤回消息：
┌─────────────────────┐  "你撤回了一条消息"（灰色居中）
│ 📄 设计稿v2.pdf    │
│    12.3 MB  [下载]  │
└─────────────────────┘
```

---

## 4. WebSocket 接入设计

### 4.1 连接流程

```typescript
// socket/ImSocket.ts

class ImSocket {
  private ws: WebSocket | null = null;
  private reconnectDelay = 1000;   // 初始 1s，最大 32s
  private maxDelay = 32000;

  connect(token: string, tenantId: number) {
    const url = `wss://${HOST}/ws`;  // 网关 WS 地址
    this.ws = new WebSocket(url);
    this.ws.binaryType = 'arraybuffer';

    this.ws.onopen = () => {
      // 1. 发送 AUTH 帧（Cmd=1）
      this.sendFrame(Cmd.AUTH, AuthReq.encode({
        token,
        tenantId,
        deviceId: getOrCreateDeviceId(),  // localStorage 持久化
        platform: Platform.WEB,
        appVersion: APP_VERSION,
        timestamp: Date.now() / 1000 | 0,
      }).finish());
    };

    this.ws.onmessage = (e) => {
      const frame = Frame.decode(new Uint8Array(e.data));
      handlers.dispatch(frame);  // 分发给各 Cmd handler
    };

    this.ws.onclose = () => this.scheduleReconnect(token, tenantId);
  }

  private scheduleReconnect(token: string, tenantId: number) {
    setTimeout(() => {
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, this.maxDelay);
      this.connect(token, tenantId);
    }, this.reconnectDelay + Math.random() * 1000);  // 加抖动
  }

  sendFrame(cmd: Cmd, body: Uint8Array = new Uint8Array()) {
    const frame = Frame.encode({ version: 1, reqId: nextReqId(), cmd, body }).finish();
    this.ws?.send(frame);
  }
}
```

### 4.2 Cmd Handler 分发表

```typescript
// socket/handlers.ts

const handlerMap: Record<Cmd, (body: Uint8Array) => void> = {
  [Cmd.AUTH_ACK]:      handleAuthAck,      // 连接成功 → 触发 SYNC_REQ
  [Cmd.KICK]:          handleKick,          // 被踢 → 弹框 → 清 token
  [Cmd.PONG]:          handlePong,          // 更新心跳时间戳
  [Cmd.MSG_PUSH]:      handleMsgPush,       // 新消息 → 追加到 messageStore
  [Cmd.MSG_SEND_ACK]:  handleMsgSendAck,   // 发送确认 → 更新本地消息状态
  [Cmd.SYNC_RESP]:     handleSyncResp,      // 离线消息 → 批量写入 store
  [Cmd.READ_NOTIFY]:   handleReadNotify,    // 对方已读 → 更新 read_seq
  [Cmd.REVOKE_NOTIFY]: handleRevokeNotify, // 撤回 → 更新消息状态
  [Cmd.CONV_NOTIFY]:   handleConvNotify,   // 会话元数据变更
  [Cmd.ERROR]:         handleError,
};
```

### 4.3 连接后初始化流程

```
AUTH_ACK(code=0)
    │
    ├── 重置 reconnectDelay = 1000
    ├── 从 store 读取所有 conv_id 的本地 max_seq
    │
    └── 发送 SYNC_REQ {
          conv_versions: [{ conv_id, local_max_seq }, ...],
          conv_list_version: store.convListVersion
        }
            │
            └── SYNC_RESP → 
                  ├── 更新/添加会话列表（ConvInfo）
                  ├── 追加各会话的离线消息
                  └── full_sync=true → 清本地 DB，全量拉取
```

### 4.4 发送消息流程

```typescript
async function sendMessage(convId: number, content: MsgContent) {
  const clientMsgId = crypto.randomUUID();

  // 1. 乐观更新：立即在 UI 显示（状态=SENDING）
  messageStore.addOptimistic({ clientMsgId, content, status: 'sending' });

  // 2. 发 WS 帧
  socket.sendFrame(Cmd.MSG_SEND, MsgSend.encode({
    clientMsgId,
    convId,  // 或 toUserId / groupId（首次单聊）
    content,
  }).finish());

  // 3. 等待 MSG_SEND_ACK（通过 reqId 配对，超时 10s）
  //    ACK 到达 → 更新消息 serverMsgId/seq/status=SENT
  //    超时 → 状态=FAILED，显示重发按钮
}
```

---

## 5. 状态管理设计

### 5.1 authStore

```typescript
interface AuthState {
  user: {
    userId: number;
    tenantId: number;
    nickname: string;
    avatar: string;
    userType: number;
    isAgent: boolean;
  } | null;
  accessToken: string | null;    // 存内存，不存 localStorage
  refreshToken: string | null;   // 存 localStorage（加密存储）
  isLoggedIn: boolean;
  
  // Actions
  login: (tokens: TokenResponse) => Promise<void>;
  logout: () => void;
  refreshAccessToken: () => Promise<void>;
}
```

### 5.2 convStore

```typescript
interface ConvState {
  conversations: Map<number, ConvInfo>;   // convId → ConvInfo
  convListVersion: number;
  activeConvId: number | null;
  
  // Actions
  upsertConv: (conv: ConvInfo) => void;
  removeConv: (convId: number) => void;
  setActive: (convId: number) => void;
  updateReadSeq: (convId: number, readSeq: number) => void;
}
```

### 5.3 messageStore

```typescript
// 消息按 convId 分片存储，避免全量加载
interface MessageState {
  // convId → 消息数组（按 seq 排序，最多保留 200 条，更早走 REST 分页）
  messages: Map<number, Message[]>;
  // convId → 是否还有更多历史
  hasMore: Map<number, boolean>;
  
  // Actions
  appendMessages: (convId: number, msgs: Message[]) => void;
  prependHistory: (convId: number, msgs: Message[]) => void;
  updateMessage: (convId: number, clientMsgId: string, patch: Partial<Message>) => void;
  revokeMessage: (convId: number, seq: number) => void;
}

interface Message {
  clientMsgId: string;
  serverMsgId?: number;
  seq?: number;
  convId: number;
  sender: Sender;
  content: MsgContent;
  sendTime: number;
  status: 'sending' | 'sent' | 'failed' | 'revoked';
  readSeq?: number;   // 对方已读到此 seq
}
```

---

## 6. HTTP API 封装

### 6.1 Axios 拦截器

```typescript
// api/client.ts

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'X-Tenant-Id': TENANT_ID },
});

// 请求拦截：注入 JWT
client.interceptors.request.use((config) => {
  const token = authStore.getState().accessToken;
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

// 响应拦截：401 → 刷新 Token → 重试
client.interceptors.response.use(
  (res) => {
    if (res.data.code !== 0) throw new ApiError(res.data.code, res.data.message);
    return res.data.data;
  },
  async (err) => {
    if (err.response?.status === 401 && !err.config._retry) {
      err.config._retry = true;
      await authStore.getState().refreshAccessToken();
      return client(err.config);
    }
    throw err;
  }
);
```

### 6.2 文件上传流程

```typescript
// hooks/useFileUpload.ts

async function uploadFile(file: File): Promise<{ objectKey: string; thumbKey?: string }> {
  // 1. 图片：生成缩略图
  let thumbKey: string | undefined;
  if (file.type.startsWith('image/')) {
    const thumbBlob = await generateThumb(file, 200, 200);   // canvas 压缩
    const thumbPresign = await api.file.presign({ mime: file.type, size: thumbBlob.size });
    await fetch(thumbPresign.uploadUrl, { method: 'PUT', body: thumbBlob });
    await api.file.confirm({ objectKey: thumbPresign.objectKey });
    thumbKey = thumbPresign.objectKey;
  }

  // 2. 原文件（或视频/语音）
  const presign = await api.file.presign({ mime: file.type, size: file.size });
  await fetch(presign.uploadUrl, {
    method: 'PUT',
    body: file,
    headers: { 'Content-Type': file.type },
  });
  await api.file.confirm({ objectKey: presign.objectKey });

  return { objectKey: presign.objectKey, thumbKey };
}
```

### 6.3 主要 REST 接口列表

```typescript
// auth
POST   /api/v1/auth/register    { account, password, nickname }
POST   /api/v1/auth/login       { account, password, platform }
POST   /api/v1/auth/refresh     { refreshToken }

// user
GET    /api/v1/user/me                      // 当前用户信息
GET    /api/v1/user/:userId                 // 查询用户

// message（历史分页）——URL 以 Controller 实际路径为准
GET    /api/v1/convs/{convId}/messages?end_seq=&limit=20
POST   /api/v1/convs/{convId}/messages/{seq}/revoke

// file
POST   /api/v1/files/presign    { mime, size }   → { uploadUrl, objectKey }
POST   /api/v1/files/confirm    { objectKey }

// group
POST   /api/v1/groups           { name, memberIds }
GET    /api/v1/groups/:groupId
POST   /api/v1/groups/:groupId/members   { userIds }
DELETE /api/v1/groups/:groupId/members/:userId
PATCH  /api/v1/groups/:groupId           { name }

// 文件下载：直接通过 MinIO 公开读 URL 或预签名 GET URL
GET    /api/v1/files/:objectKey/download   → { downloadUrl }（预签名 GET，5 分钟有效）
```

---

## 7. 核心页面详细设计

### 7.1 登录/注册页

```
┌──────────────────────────────────────────┐
│           🌟  IM Chat                    │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  账号（手机号/用户名）              │  │
│  └────────────────────────────────────┘  │
│  ┌────────────────────────────────────┐  │
│  │  密码                              │  │
│  └────────────────────────────────────┘  │
│                                          │
│  [  登录  ]    没有账号？ 注册           │
│                                          │
└──────────────────────────────────────────┘
```

**行为**：
- 登录成功 → 存 accessToken（内存）+ refreshToken（localStorage）→ 建 WS 连接 → 跳转 `/chat`
- 刷新页面 → 读 refreshToken → 调 `/refresh` 获取新 accessToken → 建连

### 7.2 主界面 MainLayout

```tsx
// pages/main/MainLayout.tsx
export function MainLayout() {
  const { activeConvId } = useConvStore();

  return (
    <div className="flex h-screen bg-gray-100">
      <NavSidebar />                           {/* 64px 固定 */}
      <ConvListPanel />                        {/* 280px 固定 */}
      <div className="flex-1 flex flex-col">
        {activeConvId ? (
          <ChatPanel convId={activeConvId} />
        ) : (
          <EmptyState message="选择一个会话开始聊天" />
        )}
      </div>
    </div>
  );
}
```

### 7.3 输入栏 InputBar

工具栏按钮：

| 按钮 | 功能 |
|------|------|
| 😊 | Emoji 选择器（EmojiPicker） |
| 🖼️ | 选图片（`accept="image/*"`） |
| 📎 | 选文件/视频（`accept="*"` 带 MIME 过滤） |
| 🎤 | 按住录音（MediaRecorder，松开发送） |
| Enter | 发送；Shift+Enter 换行 |
| Ctrl+Enter | 发送（可配置） |

```tsx
// 录音交互
<button
  onMouseDown={startRecording}
  onMouseUp={stopAndSend}
  onTouchStart={startRecording}
  onTouchEnd={stopAndSend}
>
  按住说话
</button>
```

### 7.4 消息列表 MessageList（虚拟滚动）

```tsx
// pages/main/chat/MessageList.tsx
import { useVirtualizer } from '@tanstack/react-virtual';

export function MessageList({ convId }: { convId: number }) {
  const messages = useMessageStore((s) => s.messages.get(convId) ?? []);
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: messages.length,
    getScrollElement: () => parentRef.current,
    estimateSize: () => 72,         // 预估消息高度
    overscan: 10,
  });

  // 新消息自动滚底
  useEffect(() => {
    virtualizer.scrollToIndex(messages.length - 1, { behavior: 'smooth' });
  }, [messages.length]);

  // 滚到顶 → 加载更多历史
  useInfiniteScroll(parentRef, () => loadHistory(convId));

  return (
    <div ref={parentRef} className="flex-1 overflow-y-auto p-4">
      <div style={{ height: virtualizer.getTotalSize() }}>
        {virtualizer.getVirtualItems().map((item) => (
          <MessageBubble key={item.key} message={messages[item.index]} />
        ))}
      </div>
    </div>
  );
}
```

### 7.5 消息气泡 MessageBubble

```tsx
function MessageBubble({ message }: { message: Message }) {
  const isSelf = message.sender.userId === authStore.getState().user?.userId;
  const content = message.content;

  return (
    <div className={`flex ${isSelf ? 'flex-row-reverse' : 'flex-row'} gap-2 mb-3`}>
      <Avatar userId={message.sender.userId} />
      <div className="max-w-[60%]">
        {!isSelf && <div className="text-xs text-gray-500 mb-1">{message.sender.nickname}</div>}
        <div className={`rounded-2xl px-4 py-2 ${isSelf ? 'bg-blue-500 text-white' : 'bg-white'}`}>
          {content.text     && <TextBubble content={content.text} />}
          {content.image    && <ImageBubble content={content.image} />}
          {content.voice    && <VoiceBubble content={content.voice} />}
          {content.file     && <FileBubble content={content.file} />}
          {content.notification && <SystemBubble content={content.notification} />}
        </div>
        <div className={`text-xs mt-1 flex gap-1 ${isSelf ? 'justify-end' : 'justify-start'}`}>
          <span className="text-gray-400">{formatTime(message.sendTime)}</span>
          {isSelf && <ReadStatus message={message} />}
        </div>
      </div>
    </div>
  );
}
```

---

## 8. protobuf 前端接入

### 8.1 安装与生成

```bash
# 安装
npm install protobufjs protobufjs-cli

# 从 proto 文件生成 TypeScript 类型（放在 proto/generated/）
npx pbjs -t static-module -w es6 --es6 \
  --path ../../im-proto/proto \
  ws/frame.proto body/messages.proto common/content.proto common/enums.proto \
  -o src/proto/generated/bundle.js

npx pbts -o src/proto/generated/bundle.d.ts src/proto/generated/bundle.js
```

### 8.2 Frame 编解码封装

```typescript
// proto/codec.ts
import { im } from './generated/bundle';
const { ws: wsProto, body, common } = im;

export const { Frame, Cmd, AuthReq, AuthResp, KickNotify } = wsProto.v1;
export const { MsgSend, MsgPush, SyncReq, SyncResp, ReadReport } = body.v1;
export const { MsgContent, TextContent, ImageContent, VoiceContent, FileContent } = common.v1;

export function encodeFrame(cmd: number, bodyBytes: Uint8Array = new Uint8Array()): ArrayBuffer {
  const frame = Frame.create({ version: 1, reqId: nextReqId(), cmd, body: bodyBytes });
  return Frame.encode(frame).finish().buffer;
}

export function decodeFrame(data: ArrayBuffer): im.ws.v1.IFrame {
  return Frame.decode(new Uint8Array(data));
}
```

---

## 9. 安全要点

| 项目 | 实现 |
|------|------|
| JWT 存储 | accessToken 存内存（JS 变量），不存 localStorage/cookie |
| refreshToken | 存 localStorage（AES 加密，key 来自设备指纹） |
| HTTPS/WSS | 生产必须全 TLS |
| XSS | 消息内容不用 `dangerouslySetInnerHTML`；用户输入 HTML 转义 |
| 文件下载 | 用预签名 URL（5 分钟过期），不暴露 MinIO 直接地址 |
| 被踢处理 | KICK 帧到达 → 清所有 token → 跳 /login |
| CORS | 后端 `IM_GATEWAY_ALLOWED_ORIGINS` 限制真实域名 |

---

## 10. 工程化配置

### 10.1 环境变量（`.env` 不入库）

```
# .env.development
VITE_API_BASE_URL=http://localhost:8081
VITE_WS_HOST=localhost:9090
VITE_TENANT_ID=1

# .env.production
VITE_API_BASE_URL=https://api.yourdomain.com
VITE_WS_HOST=gateway.yourdomain.com
VITE_TENANT_ID=1
```

### 10.2 vite.config.ts

```typescript
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8081',   // 开发时代理避免 CORS
    },
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vendor:  ['react', 'react-dom', 'react-router-dom'],
          antd:    ['antd'],
          proto:   ['protobufjs'],
          zustand: ['zustand'],
        },
      },
    },
  },
});
```

---

## 11. CS Widget 设计

Widget 是独立打包的轻量嵌入组件，企业通过一段 `<script>` 嵌入官网。

### 11.1 嵌入方式

```html
<!-- 企业网站底部加这一段 -->
<script>
  window.IMWidgetConfig = { tenantId: 123, primaryColor: '#1890FF' };
</script>
<script src="https://cdn.yourdomain.com/widget/widget.js" defer></script>
```

### 11.2 Widget 交互流程

```
页面加载
  │
  ├── 读取 localStorage.im_visitor_token（无则 crypto.randomUUID() 生成并存储）
  │
  ├── 渲染悬浮气泡按钮（右下角）
  │       │
  │       └── 点击展开聊天窗口
  │
  └── 点击时调用 POST /api/v1/cs/widget/sessions
        │
        ├── 获得 { accessToken, conversationId, displayName, csStatus }
        ├── 建立 WS 连接（accessToken）
        ├── 订阅 conversationId 的消息
        └── 显示聊天界面（历史消息 + 输入框）
```

### 11.3 Widget UI

```
┌─────────────────────────────────┐
│  🟢 在线客服                 ✕  │  ← ChatHeader（坐席头像+名字/在线状态）
├─────────────────────────────────┤
│                                  │
│  [系统] 欢迎！有什么可以帮您？   │  ← 欢迎语（widget_config.welcomeMsg）
│                                  │
│          你：你好                │
│                                  │
│  [坐席] 您好，请问有什么需要？   │
│                                  │
│                                  │
├─────────────────────────────────┤
│  [📎]  输入消息...        [发送] │
└─────────────────────────────────┘

            [💬]  ← 收起时的悬浮气泡（右下角，有未读角标）
```

---

## 12. 开发路线图（分阶段给 Codex 执行）

### Phase 1 — 工程骨架 + 鉴权（2~3 天）

**目标**：脚手架跑起来，登录/注册可用，WS 能连上。

任务清单（Codex 执行顺序）：
1. 初始化 `im-web/` Vite + React 18 + TS 项目
2. 安装并配置 Ant Design 5、Tailwind CSS、Zustand、React Router v6
3. 生成 proto TypeScript 类型（proto/generated/）
4. 实现 `api/client.ts`（axios + JWT 拦截器）
5. 实现 `api/auth.ts`（register/login/refresh）
6. 实现 `store/authStore.ts`
7. 实现 `LoginPage.tsx` + `RegisterPage.tsx`
8. 实现 `socket/ImSocket.ts`（connect/AUTH/PING/PONG/KICK/重连）
9. 实现 `socket/reconnect.ts`（指数退避 + 抖动）
10. 路由：`/login`、`/register`、`/chat`（ProtectedRoute）
11. `MainLayout.tsx` 三栏骨架（静态，数据空）

验收：浏览器访问 `/login`，登录后跳转 `/chat`，控制台可见 `AUTH_ACK code=0`。

---

### Phase 2 — 会话列表 + 文字消息（3~4 天）

**目标**：可以和另一个账号发文字消息，消息实时送达。

任务清单：
1. 实现 `store/convStore.ts`
2. 实现 `store/messageStore.ts`（乐观更新 + seq 排序）
3. 实现 `socket/handlers.ts`（MSG_PUSH / SYNC_RESP / READ_NOTIFY / REVOKE_NOTIFY / CONV_NOTIFY）
4. 实现 AUTH_ACK 后发 `SYNC_REQ`（带全量 conv_versions）
5. 实现 `ConvListPanel.tsx`（会话列表 + 未读角标）
6. 实现 `ChatPanel.tsx`（布局容器）
7. 实现 `MessageList.tsx`（虚拟滚动）
8. 实现 `MessageBubble.tsx` + `TextBubble.tsx`
9. 实现 `InputBar.tsx`（纯文字 + 发送）
10. 实现 `useMessages.ts`（分页加载历史 REST 接口）
11. 实现 `api/message.ts`（历史分页）
12. 发送文字消息全流程（clientMsgId → MSG_SEND → MSG_SEND_ACK 配对）
13. 已读回执（READ_REPORT 发送 + READ_NOTIFY 处理，消息底部显示 ✓✓）

验收：两个浏览器 Tab 登录不同账号，互发文字，实时收到，读后显示已读。

---

### Phase 3 — 富媒体消息（3~4 天）

**目标**：可发图片、语音、文件、视频。

任务清单：
1. 实现 `api/file.ts`（presign / confirm / download）
2. 实现 `hooks/useFileUpload.ts`（预签名 + PUT 直传 + confirm）
3. 实现 `utils/image.ts`（canvas 缩略图生成 200×200）
4. 实现 `InputBar` 图片选择按钮（accept="image/*"）+ 发送 `ImageContent`
5. 实现 `ImageBubble.tsx`（缩略图展示 + 点击全屏 `ImageViewer`）
6. 实现 `hooks/useRecorder.ts`（MediaRecorder → audio/webm → MinIO → VoiceContent）
7. 实现 `InputBar` 按住录音按钮
8. 实现 `VoiceBubble.tsx`（▶ 播放 + 时长 + 进度条）
9. 实现文件选择（accept="*"，MIME 过滤）+ 发送 `FileContent`
10. 实现 `FileBubble.tsx`（图标 + 文件名 + 大小 + 下载按钮）
11. 实现视频选择（video/* MIME）+ 发送 `FileContent`（mime=video/mp4）
12. 实现 `VideoBubble.tsx`（HTML5 `<video>` 标签内联播放）
13. 实现上传进度条（XHR.upload.onprogress）

验收：发图片显示缩略图点击全屏；发语音可播放；发文件可下载；发视频可播放。

---

### Phase 4 — 群聊 + 消息管理（2~3 天）

**目标**：建群聊天，消息可撤回。

任务清单：
1. 实现 `api/group.ts`（建群/加人/踢人/改名）
2. 实现 `GroupInfoPanel.tsx`（群名/成员列表/管理入口）
3. 实现 `MemberList.tsx`
4. 实现群聊消息中 `NotificationContent` 系统消息 `SystemBubble.tsx`（灰条居中）
5. 实现消息右键菜单（撤回/复制）
6. 实现消息撤回（`POST /api/v1/convs/{convId}/messages/{seq}/revoke`）
7. 实现 `REVOKE_NOTIFY` 处理（UI 显示"撤回了一条消息"）
8. 实现 KICK 弹窗提示（`KickDialog.tsx`）

---

### Phase 5 — CS Widget（2~3 天，可并行）

**目标**：独立 Widget 包，可嵌入任意网页。

任务清单：
1. 初始化 `im-widget/`（Vite library mode + Preact）
2. 实现访客 token 管理（localStorage `im_visitor_token`）
3. 实现 `api.ts`（调用 `POST /api/v1/cs/widget/sessions`）
4. 实现 WS 连接（轻量版 ImSocket，只处理 MSG_PUSH/MSG_SEND/SYNC）
5. 实现气泡按钮 + 聊天窗口（CSS 行内样式，不污染宿主）
6. 实现文字消息收发
7. 调用 `GET /api/v1/cs/widget/config` 加载品牌配置（T36 完成后）
8. 调用 `GET /api/v1/cs/widget/availability` 显示在线/离线（T34 完成后）
9. 打包输出 `widget.js`（< 80KB gzip）

---

### Phase 6 — 打磨与部署（1~2 天）

1. 404 / 加载状态 / 空状态占位
2. 错误边界（Error Boundary）
3. 浏览器 Notification API（离焦时收到消息弹通知）
4. 主题切换（暗色模式预留）
5. Dockerfile（nginx 静态托管）
6. nginx 反向代理配置（`/api` → Java，`/ws` → Rust 网关）
7. CI/CD 接入

---

## 13. 关键注意事项（给 Codex）

1. **proto 类型来自 `src/proto/generated/`**，不要手写，通过 `pbjs + pbts` 生成。
2. **WS 是二进制**：`ws.binaryType = 'arraybuffer'`，每帧是一个 protobuf `Frame`。
3. **accessToken 不存 localStorage**，只存内存；页面刷新时用 refreshToken 换新 access token，然后重建 WS 连接。
4. **乐观更新**：发消息先加到列表（status=sending），ACK 到后更新 status，超时显示重发。
5. **虚拟滚动必须**：消息量大不用虚拟滚动会卡死，用 `@tanstack/react-virtual`。
6. **图片上传流程**：缩略图先传（canvas 压缩到 200px），原图后传，两个 `objectKey` 都要 `confirm`。
7. **语音消息**：`MediaRecorder` 输出 `audio/webm;codecs=opus`，后端 MIME 白名单已包含 `audio/ogg`（webm/opus 兼容），上传后客户端播放用 `<audio>` 标签。
8. **视频消息**：后端 MIME 白名单需要手动添加 `video/mp4` 等（见 G1），前端发 `FileContent`，播放用 `<video>` 标签。
9. **`X-Tenant-Id` 请求头**必须在所有 REST 请求和 WS 的 AuthReq 里带上。
10. **SYNC_REQ 在每次重连后必须发**，带上所有会话的本地 max_seq，服务端返回缺口消息。
11. **conv_list_version**：首次 0（拉全量），之后带上服务端返回的版本（diff 同步）。
12. **会话 ID 是 64 位整数（Snowflake）**，JavaScript 的 `number` 只有 53 位精度，必须用 `BigInt` 或 `protobufjs` 的 `Long` 类型（pbjs 默认用 `Long.js`，不需要额外处理，但 JSON 序列化时注意）。

---

## 14. 目录 / 包初始化命令

```bash
# 主 App
cd im-chat
mkdir im-web && cd im-web
npm create vite@latest . -- --template react-ts
npm install antd @ant-design/icons
npm install tailwindcss postcss autoprefixer
npx tailwindcss init -p
npm install zustand react-router-dom axios
npm install @tanstack/react-virtual
npm install protobufjs protobufjs-cli
npm install dayjs
npm install -D @types/node eslint prettier

# Widget
cd ../
mkdir im-widget && cd im-widget
npm create vite@latest . -- --template preact-ts
npm install -D vite
```
