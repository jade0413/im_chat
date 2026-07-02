# Windows 平台配置

WebSocket / REST 开箱即用，无需额外网络权限。

- 应用窗口标题在 `runner/main.cpp`（`flutter create` 生成）中设置，可改为「微光 Lumo」。
- 若打 MSIX 分发包，在 `msix` 配置中勾选 `internetClient` 能力。

`bash tool/bootstrap.sh`（或 `flutter create --platforms=windows .`）生成 runner 后即可
`flutter run -d windows`。
