#!/usr/bin/env bash
# =============================================================================
# im-project 单机一键部署脚本
#
# 用法：
#   ./deploy.sh                # 完整部署：中间件 + 应用（本机编译镜像）
#   ./deploy.sh --obs          # 额外启动观测栈（Prometheus/Grafana/Loki）
#   ./deploy.sh --no-build     # 应用用预构建镜像（方式C），服务器不编译
#   ./deploy.sh --mw-only      # 只起中间件（MySQL/Redis/RabbitMQ/MinIO）
#
# 前置：服务器已装 Docker（含 compose v2）。没装先跑 00-install-docker.sh。
# 脚本会自动定位 deploy/docker-compose 目录，从任意位置执行都可以。
# =============================================================================
set -euo pipefail

# ╔═══════════════════════════════════════════════════════════════════════╗
# ║  ① 需要你自己配置的参数 —— 部署前改这里                                  ║
# ╚═══════════════════════════════════════════════════════════════════════╝

# WebSocket Origin 白名单；多个用英文逗号隔开。
#   "*"                    = 放行所有来源（当前用 IP 直连/无域名阶段用这个）
#   https://im.example.com = 上线正式域名后改成它，收紧到只放行自己的站点
#   说明：App/IP 直连等非浏览器客户端不带 Origin 头，无论此项如何都放行。
ALLOWED_ORIGINS="*"

# 【建议 yes】中间件端口是否只绑本机 127.0.0.1（安全红线：不暴露公网）。
#   yes = MySQL/Redis/RabbitMQ/MinIO 仅本机可达；要连管理台用 SSH 隧道。
#   no  = 绑 0.0.0.0（仅当你已有外层防火墙/安全组严格限制时才用）。
BIND_LOCALHOST="yes"

# 【可选】握手限流：重连风暴大时调高。
HANDSHAKE_RATE_PER_SEC="200"
HANDSHAKE_RATE_BURST="400"

# 【可选，仅 --no-build 模式用】预构建镜像地址（方式C）。
#   留空则沿用 compose 里的 build 配置。填了会改用 image: 拉取。
IMAGE_IM_SERVER=""     # 例：registry.cn-hangzhou.aliyuncs.com/yourns/im-server:1.0
IMAGE_IM_GATEWAY=""    # 例：.../im-gateway:1.0
IMAGE_IM_WEB=""        # 例：.../im-web:1.0

# ╚════════════════ 以下一般不用改 ══════════════════════════════════════════╝

# ---- 解析参数 ----
WITH_OBS="no"; DO_BUILD="yes"; MW_ONLY="no"
for arg in "$@"; do
  case "$arg" in
    --obs)      WITH_OBS="yes" ;;
    --no-build) DO_BUILD="no" ;;
    --mw-only)  MW_ONLY="yes" ;;
    *) echo "未知参数：$arg"; exit 1 ;;
  esac
done

# ---- 定位 compose 目录（scripts/ 的上一级下的 docker-compose）----
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_DIR="$SCRIPT_DIR/../docker-compose"
REPO_ROOT="$SCRIPT_DIR/../.."
cd "$COMPOSE_DIR"

log()  { printf '\033[1;32m[deploy]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

# ---- 0. 环境自检 ----
log "检查 Docker 环境…"
command -v docker >/dev/null    || die "未找到 docker，请先运行 00-install-docker.sh"
docker compose version >/dev/null 2>&1 || die "未找到 docker compose v2 插件"
command -v openssl >/dev/null   || die "未找到 openssl（生成随机密码需要）"

# 随机串生成：仅字母数字，避免破坏 .env / amqp URL
gen() { openssl rand -base64 64 | tr -dc 'A-Za-z0-9' | cut -c1-"${1:-32}"; }

# ---- 1. 生成 .env（已存在则保留不覆盖）----
if [[ -f .env ]]; then
  warn ".env 已存在，沿用现有配置（不覆盖）。如需重生成请先备份删除它。"
else
  log "首次部署，自动生成 .env（强随机密码）…"
  # BIND_LOCALHOST=yes 时，把端口值写成 127.0.0.1:端口，compose 会绑定到本机
  if [[ "$BIND_LOCALHOST" == "yes" ]]; then P="127.0.0.1:"; else P=""; fi
  cat > .env <<EOF
# 本文件由 deploy.sh 自动生成（$(date '+%F %T')）。密码为强随机值，妥善保管，勿入库。
MYSQL_ROOT_PASSWORD=$(gen 32)
MYSQL_PORT=${P}3306

REDIS_PASSWORD=$(gen 32)
REDIS_PORT=${P}6379

RABBITMQ_USER=im
RABBITMQ_PASSWORD=$(gen 32)
RABBITMQ_PORT=${P}5672
RABBITMQ_MGMT_PORT=${P}15672

MINIO_ROOT_USER=im_minio
MINIO_ROOT_PASSWORD=$(gen 32)
MINIO_PORT=${P}9000
MINIO_CONSOLE_PORT=${P}9001

# im-server
JWT_SECRET=$(gen 48)

# im-gateway
IM_GATEWAY_ALLOWED_ORIGINS=${ALLOWED_ORIGINS}
IM_GATEWAY_HANDSHAKE_RATE_LIMIT_PER_SEC=${HANDSHAKE_RATE_PER_SEC}
IM_GATEWAY_HANDSHAKE_RATE_LIMIT_BURST=${HANDSHAKE_RATE_BURST}

GRAFANA_PASSWORD=$(gen 24)
EOF
  chmod 600 .env
  log ".env 已生成（权限 600）。所有密码可用 'cat $COMPOSE_DIR/.env' 查看。"
fi

if [[ "$ALLOWED_ORIGINS" == "https://im.example.com" ]]; then
  warn "ALLOWED_ORIGINS 还是示例域名 https://im.example.com —— 生产请改成真实域名！"
fi

# ---- 2. 自动修复 im-web/nginx.conf 的 /ws 反代（指向错误：im-gateway-rust:9090 → im-gateway:8080）----
NGINX_CONF="$REPO_ROOT/im-web/nginx.conf"
if [[ -f "$NGINX_CONF" ]] && grep -q "im-gateway-rust:9090" "$NGINX_CONF"; then
  cp "$NGINX_CONF" "$NGINX_CONF.bak"
  sed -i 's#http://im-gateway-rust:9090#http://im-gateway:8080#g' "$NGINX_CONF"
  log "已修复 im-web/nginx.conf 的 /ws 反代为 im-gateway:8080（原文件备份为 .bak）。"
fi

# ---- 3. 起中间件并等待 healthy ----
log "启动中间件（MySQL/Redis/RabbitMQ/MinIO）…"
docker compose up -d

wait_healthy() {  # $1=服务名 $2=最多重试次数
  local svc="$1" tries="${2:-60}" st
  for ((i=1; i<=tries; i++)); do
    st="$(docker compose ps --format '{{.Health}}' "$svc" 2>/dev/null | head -n1 || true)"
    [[ "$st" == "healthy" ]] && { log "  ✓ $svc healthy"; return 0; }
    sleep 3
  done
  warn "  ✗ $svc 未在预期时间内 healthy，最近日志："
  docker compose logs --tail=30 "$svc" || true
  die "中间件未就绪，终止。"
}
for s in mysql redis rabbitmq minio; do wait_healthy "$s"; done
log "中间件就绪。MySQL 首启已自动建表，MinIO 已建 bucket im-media。"

if [[ "$MW_ONLY" == "yes" ]]; then
  log "仅中间件模式完成。"; exit 0
fi

# ---- 4. 起应用 ----
if [[ "$DO_BUILD" == "yes" ]]; then
  log "构建并启动应用镜像（首次 Rust/Maven 编译较慢，请耐心）…"
  docker compose --profile app up -d --build
else
  # --no-build：若填了镜像地址，导出为 compose 可覆盖的环境变量（需 compose 文件支持 image 覆盖）
  [[ -n "$IMAGE_IM_SERVER"  ]] && warn "镜像模式：请确认已把 compose 中 im-server 的 build 改为 image:$IMAGE_IM_SERVER"
  log "启动应用（使用预构建镜像，不编译）…"
  docker compose --profile app up -d
fi
wait_healthy im-server 100
wait_healthy im-gateway 40

# ---- 5. 观测栈（可选）----
if [[ "$WITH_OBS" == "yes" ]]; then
  log "启动观测栈…"
  docker compose --profile obs up -d
fi

# ---- 6. 结果 ----
log "部署完成。当前服务："
docker compose ps
cat <<EOF

==================== 后续手动事项（重要）====================
1) 前置 nginx/SLB 终止 TLS，反代：
     /     /api/  -> im-web:80
     /ws          -> im-gateway:8080   （客户端连 wss://你的域名/ws）
2) 防火墙/安全组只放 80/443；本脚本已把中间件端口绑 127.0.0.1（BIND_LOCALHOST=$BIND_LOCALHOST）。
3) 5万级在线务必先跑同目录的 tune-host.sh（放开内核 fd/连接数上限，需 sudo）。
4) 配置每日备份：crontab 调用同目录 backup.sh。
5) 所有自动生成的密码在： $COMPOSE_DIR/.env
============================================================
EOF
