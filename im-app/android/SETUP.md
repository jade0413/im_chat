# Android 平台配置（已预置）

- `app/src/main/AndroidManifest.xml`：已加 `INTERNET` / `ACCESS_NETWORK_STATE` 权限、
  应用名「微光 Lumo」、`networkSecurityConfig` 引用。
- `app/src/main/res/xml/network_security_config.xml`：开发期对 `10.0.2.2 / 127.0.0.1 / localhost`
  放行明文（`ws://`/`http://`）。生产用 `wss/https`，上线前收紧或移除。

`flutter create --platforms=android .` 会保留以上文件，仅补齐 gradle / MainActivity 等骨架。
模拟器访问宿主机后端用 `10.0.2.2`（见 `lib/core/config/env.dart` 默认值）。
