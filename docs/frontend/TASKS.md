# im-web 前端任务拆分

> 日期：2026-06-13
> 依据：`docs/frontend-architecture.md`、`docs/protocol.md`、当前后端 Controller 与 proto。

## 0. 当前实现状态

状态：基础版已落地。

已完成：

- `im-web` 初始化为 React 18 + TypeScript + Vite + Ant Design + Zustand。
- proto 生成脚本：从 `im-proto/proto` 生成 `src/proto/generated/bundle.{js,d.ts}`。
- REST API 封装：auth、user、message、file、group。
- 鉴权状态：`accessToken` 内存保存，`refreshToken` localStorage 保存，刷新页面自动 refresh。
- WS 层：二进制 `Frame` 编解码、AUTH、PING/PONG、KICK、重连、SYNC、MSG_PUSH、MSG_SEND_ACK、READ/REVOKE/CONV 通知。
- UI：登录/注册、三栏主界面、会话列表、聊天区、文本消息发送、基础消息气泡。
- 文件上传：预签名、PUT 直传、confirm、上传进度封装。

## 1. 后端契约校准

后端当前事实来源：

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/users/me`
- `GET /api/v1/convs/{convId}/messages?end_seq=&limit=`
- `POST /api/v1/convs/{convId}/messages/{seq}/revoke`
- `POST /api/v1/files/presign`
- `POST /api/v1/files/confirm`
- `POST /api/v1/groups`
- `POST /api/v1/groups/{groupId}/members`
- `DELETE /api/v1/groups/{groupId}/members/{userId}`
- `PATCH /api/v1/groups/{groupId}`

已发现差异：

- 架构文档写的是 `/api/v1/user/me`，当前后端实际是 `/api/v1/users/me`，前端按后端实现。
- 后端当前没有文件下载或预签名 GET 接口，图片/文件历史展示需要后端补接口。
- 历史消息 REST 当前只返回文本摘要 DTO，富媒体历史回放需要后端返回 `MsgContent` 或下载信息。

## 2. 下一阶段任务

### F01 — 网关联调与文本消息闭环

状态：PENDING

目标：

- 使用真实 Rust 网关验证登录后 AUTH_ACK、SYNC_RESP、MSG_SEND、MSG_SEND_ACK、MSG_PUSH。
- 两个浏览器用户可互发文本，刷新后可通过 SYNC/历史分页恢复。

验收：

- 登录后 WS 状态为在线。
- 发送文本先乐观显示，ACK 后变为已发送并带 seq。
- 另一端实时收到消息。
- 断网重连后自动 SYNC。

### F02 — 会话创建入口

状态：PENDING

目标：

- 基于开放式单聊策略，提供按用户 ID 发起单聊入口。
- 首次单聊使用 `MsgSend.to_user_id`，ACK 后拿到 `conv_id`。

验收：

- 输入对方用户 ID 可创建/进入会话。
- 不依赖预先存在的会话列表。

### F03 — 富媒体发送

状态：PENDING

目标：

- 在现有上传链路上补 `ImageContent`、`VoiceContent`、`FileContent` 的 WS 发送。
- 图片上传缩略图和原图；语音使用 MediaRecorder；文件按 MIME 白名单提示。

验收：

- 图片消息显示本地预览，ACK 后保留消息状态。
- 语音消息可录制并发送。
- 文件消息显示文件名、大小和状态。

依赖：

- 后端补文件下载/预签名 GET 接口，或返回可访问 URL。

### F04 — 群聊管理

状态：PENDING

目标：

- 实现建群、加人、踢人、改名 UI。
- 群系统通知渲染为居中灰条。

验收：

- `POST /api/v1/groups` 后自动进入群会话。
- 成员变更后 UI 刷新群信息。

### F05 — 已读与撤回交互

状态：PENDING

目标：

- 会话激活时上报 `READ_REPORT`。
- 消息菜单支持复制、撤回。
- `REVOKE_NOTIFY` 后本地消息变成撤回状态。

验收：

- unread 角标随 read_seq 归零。
- 撤回后双方 UI 一致。

### F06 — CS Widget 独立包

状态：PENDING

目标：

- 初始化 `im-widget` Preact library mode。
- 调用 `POST /api/v1/cs/widget/sessions`，访客拿 JWT 后复用轻量 WS。

验收：

- 任意页面引入 `widget.js` 后可打开访客聊天窗口。
- 样式不污染宿主页面。

## 3. UI 约束

- 继续保持工具型界面：信息密度适中、边框和留白克制、避免营销式 hero。
- 操作按钮优先使用图标和 tooltip。
- 不把页面 section 做成大卡片；卡片只用于会话项、消息气泡、弹窗等局部元素。
- 移动端优先保证会话列表与聊天区可切换，不在小屏强塞三栏。
