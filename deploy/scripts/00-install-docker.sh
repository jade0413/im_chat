#!/usr/bin/env bash
# =============================================================================
# 在干净的 Linux 服务器上安装 Docker Engine + compose v2 插件
# 用法：  sudo ./00-install-docker.sh
# 已装 Docker 的机器无需运行。仅在 Ubuntu/Debian/CentOS 类系统验证。
# =============================================================================
set -euo pipefail

if command -v docker >/dev/null && docker compose version >/dev/null 2>&1; then
  echo "Docker 与 compose 插件已存在，无需安装："
  docker --version; docker compose version
  exit 0
fi

echo "[1/3] 用 Docker 官方脚本安装 Engine + compose 插件…"
curl -fsSL https://get.docker.com | sh

echo "[2/3] 设置开机自启并启动…"
systemctl enable --now docker

# 【可自行修改】把哪个普通用户加入 docker 组（免 sudo 用 docker）。默认调用脚本的用户。
TARGET_USER="${SUDO_USER:-$USER}"
echo "[3/3] 将用户 $TARGET_USER 加入 docker 组（重新登录后生效）…"
usermod -aG docker "$TARGET_USER" || true

echo "完成："
docker --version; docker compose version
echo "提示：$TARGET_USER 需重新登录（或执行 newgrp docker）才能免 sudo 使用 docker。"
