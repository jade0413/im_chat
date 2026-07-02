#!/usr/bin/env bash
# ============================================================
# 一键生成四端平台工程 + 代码生成。
# flutter create 会保留已存在的自定义文件（android manifest / macOS entitlements /
# iOS Info.plist 已预置好联网/出网/ATS 配置），只补齐缺失的 runner 骨架与二进制资源。
#
# 用法：  bash tool/bootstrap.sh
# ============================================================
set -euo pipefail
cd "$(dirname "$0")/.."

echo "→ 1/4 生成四端平台工程（已保留预置的平台配置文件）"
flutter create --org com.lumo --project-name im_app \
  --platforms=android,ios,macos,windows .

echo "→ 2/4 生成 protobuf 绑定"
bash tool/generate_proto.sh

echo "→ 3/4 拉取依赖"
flutter pub get

echo "→ 4/4 drift / 代码生成"
dart run build_runner build --delete-conflicting-outputs

echo ""
echo "✓ 完成。运行：flutter run -d macos | windows | <android/ios 设备>"
echo "  平台关键配置（已预置，详见各平台 SETUP.md）："
echo "   · Android  INTERNET 权限 + 开发明文白名单"
echo "   · macOS    com.apple.security.network.client 出网 entitlement（最易踩坑）"
echo "   · iOS      NSAppTransportSecurity 开发例外 + 相机/麦克风/相册用途说明"
