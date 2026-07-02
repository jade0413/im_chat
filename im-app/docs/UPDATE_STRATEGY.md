# 更新策略

## 原则

热更新只用于小范围 Dart 层修复，不替代正式发版。协议变更、数据库 schema 变更、原生插件变更、权限变更、Rust FFI 或平台能力变更必须走整包发版。

## 移动端

Android 和 iOS 预留 Shorebird Code Push：

- `shorebird.yaml` 保存 app 配置。
- `lib/core/update/update_service.dart` 负责启动时检查和下载补丁。
- 补丁通常在下次冷启动生效。

## 桌面端

macOS 和 Windows 预留 Remote Config / OTA 配置中心：

- 通过 `DESKTOP_UPDATE_URL` 注入版本清单地址。
- 客户端比较 `version + build`。
- 有新版本时引导用户下载 `.dmg` 或 `.exe/.msix`。

## Remote Config

后续配置中心可承载：

- API/WS 灰度地址。
- 心跳间隔和重连退避参数。
- 功能开关，例如已读回执、文件消息、桌面更新提示。
- 租户级 UI 配置和客服能力开关。

