# im-proto — 协议定义（单一事实来源）

设计说明与演进纪律见 `../docs/protocol.md`。

```
proto/
├── ws/frame.proto        WS 帧（Frame/Cmd/AUTH/KICK）      ← 网关编译
├── rpc/gateway.proto     网关↔Java 全部 RPC + PushEnvelope ← 网关编译
├── common/               enums / content(MsgContent) / error
├── body/messages.proto   业务帧 body（MsgSend/MsgPush/Sync...）
├── rpc/internal.proto    Java 模块间 RPC（User/Message/Conversation/Push）
└── events/events.proto   MQ 事件（msg.saved 等，经 Outbox）
```

要点（D19/D20）：网关只编译 ws/ + rpc/gateway.proto，业务 body 一律 bytes 透传——新增业务帧 Rust 零改动。
生成：Rust(prost/tonic-build)、Java(protobuf-maven-plugin)；generate.sh 待建。改 proto 必须两端编译通过。
