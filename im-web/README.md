# im-web

React 18 + TypeScript + Vite 的 Web IM 客户端，实现主 App 的登录、会话列表、聊天窗口、WebSocket/protobuf 接入骨架和文件预签名上传封装。

## 本地启动

```bash
cd im-web
npm install
npm run proto:gen
npm run dev
```

默认开发代理：

- REST：`/api` -> `http://localhost:8081`
- WS：`/ws` -> `ws://localhost:9090`
- 租户：`VITE_TENANT_ID=1`

可用环境变量覆盖：

```bash
VITE_API_BASE_URL=http://localhost:8081
VITE_WS_URL=ws://localhost:9090/ws
VITE_TENANT_ID=1
VITE_APP_VERSION=0.1.0-web
```

## 验证

```bash
npm run typecheck
npm run build
```

`npm run build` 会先执行 `proto:gen`，从 `../im-proto/proto` 生成前端 protobuf runtime。

## 当前范围

- 已接入 `/api/v1/auth/register`、`/api/v1/auth/login`、`/api/v1/auth/refresh`、`/api/v1/users/me`。
- 已封装消息历史 `/api/v1/convs/{convId}/messages` 和撤回接口。
- 已封装文件 `/api/v1/files/presign`、`/api/v1/files/confirm` 和 MinIO 直传。
- 已实现二进制 WS Frame、AUTH、PING/PONG、KICK、SYNC、MSG_PUSH、MSG_SEND_ACK、READ/REVOKE/CONV 通知处理。
- UI 已完成登录/注册、三栏布局、会话列表、聊天区、文本发送和基础消息气泡。

## 已知联调项

- 后端当前没有文件下载/预签名 GET Controller，历史图片只能先展示占位。
- 富媒体发送的 WS `MsgContent` 已有类型基础，输入栏目前只完成上传链路，发送图片/语音/文件消息需要继续接协议构造。
- 会话列表完全依赖 WS `SYNC_RESP`，如果网关尚未运行，登录后会显示空列表。
