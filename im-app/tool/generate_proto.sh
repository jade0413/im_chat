#!/usr/bin/env bash
# ============================================================
# 从 im-proto（唯一事实来源）生成 Dart protobuf 绑定。
# 对齐 im-web 的 `proto:gen`：客户端只编译连接层 + 业务 body + common，
# 不编译网关/内部 RPC（rpc/gateway.proto, rpc/internal.proto, events/）。
#
# 依赖：
#   - protoc            （brew install protobuf / choco install protoc）
#   - protoc-gen-dart   （dart pub global activate protoc_plugin，并确保 PATH 含 ~/.pub-cache/bin）
#
# 用法：  bash tool/generate_proto.sh
# 产物：  lib/core/proto/generated/*.pb.dart（已 gitignore，构建前生成）
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROTO_ROOT="$(cd "$APP_DIR/../im-proto/proto" && pwd)"
OUT_DIR="$APP_DIR/lib/core/proto/generated"
export PATH="$PATH:$HOME/.pub-cache/bin"
if [[ -n "${LOCALAPPDATA:-}" ]] && command -v cygpath >/dev/null 2>&1; then
  export PATH="$PATH:$(cygpath -u "$LOCALAPPDATA")/Pub/Cache/bin"
fi

if ! command -v protoc >/dev/null 2>&1; then
  echo "✗ 未找到 protoc，请先安装：brew install protobuf（mac）/ choco install protoc（win）"; exit 1
fi
if ! command -v protoc-gen-dart >/dev/null 2>&1; then
  echo "✗ 未找到 protoc-gen-dart，请执行：dart pub global activate protoc_plugin"
  echo "  并把 ~/.pub-cache/bin 加入 PATH"; exit 1
fi

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

# 与 im-web 编译集合一致 + error.proto（错误码）
PROTO_FILES=(
  "ws/frame.proto"
  "body/messages.proto"
  "body/call.proto"
  "common/content.proto"
  "common/enums.proto"
  "common/error.proto"
)

echo "→ protoc 生成 Dart 绑定到 $OUT_DIR"
protoc \
  --proto_path="$PROTO_ROOT" \
  --dart_out="$OUT_DIR" \
  "${PROTO_FILES[@]}"

echo "✓ 完成。生成文件："
ls -1 "$OUT_DIR"
echo ""
echo "提示：改了 im-proto 后必须重跑本脚本，并保证 Rust/Java/Dart 三端同时编译通过（核心约定 5）。"
