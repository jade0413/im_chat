# macOS 平台配置（已就绪）

沙箱默认禁止外联，也默认禁止读取 App 容器外文件。必须开启「出网」和「用户选择文件读取」
entitlement，否则 WebSocket / REST 会连不上，聊天框选择图片/文件后也无法读取内容并上传。
本项目两个 entitlements 均已包含 `com.apple.security.network.client` 与
`com.apple.security.files.user-selected.read-only`：

- `Runner/DebugProfile.entitlements`：app-sandbox + allow-jit + network.server + **network.client** + **user-selected.read-only**
- `Runner/Release.entitlements`：app-sandbox + **network.client** + **user-selected.read-only**

无需额外操作。直接 `flutter run -d macos`。
