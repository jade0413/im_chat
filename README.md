# im-project — 多租户 IM / 客服系统

> 详细设计见 `docs/architecture.md`，项目约定与决策日志见 `CLAUDE.md`。

## 目录结构

```
im-project
├── im-gateway-rust          # Rust WebSocket 网关（纯网络层：连接/鉴权转发/编解码/路由）
├── im-proto                 # Protobuf 协议定义（WS 帧 + gRPC service，单一事实来源）
├── im-server                # Java 21 + Spring Boot 3.2 业务侧（Maven 多模块，模块化单体）
│   ├── im-common            # 公共：tenant 上下文、错误码、ID 生成、MQ/Redis 封装
│   ├── im-user-service      # 用户/认证/租户
│   ├── im-message-service   # 消息收发、seq、落库、离线消息
│   ├── im-conversation-service  # 会话列表、未读数、已读回执
│   ├── im-group-service     # 群组（第一阶段含基础群聊）
│   ├── im-file-service      # 文件/图片/语音（MinIO 预签名直传）
│   ├── im-push-service      # 在线推送路由 + 离线第三方推送
│   └── im-bootstrap         # 启动模块：MVP 把上述模块合并为 1 个进程部署
├── im-admin                 # 管理后台后端（租户管理、客服坐席管理）
├── im-web                   # Web 前端（IM + 客服工作台）
├── im-app                   # App（uni-app / Flutter，待定）
├── deploy
│   ├── docker-compose       # MySQL / Redis / RabbitMQ / MinIO / 服务编排
│   └── k8s                  # 第二阶段
└── docs
    ├── architecture.md      # ★ 详细架构设计文档
    └── research             # OpenIM / Tinode / Matrix 调研笔记
```

## 阶段

- **第一阶段 MVP**：登录、WS 长连接、单聊、群聊、离线消息、会话列表、已读回执、图片/语音消息
- **第二阶段**：参考 OpenIM 补全（多端同步、撤回、扩展字段、SDK 化），实现多租户客服（访客接入、坐席分配、转人工）
