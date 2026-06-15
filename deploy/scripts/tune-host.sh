#!/usr/bin/env bash
# =============================================================================
# 宿主机内核 / 文件描述符调优 —— 大量 WebSocket 长连接（5万级在线）的硬前提
# 用法：  sudo ./tune-host.sh
# 不调这些，连接数到几千~上万就会因 fd / backlog 不足而掉线。
# =============================================================================
set -euo pipefail
[[ $EUID -eq 0 ]] || { echo "请用 sudo 运行"; exit 1; }

# 【可按机器内存调整】file-max 是全系统最大 fd 数；2百万对 5万连接足够有余。
cat > /etc/sysctl.d/99-im-gateway.conf <<'EOF'
# im-project 网关长连接调优
net.core.somaxconn = 32768
net.ipv4.tcp_max_syn_backlog = 8192
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_tw_reuse = 1
fs.file-max = 2000000
EOF
sysctl --system

# 放开登录会话的 nofile 上限（对宿主机进程生效；容器内 fd 上限由 compose 的 ulimits 控制）
cat > /etc/security/limits.d/99-im.conf <<'EOF'
* soft nofile 1000000
* hard nofile 1000000
EOF

echo "已写入 sysctl 与 limits 配置并生效。"
echo ""
echo ">>> 还需手动一步：给网关容器放开 fd 上限。"
echo "    在 docker-compose.yml 的 im-gateway 服务下加："
echo ""
echo "    ulimits:"
echo "      nofile:"
echo "        soft: 1000000"
echo "        hard: 1000000"
echo ""
echo "    （compose 不读取宿主机 limits.conf，必须显式声明）"
