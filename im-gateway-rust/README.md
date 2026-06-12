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
| `IM_GATEWAY_PUSH_ACK_TIMEOUT_SEC` | `10` | `need_ack` 下行帧 ack 超时秒数 |
| `IM_GATEWAY_DISPATCH_TIMEOUT_SEC` | `10` | 上行业务帧 gRPC deadline |
| `IM_GATEWAY_VERIFY_TIMEOUT_SEC` | `5` | 鉴权 gRPC deadline |
| `IM_GATEWAY_OUTBOUND_QUEUE_SIZE` | `256` | 单连接下行有界队列大小，满则按慢消费者断连 |
| `IM_GATEWAY_OUTBOUND_QUEUE_FULL_THRESHOLD` | `3` | 单连接下行队列连续满 N 次后主动断连 |
| `IM_GATEWAY_ROUTE_RENEW_HEARTBEATS` | `3` | 每 N 次 PING 续一次路由 TTL |

入口：

- `GET /health`
- `GET /metrics`
- `GET /ws`
