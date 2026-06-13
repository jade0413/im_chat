# 后端能力现状评估报告

> 评估日期：2026-06-13 | 审查人：Claude
> 依据：TASKS.md T00~T31、proto 文件、Java/Rust 源码扫描

---

## 1. 任务完成度概览

| 范围 | 任务 | 状态 |
|------|------|------|
| 工程基础 | T00~T05 | ✅ 全部 DONE |
| 用户鉴权 | T06、T07 | ✅ DONE |
| 消息核心 | T08~T12 | ✅ DONE |
| 集成测试 | T13 | ✅ DONE |
| Bug 修复 | T14~T16、T19、T21~T24 | ✅ DONE |
| 撤回 | T17 | ✅ DONE |
| 多端同步 | T18 | ✅ DONE |
| Rust 网关 | T20~T24 | ✅ DONE |
| 已读回执 | T25 | ✅ DONE |
| 群聊 | T26 | ✅ DONE |
| 会话列表 diff | T27 | ✅ DONE |
| 文件上传 | T28 | ✅ DONE |
| 内容安全 | T29 | ✅ DONE |
| **CS 客服** | **T30~T31** | ✅ DONE（骨架+访客接入） |
| **CS 客服** | **T32~T36** | ❌ PENDING |

---

## 2. Rust 网关稳定性评估

### ✅ 已具备的能力

| 能力 | 实现 |
|------|------|
| WS 连接生命周期 | `connection.rs`：AUTH 握手 → 路由注册 → 心跳检测 → 优雅断连 |
| 心跳检测 | 服务端下发 `heartbeat_interval_sec`，3 倍 idle_timeout 触发断连 |
| Push ACK 超时 | 推送后等 `ack_timeout`（默认 10s），超时主动断连；客户端重连走 SYNC 补齐 |
| 握手限流 | `HandshakeLimiter`：令牌桶限速，防 DDoS 连接风暴 |
| 防重放 | `AuthReq.timestamp` 时间戳验证，|now-ts| > 5min 拒绝 |
| 慢消费者背压 | T23 已处理（slow consumer backoff） |
| 路由表维护 | in-process gRPC ConnEvent → Java push-service 维护 Redis 路由 |
| 指标监控 | `metrics.rs`：ack_timeout 断连计数等 |
| Prometheus | metrics endpoint 暴露 |

### ⚠️ 已知限制（可接受的 MVP 权衡）

| 问题 | 现状 | 风险 |
|------|------|------|
| 重连无指数退避 | 客户端自行实现（服务端无法控制） | 前端需实现退避重连逻辑 |
| 无 WebRTC 信令 | 不在协议范围内 | 视频通话不支持（见 §4） |
| 单网关实例 | MVP 单机；路由表 Redis 支持水平扩展 | 有状态扩缩需关注 |
| ACK 超时仅断连不重推 | 协议 §3 明确设计：超时断连+客户端 SYNC | 正常；前端必须实现重连+SYNC |

### 结论：Rust 网关 MVP 级别稳定，无阻塞性问题。

---

## 3. IM 核心功能完整度

### 3.1 消息类型支持

| 消息类型 | Proto | 上传 | 发送 | 预览/播放 | 备注 |
|---------|-------|------|------|----------|------|
| 文字 | ✅ `TextContent` | — | ✅ | ✅ | 支持 @提及 |
| 图片 | ✅ `ImageContent` | ✅ MinIO 预签名 | ✅ | 前端实现 | `thumb_key` 由客户端上传缩略图 |
| 语音 | ✅ `VoiceContent` | ✅ MinIO | ✅ | 前端实现 | aac/opus/ogg/wav/mp4 |
| 文件 | ✅ `FileContent` | ✅ MinIO | ✅ | 前端下载 | PDF/ZIP/octet-stream/text |
| **视频** | ❌ **无 VideoContent** | ❌ 无 video/* MIME | — | — | **⚠️ 缺口，见 §4** |
| 系统通知 | ✅ `NotificationContent` | — | 系统写入 | 灰条渲染 | 建群/加人/撤回等 |
| 自定义 | ✅ `CustomContent` | — | ✅ | 租户自定义 | JSON payload |

### 3.2 会话能力

| 功能 | 状态 | 说明 |
|------|------|------|
| C2C 私聊 | ✅ | 幂等建会话，seq 对齐 |
| 群聊（≤500 人） | ✅ | 建群/加人/踢人/改名 |
| 消息同步（增量）| ✅ | `SYNC_REQ` + `conv_list_version` diff |
| 已读回执 | ✅ | `READ_REPORT` → `READ_NOTIFY` 推对端+自己其他端 |
| 消息撤回 | ✅ | 2 分钟窗口，审核撤回无限制 |
| 多端同步 | ✅ | Mobile/Desktop/Web 各 1 台，新登踢旧 |
| 内容安全 | ✅ | 先发后审，命中敏感词自动撤回 |
| 消息历史分页 | ✅ | REST 懒加载 |
| 黑名单 | ✅（D17 决策） | 接口待验证 |
| 好友申请流程 | ❌ Open Question | 当前开放单聊，二阶段做好友制 |
| 消息引用/回复 | ❌ Open Question E3 | — |
| Reactions | ❌ Open Question E4 | — |
| Typing 指示器 | ❌ Open Question E2 | — |
| @提及推送 | ⚠️ 部分 | proto 有 `at_user_ids`，但 @ 扩散写推送未实现 |
| 置顶/免打扰 | ✅ proto 预留 | `ConvInfo.pinned/muted`，REST 操作未验证 |

### 3.3 文件上传能力

| 限制 | 默认值 | 配置项 |
|------|--------|--------|
| 图片最大 | `im.file.size-limit.image-bytes`（默认 20MB） | 可配 |
| 语音最大 | `im.file.size-limit.voice-bytes`（默认 10MB） | 可配 |
| 文件最大 | `im.file.size-limit.file-bytes`（默认 100MB） | 可配 |
| 允许 MIME | 见下 | 可配 |

允许的 MIME 类型（当前默认）：
- 图片：`image/jpeg`, `image/png`, `image/webp`, `image/gif`
- 语音：`audio/aac`, `audio/mpeg`, `audio/ogg`, `audio/opus`, `audio/wav`, `audio/mp4`
- 文件：`application/pdf`, `application/zip`, `application/octet-stream`, `text/plain`

---

## 4. 缺口与建议修复

### G1 — ⚠️ 视频消息（高优先级，前端需要）

**现状**：`content.proto` 无 `VideoContent`；`FileProperties` 无 `video/*` MIME。

**解决方案**（推荐 A，成本最低）：

**方案 A**：用 `FileContent` 发视频（改 MIME 白名单即可）
```yaml
# application.yml
im.file.allowed-mimes:
  - video/mp4
  - video/webm
  - video/quicktime
im.file.size-limit.file-bytes: 200MB   # 视频需要更大上限
```
前端发视频用 `FileContent`，`file_name` 带扩展名，前端识别后用 `<video>` 标签播放。

**方案 B**：加 `VideoContent` 到 proto（干净，但需要 proto 编译+各端更新）
```protobuf
message VideoContent {
  string object_key  = 1;
  string thumb_key   = 2;  // 封面帧（客户端截帧后上传）
  uint32 duration_ms = 3;
  uint32 width       = 4;
  uint32 height      = 5;
  uint64 size        = 6;
  string mime        = 7;
}
```

**建议**：MVP 先用方案 A（1 行配置），等前端稳定后再做方案 B。

---

### G2 — ⚠️ 图片缩略图（中优先级）

**现状**：`ImageContent.thumb_key` 由客户端上传。服务端无自动生成缩略图。

**影响**：前端上传图片时需要：
1. 本地压缩生成缩略图（canvas）
2. 先上传缩略图获得 `thumb_key`
3. 再上传原图获得 `object_key`
4. 发送 `ImageContent{ object_key, thumb_key, width, height, size, mime }`

如果客户端不传 `thumb_key`，消息列表直接用 `object_key` 加载原图（影响性能）。

**建议**：前端实现客户端压缩缩略图（canvas API）；后期可加服务端转码。

---

### G3 — ⚠️ 离线推送通知（中优先级）

**现状**：无 APNs/FCM/厂商通道。App 后台时收不到消息通知。

**影响**：Web 端影响较小（可用浏览器 Notification API）；Flutter App 后台时消息无感知。

**建议**：前端先做浏览器 Notification；Flutter 端接入 FCM 在第二阶段做。

---

### G4 — 低优先 / 二阶段

| 缺口 | 说明 |
|------|------|
| 好友申请流程 | 当前开放单聊；二阶段加好友申请 + `friend_required` 租户开关 |
| @ 提及推送 | proto 有字段，后端推送未做写扩散；二阶段 |
| Typing 指示器 | 协议预留 E2；二阶段 |
| 消息引用/回复 | 协议预留 E3；二阶段 |
| 消息搜索 | 二阶段（ES 或客户端 SQLite FTS） |
| 用户在线状态 | 只有 CS 坐席有 `agent_status`；普通用户在线状态二阶段 |

---

## 5. 后端 PENDING 任务（CS 系统 T32~T36）

| 任务 | 内容 | 前端影响 |
|------|------|---------|
| T32 | CS 会话状态机 + assign/resolve API | 坐席端 |
| T33 | 坐席 inbox + CS 消息推送路由 | 坐席端 |
| T34 | 坐席在线状态 | Widget 显示在线/离线 |
| T35 | 坐席上线通知 | 坐席端 |
| T36 | Widget 配置接口 | Widget 初始化 |

**结论**：CS 系统对主 IM App 前端无阻塞，前端可先做 IM 主界面；CS Widget 是独立轻量组件，可并行开发。

---

## 6. 总结

**可以立即开始前端开发的功能（后端已完整）**：
- 用户注册/登录/JWT
- C2C 私聊（文字、图片、语音、文件）
- 群聊（建群、发消息、管理成员）
- 消息同步（在线推送 + 离线 SYNC）
- 已读回执
- 消息撤回
- 文件上传（MinIO 预签名直传）

**前端开发前需要配置（否则会报错）**：
1. 在 `application.yml` 加 `video/mp4`, `video/webm` 到 MIME 白名单（G1 方案 A）
2. 调大文件上传限制至 200MB
3. 确认 MinIO 已启动并 bucket 已创建
