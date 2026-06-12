# im-gateway-rust — WebSocket 网关（纯网络层）

职责：连接生命周期管理、token 校验（gRPC 调 user-service）、protobuf 帧编解码、
上行消息投递（gRPC 调 message-service）、下行推送（订阅 RabbitMQ push 队列 + Redis 路由表）。
**零业务逻辑。**

技术：tokio / tokio-tungstenite / tonic / prost / redis-rs
状态：骨架，待第一阶段开发。详见 ../docs/architecture.md §4
