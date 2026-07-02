# Roadmap

## P0：工程骨架

- 四端 Flutter 平台目录。
- Riverpod + go_router + drift + WebSocket + Protobuf。
- 本地 schema 覆盖用户、群、会话、消息、附件、同步游标、Outbox。
- 发送、接收、ACK、重连、同步的代码边界清晰。

## P1：协议联调

- 对齐 `im-rust` 网关 AUTH、PING/PONG、KICK、MSG_SEND、MSG_PUSH、SYNC_REQ。
- 对齐 `im-server` REST 登录、刷新 token、历史消息、文件预签名。
- 增加 socket 集成测试和本地库事务测试。

## P2：业务补齐

- 好友、黑名单、群资料、群成员、已读回执。
- 图片、语音、文件上传直传 MinIO。
- 消息撤回、失败重试、草稿、置顶、免打扰完整交互。

## P3：多端体验

- 多端登录状态展示。
- 多端已读同步。
- 桌面快捷键、窗口状态、系统通知。
- 移动端相册、麦克风、通知权限。

## P4：发布与运维

- Shorebird 移动端补丁流程。
- 桌面端更新清单、安装包签名和下载引导。
- Remote Config 灰度发布。
- 连接质量、同步耗时、Outbox 堆积、ACK 延迟指标。

