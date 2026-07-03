# im-app —— 微光 Lumo 多端客户端（Flutter）

> Android / iOS / macOS / Windows 四端一套代码。负责前端 UI、消息展示、本地缓存、网络连接。
> 与 `im-gateway-rust`（网关）、`im-server`（Java 业务）配合，协议事实来源是 `../im-proto`。

本目录是**架构脚手架**：把底层打通（连接层 / 本地缓存 / 消息框架 / 热更新 / 自适应 UI），
业务细节（好友、群管理、客服、富媒体上传等）按 `docs/` 决策分阶段填充。

---

## 1. 已搭好的能力

| 层 | 内容 | 关键文件 |
|---|---|---|
| 协议 | 帧编解码、与 im-proto 对齐（透传式 cmd+bytes） | `lib/core/proto/codec.dart` |
| **消息核心** | WS 连接状态机、指数退避重连、心跳/半死链探测、AUTH 刷新、pending 补发、帧分发、seq 对齐增量同步 | `lib/data/remote/ws/*`、`lib/data/im_engine.dart` |
| 本地缓存 | drift（SQLite）四端落库，UI 响应式订阅，离线可读 | `lib/data/local/*` |
| 网络 REST | dio + Bearer/X-Tenant-Id 注入、401 单飞刷新重放 | `lib/data/remote/rest/*` |
| 状态管理 | Riverpod 全量装配 | `lib/app/providers.dart` |
| 热更新 | Shorebird（移动端 Dart 热更）+ 桌面端版本清单 OTA | `lib/core/update/*`、`shorebird.yaml` |
| UI | Lumo 设计语言主题；移动底 Tab / 桌面三栏自适应；登录 / 会话列表 / 聊天 | `lib/features/*`、`lib/core/theme/*` |

设计语言取自《微光 Lumo》：主色 `#5A54F0`、浅紫 `#ECEBFE`、墨 `#16171D`、成功绿 `#21C16B`、警示红 `#FF4D5E`，字体 Plus Jakarta Sans + 中文系统字体。

---

## 2. 架构分层

```
┌─────────────────────────── UI (features/) ───────────────────────────┐
│  自适应外壳 HomeShell（移动底Tab / 桌面三栏）                          │
│  登录 · 会话列表 · 聊天（消息列表 / 气泡 / 输入栏）· 通讯录 · 我        │
└───────────────▲───────────────────────────────────▲──────────────────┘
                │ watch（Stream）                     │ 动作（send/markRead）
┌───────────────┴───────────────────────────────────┴──────────────────┐
│                     Riverpod providers (app/providers.dart)            │
└───────────────▲───────────────────────────────────▲──────────────────┘
                │                                     │
        ┌───────┴────────┐                   ┌────────┴─────────┐
        │  本地缓存 drift │◀── 写入（落库）──│   ImEngine        │
        │  conversations │                   │  帧分发 / 发送    │
        │  messages      │── 响应式读 ──────▶│  seq 对齐 / 同步  │
        └────────────────┘                   └───▲──────────▲────┘
                                                 │          │
                                       ┌─────────┴───┐  ┌───┴─────────┐
                                       │  ImSocket    │  │  REST(dio)  │
                                       │  WS 状态机    │  │  历史/鉴权   │
                                       └──────▲───────┘  └─────────────┘
                                              │ wss + protobuf 帧
                                   im-gateway-rust ──gRPC/MQ── im-server
```

**核心理念**：网络层只把数据写进本地 DB，UI 只订阅 DB 的 Stream。
于是「离线可读、重连即自动刷新、多端同步免费」——这也是 OpenIM/微信式收件箱模型在客户端的落地。

### 消息收发链路（最核心）

- **发送**：`ImEngine.sendText` → 乐观消息先以 `pending` 落库 + 入 Outbox → `ImSocket` 发 `MSG_SEND`
  → 写出成功后置 `sending` → 收 `MSG_SEND_ACK` 回填 `server_msg_id/seq`、状态置 `sent`、会话 `read_seq` 同步前进（避免假未读）。
  连接未就绪不丢：保持 `pending`，重连 `AUTH_ACK` 后 drain Outbox 补发；重试过期/次数耗尽标记 `failed`，支持手动重试。
- **接收**：`MSG_PUSH` → 落库（按 `client_msg_id` 去重，自回显/多端天然幂等）→ 回 `MSG_RECV_ACK`（带网关分配的 `req_id`，D28）
  → 若 `seq` 与本地 `syncSeq` 出现缺口，触发 `SYNC_REQ` 增量补齐。
- **离线同步**：上线/重连必发 `SYNC_REQ`（带每会话 `local_max_seq` + `conv_list_version`），
  服务端回 `[local+1, server_max]`；`syncSeq` 维护「已连续同步到」的水位（核心约定 4），更早缺口走 REST 历史分页。
- **可靠性**：心跳每 N 秒；2.5 个周期无下行帧判半死链 → 主动断开重连补齐（对齐 protocol.md §3，不做服务端重推）。

---

## 3. 首次构建

> Flutter ≥ 3.24，Dart ≥ 3.4。

```bash
# 一次性：装 protoc 插件，确保 ~/.pub-cache/bin 在 PATH
dart pub global activate protoc_plugin

# 一键：生成四端平台目录(android/ios/macos/windows) + proto + 依赖 + 代码生成
make bootstrap        # = bash tool/bootstrap.sh

# 或分步：
#   flutter create --org com.lumo --project-name im_app \
#       --platforms=android,ios,macos,windows .
#   bash tool/generate_proto.sh        # 从 ../im-proto 生成 Dart 绑定
#   flutter pub get
#   dart run build_runner build --delete-conflicting-outputs
```

> 平台配置已预置（`flutter create` 会保留它们）：Android 联网/明文白名单、
> macOS 出网 entitlement、iOS ATS（见各平台 `SETUP.md`）。架构与消息流程详见
> [`docs/APP_ARCHITECTURE.md`](docs/APP_ARCHITECTURE.md) 和
> [`docs/MESSAGE_FLOW.md`](docs/MESSAGE_FLOW.md)。

> 生成前 `lib/core/proto/generated/` 为空，`codec.dart` 与依赖它的文件会报红——属正常，跑完第 1 步即消失（与 im-web 必须先 `proto:gen` 一致）。

运行：

```bash
flutter run -d macos      # 或 windows / <android设备> / <ios设备>
# 注入后端地址（不传则默认对齐 im-web 开发代理目标）
# 默认 REST: http://103.45.65.84:8081
# 默认 WS:   ws://103.45.65.84:8082/ws
flutter run -d android --dart-define=API_BASE_URL=https://im.example.com \
  --dart-define=WS_URL=wss://im.example.com/ws --dart-define=TENANT_ID=1
```

---

## 4. 四端平台配置（关键，务必做）

- **macOS · 出网/附件权限（最常见坑）**：沙箱默认禁止外联，也禁止读取用户选择的容器外文件。必须在
  `macos/Runner/DebugProfile.entitlements` 与 `Release.entitlements` 同时加：
  ```xml
  <key>com.apple.security.network.client</key><true/>
  <key>com.apple.security.files.user-selected.read-only</key><true/>
  ```
  否则 WebSocket / REST 会连不上，聊天框选择图片/文件后也无法读取内容并上传。
- **Android · 联网**：`android/app/src/main/AndroidManifest.xml` 加
  `<uses-permission android:name="android.permission.INTERNET"/>`；
  开发若用明文 `ws://`（如 10.0.2.2），再加 `android:usesCleartextTraffic="true"` 或网络安全配置。
- **iOS · ATS / 权限**：生产用 `wss://` 无需配置；开发若用明文 `ws://`，在 `ios/Runner/Info.plist`
  添加 `NSAppTransportSecurity` 例外。聊天语音/语音通话需要 `NSMicrophoneUsageDescription`；
  视频通话需要 `NSCameraUsageDescription`；图片按需加 `NSPhotoLibraryUsageDescription`。
- **Windows**：开箱即用，无额外权限。打 MSIX 时勾选 `internetClient` 能力。

---

## 5. 热更新

策略（决策见会话）：**Shorebird code push 管移动端 Dart 热更，桌面端走配置 OTA**。

- **Android / iOS（Shorebird）**：
  ```bash
  dart pub global activate shorebird_cli   # 或官方安装脚本
  shorebird login && shorebird init        # 回填 shorebird.yaml 的 app_id
  shorebird release android                # 发版（生成基线）
  shorebird patch android                  # 发补丁（用户下次冷启动生效）
  ```
  `lib/core/update/update_service.dart` 启动时检查并静默下载补丁；`shorebird.yaml: auto_update` 亦会自动拉。
- **Windows / macOS（Shorebird 不支持热更 Dart）**：应用内拉取版本清单 JSON，比较 `build` 号，
  发现新版引导下载新安装包。配置清单地址：
  ```bash
  flutter run -d macos --dart-define=DESKTOP_UPDATE_URL=https://dl.example.com/lumo/latest.json
  ```
  清单格式：
  ```json
  { "version": "0.2.0", "build": 5,
    "platforms": { "windows": {"url": "https://.../Lumo-Setup.exe"},
                   "macos":   {"url": "https://.../Lumo.dmg"} },
    "notes": "修复若干问题" }
  ```
  桌面端结构已预留（`DesktopUpdateAvailable`），下载/安装的引导按需在「我」页接 `url_launcher`。

> 注：原生层（Rust FFI、Android/iOS 原生插件、桌面可执行）的改动不在 Shorebird 热更范围，需整包发版。

---

## 6. 目录结构

```
lib/
├── main.dart                      # 入口
├── bootstrap.dart                 # 启动初始化
├── app/
│   ├── app.dart                   # MaterialApp.router + Lumo 主题
│   ├── router.dart                # go_router + 鉴权重定向
│   ├── router/                    # 路由门面
│   ├── theme/                     # 主题门面
│   ├── lifecycle/                 # 生命周期扩展点
│   ├── providers.dart             # Riverpod 全量装配 + AuthController
│   └── update_providers.dart      # 热更新 provider
├── core/
│   ├── config/env.dart            # 租户/WS/REST 地址 + dart-define
│   ├── network/                   # WS / 协议门面
│   ├── platform/platform_info.dart# 平台/平台类探测（对齐 proto Platform）
│   ├── proto/codec.dart           # 帧编解码（导入 generated/）
│   ├── storage/                   # secure storage / preferences 边界
│   ├── theme/                     # Lumo 色板 + ThemeData + 气泡 ThemeExtension
│   ├── update/update_service.dart # Shorebird + 桌面 OTA
│   └── utils/                     # id / seq 对齐 / uuid / time
├── data/
│   ├── models/                    # Conversation / ChatMessage / MessageContent(sealed) ...
│   ├── local/                     # drift：tables / daos / 连接 / 行↔模型
│   │   └── db/                    # 推荐目录门面（app_database / tables / daos）
│   ├── remote/
│   │   ├── rest/                  # dio client / auth / message / file
│   │   └── ws/                    # ★ im_socket / reconnect / ws_mappers / ws_channel
│   └── im_engine.dart             # ★ 帧分发 + 发送 + 同步 协调者
├── domain/                        # entities / repositories / usecases
└── features/
    ├── splash · auth · home · conversations · chat · contacts · profile · settings
```

---

## 7. 与后端/协议的对齐

- 协议唯一事实来源是 `../im-proto`；客户端编译集合与 im-web 一致（`ws/frame`、`body/messages`、`common/{content,enums,error}`）。
- 改 proto 后：重跑 `tool/generate_proto.sh`，并保证 **Rust / Java / Dart 同时编译通过**（核心约定 5）。
- 行为对齐成熟参考实现 `../im-web`（zustand store ↔ 这里的 drift+Riverpod；`ImSocket.ts` ↔ `im_socket.dart`；`handlers.ts` ↔ `im_engine.dart`）。
- REST 契约见 `docs/protocol.md §5`；DTO 解析对字段名保持宽容，按 im-server 实际返回微调 `lib/data/remote/rest/dto.dart`。

## 8. 二阶段待接（已留结构）

富媒体上传直传（image_picker/file_picker→presign→MinIO，`sendImage/File/Voice` 已就绪）、
好友/群管理 UI（D40/D42）、客服坐席工作台（D30-D39）、本地推送通知、消息搜索（SQLite FTS）。
