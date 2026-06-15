# im-gateway-rust — WebSocket 网关（纯网络层）

职责：连接生命周期管理、token 校验（gRPC 调 user-service）、protobuf 帧编解码、
上行业务帧透传（gRPC 调 Uplink.Dispatch）、下行推送（订阅 RabbitMQ `push.gw.{instance}` 队列）。
**零业务逻辑。**

技术：tokio / axum ws / tonic / prost / lapin

## 本地启动

前置：

- im-server gRPC：默认 `http://127.0.0.1:9091`
- RabbitMQ：默认 `amqp://im:im_dev_mq_pwd@127.0.0.1:5672/%2f`
- Redis 路由表由 im-server push 模块维护，网关 MVP 不直连 Redis。

```bash
cargo run
```

常用环境变量：

| 变量 | 默认值 | 说明 |
|---|---:|---|
| `GW_INSTANCE_ID` / `IM_GATEWAY_INSTANCE_ID` | `gw-local` | 网关实例 ID，对应 RabbitMQ 队列 `push.gw.{instance}` |
| `GW_WS_BIND` / `IM_GATEWAY_WS_BIND` | `0.0.0.0:8080` | WebSocket 监听地址 |
| `UPSTREAM_GRPC` / `IM_GATEWAY_UPSTREAM_GRPC` | `http://127.0.0.1:9091` | Java im-server gRPC 地址 |
| `RABBITMQ_URL` / `IM_GATEWAY_RABBITMQ_URL` | `amqp://im:im_dev_mq_pwd@127.0.0.1:5672/%2f` | RabbitMQ 连接串 |
| `IM_GATEWAY_ALLOWED_ORIGINS` | `*` | WebSocket Origin 白名单，逗号分隔；生产必须配置为实际 Web 域名 |
| `IM_GATEWAY_HANDSHAKE_RATE_LIMIT_PER_SEC` | `200` | 实例级 WS 握手令牌桶每秒补充速率 |
| `IM_GATEWAY_HANDSHAKE_RATE_LIMIT_BURST` | `400` | 实例级 WS 握手令牌桶突发容量 |
| `IM_GATEWAY_PER_IP_HANDSHAKE_RATE_LIMIT_PER_SEC` | `20` | 单 IP WS 握手令牌桶每秒补充速率 |
| `IM_GATEWAY_PER_IP_HANDSHAKE_RATE_LIMIT_BURST` | `40` | 单 IP WS 握手令牌桶突发容量 |
| `IM_GATEWAY_PER_IP_HANDSHAKE_LIMITER_IDLE_TTL_SEC` | `600` | 单 IP 限流桶空闲清理时间 |
| `IM_GATEWAY_PUSH_ACK_TIMEOUT_SEC` | `10` | `need_ack` 下行帧 ack 超时秒数 |
| `IM_GATEWAY_DISPATCH_TIMEOUT_SEC` | `10` | 上行业务帧 gRPC deadline |
| `IM_GATEWAY_VERIFY_TIMEOUT_SEC` | `5` | 鉴权 gRPC deadline |
| `IM_GATEWAY_OUTBOUND_QUEUE_SIZE` | `256` | 单连接下行有界队列大小，满则按慢消费者断连 |
| `IM_GATEWAY_OUTBOUND_QUEUE_FULL_THRESHOLD` | `3` | 单连接下行队列连续满 N 次后主动断连 |
| `IM_GATEWAY_ROUTE_RENEW_HEARTBEATS` | `3` | 每 N 次 PING 续一次路由 TTL |
| `IM_GATEWAY_DRAIN_TIMEOUT_SEC` | `10` | 收到退出信号后进入 drain、关闭现有连接并等待的秒数 |
| `IM_GATEWAY_RABBITMQ_PREFETCH_COUNT` | `256` | RabbitMQ 单 consumer 未 ack 预取上限，防止网关慢时无限拉消息 |
| `IM_GATEWAY_MAX_FRAME_BYTES` | `65536` | 单个 WS protobuf frame 最大字节数 |
| `IM_GATEWAY_MIN_PROTOCOL_VERSION` | `1` | 最低兼容协议版本，过低直接 KICK |

入口：

- `GET /health`
- `GET /ready`
- `GET /metrics`
- `GET /ws`

## 网关能力回归状态

| 能力 | 状态 | 说明 |
|---|---|---|
| WebSocket 长连接接入 | 已实现 | `GET /ws`，1 个 Binary Message = 1 个 protobuf `Frame` |
| token 鉴权 | 已实现 | 首帧必须 `AUTH`，gRPC 调 `GatewayAuth.VerifyToken` |
| uid/deviceId/tenant/platform 绑定 | 已实现 | 鉴权成功后固化为 `ConnCtx` |
| 心跳与超时清理 | 已实现 | `PING/PONG`，服务端按 `heartbeat * 3` idle timeout 清理 |
| uid -> gatewayId -> connId 路由 | 已实现 | Java `ConnEvent.OnConnected` 写 Redis，网关心跳走 `RefreshRoute` 续 TTL |
| 上行透传 | 已实现 | `Uplink.Dispatch(cmd, bytes)` |
| 下行推送 | 已实现 | 消费 `push.gw.{instance}`，查本地 ConnMap 写 WS |
| 下行 ACK | 已实现 | `need_ack` 时网关分配 `req_id`，超时主动断连 |
| 每连接发送队列限制 | 已实现 | 有界 mpsc 队列，连续满阈值后断开 |
| 慢消费者处理 | 已实现 | 下行队列满或推送失败时关闭连接并上报断线 |
| 最大包大小限制 | 已实现 | `IM_GATEWAY_MAX_FRAME_BYTES` |
| per-IP 建连限流 | 已实现 | 单 IP token bucket |
| 实例级建连限流 | 已实现 | 实例总 token bucket |
| readiness/liveness | 已实现 | `/health` 活性；`/ready` drain 时返回 503 |
| 优雅停机 drain | 已实现基础版 | 收到 SIGINT/SIGTERM 后标记 draining、拒绝新 WS、关闭已有连接并等待 drain 窗口 |
| 基础 metrics | 已实现 | 在线连接、pending ack、push 成功/失败、握手拒绝、Dispatch 成功/失败/耗时总量 |
| 多节点部署 | 已支持 | `GW_INSTANCE_ID` 唯一，RabbitMQ 队列按实例隔离 |
| 节点故障路由清理 | 已支持 | 正常断线上报；异常崩溃由 Redis route TTL 兜底 |
| gRPC 超时 | 已实现 | verify/dispatch/conn_event 均有 timeout |
| gRPC 重试/熔断 | 未在网关实现 | Dispatch 重试可能放大非幂等请求，熔断后续按压测结果再加 |
| per-UID 发消息限流 | Java 侧处理 | 属于业务入口限流，不放 Rust 网关 |
| clientMsgId 去重 | Java 侧处理 | 属于消息幂等，不放 Rust 网关 |
| 离线消息同步 | Java/客户端处理 | 网关只透传 `SYNC_REQ/SYNC_RESP` |

## 工程级上线检查

- 生产必须配置 `IM_GATEWAY_ALLOWED_ORIGINS` 为真实 Web 域名，不要使用默认 `*`。
- `GW_INSTANCE_ID` 必须全局唯一；Java push 模块按该 ID 投递到 `push.gw.{instance}`。
- LB/Nginx 使用 `/ready` 做摘流判断；进程退出时先进入 drain，再停止服务。
- 监控至少采集 `/metrics` 中的在线连接数、pending ack、push failed、ack timeout disconnect、slow consumer disconnect、握手拒绝和 Dispatch 失败率。
- 网关进程前面应放 LB/Nginx 并开启 TLS；网关自身保持纯 WS 和 gRPC 内网通信。
