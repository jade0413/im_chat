# macOS 平台配置（已就绪）

沙箱默认禁止外联，必须开启「出网」entitlement，否则 WebSocket / REST 全部连不上。
本项目两个 entitlements 均已包含 `com.apple.security.network.client`：

- `Runner/DebugProfile.entitlements`：app-sandbox + allow-jit + network.server + **network.client**
- `Runner/Release.entitlements`：app-sandbox + **network.client**

无需额外操作。直接 `flutter run -d macos`。
