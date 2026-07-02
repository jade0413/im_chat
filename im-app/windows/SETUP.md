# Windows 平台配置

WebSocket / REST 开箱即用，无需额外网络权限。

- 应用窗口标题在 `runner/main.cpp`（`flutter create` 生成）中设置，可改为「微光 Lumo」。
- 若打 MSIX 分发包，在 `msix` 配置中勾选 `internetClient` 能力。

`bash tool/bootstrap.sh`（或 `flutter create --platforms=windows .`）生成 runner 后即可
`flutter run -d windows`。

## 打包给测试用户

在 Windows 电脑上执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\tool\package_windows.ps1
```

脚本会执行 `flutter build windows --release`，然后把
`build\windows\x64\runner\Release` 整体压缩到 `dist\`。发给别人时直接发这个
zip，不要只发 exe。
