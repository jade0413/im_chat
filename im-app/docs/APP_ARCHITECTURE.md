# im-app 架构说明

## 定位

`im-app` 是 Flutter 多端客户端，目标平台为 Android、iOS、macOS、Windows。第一阶段不是做一次性 UI demo，而是建立可长期演进的客户端底座：连接层、本地缓存、消息同步、发送队列、协议编解码、热更新预留和多端适配。

## 分层

- `app/`：应用根、路由、主题、生命周期。`LumoApp` 监听前后台切换，回前台时触发连接探活。
- `core/`：环境配置、日志、平台识别、协议门面、网络连接门面、存储和更新策略。
- `data/`：本地 drift 数据源、REST/WS 远端数据源、Repository 实现和 `ImEngine`。
- `domain/`：核心实体、Repository 契约和用例边界，作为后续业务下沉的稳定入口。
- `features/`：登录、会话、聊天、联系人、设置等 UI 骨架。UI 只通过 Riverpod Provider/Repository 读写状态。
- `shared/`：跨 feature 复用组件和通用模型。

## 核心原则

- WebSocket 不进入 UI 页面，统一由 `ImSocket` 管连接生命周期，由 `ImEngine` 管消息分发。
- UI 不直接读写 drift DAO，只订阅 Repository 暴露的 Stream。
- 网络下行先落本地库，UI 由本地库响应式刷新，保证离线可读和重连补齐。
- 消息可靠性以 `clientMsgId + serverMsgId + conversationId + sequence + status` 为最小状态闭环。
- `syncSeq` 表示本地连续同步水位，不能用服务端 `maxSeq` 替代。

## 当前入口

- 启动：`lib/bootstrap.dart` 初始化 Flutter binding 和日志。
- 路由：`lib/app/router.dart` 使用 `go_router` 和鉴权重定向。
- 状态装配：`lib/app/providers.dart` 装配 database、DAO、API、engine、repository。
- 消息核心：`lib/data/im_engine.dart`。
- WebSocket 状态机：`lib/data/remote/ws/im_socket.dart`。

