# iOS 平台配置

`bash tool/bootstrap.sh`（或 `flutter create --platforms=ios .`）生成 `ios/` 后，
在 `ios/Runner/Info.plist` 的根 `<dict>` 内补充以下键：

```xml
<!-- 开发期允许明文 ws://（生产用 wss://，上线前移除本段）-->
<key>NSAppTransportSecurity</key>
<dict>
    <key>NSAllowsArbitraryLoads</key>
    <true/>
</dict>

<!-- 富媒体消息用途说明（发图/语音时需要）-->
<key>NSCameraUsageDescription</key>
<string>发送图片或拍照需要访问相机</string>
<key>NSMicrophoneUsageDescription</key>
<string>发送语音消息需要访问麦克风</string>
<key>NSPhotoLibraryUsageDescription</key>
<string>发送图片需要访问相册</string>
```

生产环境改用 `wss://` 后，应移除 `NSAppTransportSecurity` 例外。
