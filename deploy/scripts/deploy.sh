#!/usr/bin/env bash
# =============================================================================
# im-project 单机一键部署脚本
#
# 用法：
#   ./deploy.sh                # 完整部署：中间件 + 应用（本机编译镜像）
#   ./deploy.sh --server-only  # 只发布 Java im-server；不重启网关，避免踢掉现有 WS 连接
#   ./deploy.sh --gateway-only # 只发布 Rust 网关；会断开现有 WS 连接，谨慎用于网关改动
#   ./deploy.sh --web-only     # 只发布 Web/nginx
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
WITH_OBS="no"; DO_BUILD="yes"; MW_ONLY="no"; APP_SCOPE="all"
for arg in "$@"; do
  case "$arg" in
    --obs)      WITH_OBS="yes" ;;
    --no-build) DO_BUILD="no" ;;
    --mw-only)  MW_ONLY="yes" ;;
    --server-only)  APP_SCOPE="server" ;;
    --gateway-only) APP_SCOPE="gateway" ;;
    --web-only)     APP_SCOPE="web" ;;
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

# ---- 2. 自动修复 im-web/nginx.conf 的 /ws 反代（历史错误：im-gateway-rust:9090 → im-gateway:8080）----
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
wait_completed() {  # $1=服务名 $2=最多重试次数
  local svc="$1" tries="${2:-40}" cid status exit_code
  for ((i=1; i<=tries; i++)); do
    cid="$(docker compose ps -a -q "$svc" 2>/dev/null | head -n1 || true)"
    if [[ -n "$cid" ]]; then
      status="$(docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || true)"
      exit_code="$(docker inspect -f '{{.State.ExitCode}}' "$cid" 2>/dev/null || true)"
      if [[ "$status" == "exited" && "$exit_code" == "0" ]]; then
        log "  ✓ $svc completed"
        return 0
      fi
      if [[ "$status" == "exited" && "$exit_code" != "0" ]]; then
        warn "  ✗ $svc 执行失败，最近日志："
        docker compose logs --tail=30 "$svc" || true
        die "$svc 未完成，终止。"
      fi
    fi
    sleep 3
  done
  warn "  ✗ $svc 未在预期时间内完成，最近日志："
  docker compose logs --tail=30 "$svc" || true
  die "$svc 未完成，终止。"
}
for s in mysql redis rabbitmq minio; do wait_healthy "$s"; done
wait_completed minio-init 40
log "中间件就绪。MySQL 首启已自动建表，MinIO 已建 bucket im-media。"

if [[ "$MW_ONLY" == "yes" ]]; then
  log "仅中间件模式完成。"; exit 0
fi

preflight_im_server() {
  local image network name running
  image="$(docker compose --profile app images -q im-server | head -n1)"
  [[ -n "$image" ]] || die "找不到 im-server 镜像，无法做发布前预检。"

  network="${COMPOSE_PROJECT_NAME:-im-project}_default"
  docker network inspect "$network" >/dev/null 2>&1 || die "找不到 compose 网络：$network"

  # 只读取本目录 .env。deploy.sh 生成的值都是 shell-safe；手工改 .env 时也应保持 KEY=VALUE 格式。
  set -a
  set -f
  # shellcheck disable=SC1091
  source ./.env
  set +f
  set +a

  name="im-server-preflight-$(date +%s)"
  docker rm -f "$name" >/dev/null 2>&1 || true

  log "发布前预检 im-server 新镜像（通过前不会停止旧容器）…"
  docker run -d \
    --name "$name" \
    --network "$network" \
    -e SPRING_PROFILES_ACTIVE=docker \
    -e IM_STARTUP_CHECK_ENABLED=true \
    -e MYSQL_URL=jdbc:mysql://mysql:3306/im \
    -e MYSQL_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
    -e REDIS_HOST=redis \
    -e REDIS_PASSWORD="${REDIS_PASSWORD}" \
    -e RABBITMQ_HOST=rabbitmq \
    -e RABBITMQ_USER="${RABBITMQ_USER}" \
    -e RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD}" \
    -e MINIO_ENDPOINT=http://minio:9000 \
    -e MINIO_PUBLIC_ENDPOINT="${MINIO_PUBLIC_ENDPOINT:-http://localhost:9000}" \
    -e MINIO_ROOT_USER="${MINIO_ROOT_USER}" \
    -e MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD}" \
    -e MINIO_BUCKET="${MINIO_BUCKET:-im-media}" \
    -e IM_AUTH_JWT_SECRET="${JWT_SECRET}" \
    -e IM_CALL_TURN_URLS="${IM_CALL_TURN_URLS:-turn:${EXTERNAL_IP:-127.0.0.1}:3478?transport=udp}" \
    -e IM_CALL_TURN_SECRET="${TURN_SECRET:-dev-turn-secret-change-me}" \
    -e IM_RABBITMQ_LISTENER_AUTO_STARTUP=false \
    -e OUTBOX_ENABLED=false \
    -e IM_OUTBOX_ENABLED=false \
    -e IM_MSG_RETENTION_ENABLED=false \
    -e IM_FILE_TRANSCODE_ENABLED=false \
    -e IM_WORKER_ID=1023 \
    "$image" >/dev/null

  for ((i=1; i<=40; i++)); do
    if docker exec "$name" curl -fsS http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1; then
      docker rm -f "$name" >/dev/null 2>&1 || true
      log "  ✓ im-server 新镜像预检通过"
      return 0
    fi

    running="$(docker inspect -f '{{.State.Running}}' "$name" 2>/dev/null || echo false)"
    if [[ "$running" != "true" ]]; then
      warn "im-server 新镜像预检失败，旧容器未被替换。最近日志："
      docker logs --tail=120 "$name" 2>/dev/null || true
      docker rm -f "$name" >/dev/null 2>&1 || true
      die "新版本启动失败，已终止发布。"
    fi
    sleep 3
  done

  warn "im-server 新镜像预检超时，旧容器未被替换。最近日志："
  docker logs --tail=120 "$name" 2>/dev/null || true
  docker rm -f "$name" >/dev/null 2>&1 || true
  die "新版本未在预期时间内就绪，已终止发布。"
}

# ---- 4. 起应用 ----
APP_UP_SERVICES=()
APP_BUILD_SERVICES=()
case "$APP_SCOPE" in
  server)
    APP_UP_SERVICES=(im-server)
    APP_BUILD_SERVICES=(im-server)
    ;;
  gateway)
    APP_UP_SERVICES=(im-gateway)
    APP_BUILD_SERVICES=(im-gateway)
    warn "即将重启 im-gateway，现有 WebSocket 连接会断开。只有网关代码/配置变化时才用 --gateway-only。"
    ;;
  web)
    APP_UP_SERVICES=(im-web)
    APP_BUILD_SERVICES=(im-web)
    ;;
  all)
    APP_UP_SERVICES=(im-server im-gateway coturn im-web)
    APP_BUILD_SERVICES=(im-server im-gateway im-web)
    warn "完整应用发布会重建 im-gateway/im-web；在线 WebSocket 会短暂断开。日常后端发版建议用 --server-only。"
    ;;
esac

if [[ "$DO_BUILD" == "yes" ]]; then
  log "先构建应用镜像：${APP_BUILD_SERVICES[*]}（build 成功前不会触碰现有容器）…"
  docker compose --profile app build "${APP_BUILD_SERVICES[@]}"
  if [[ " ${APP_BUILD_SERVICES[*]} " == *" im-server "* ]]; then
    preflight_im_server
  fi
  log "构建成功，启动/替换服务：${APP_UP_SERVICES[*]} …"
else
  # --no-build：若填了镜像地址，导出为 compose 可覆盖的环境变量（需 compose 文件支持 image 覆盖）
  [[ -n "$IMAGE_IM_SERVER"  ]] && warn "镜像模式：请确认已把 compose 中 im-server 的 build 改为 image:$IMAGE_IM_SERVER"
  if [[ " ${APP_UP_SERVICES[*]} " == *" im-server "* ]]; then
    preflight_im_server
  fi
  log "启动应用（使用预构建镜像，不编译）…"
fi
if [[ "$APP_SCOPE" == "all" ]]; then
  docker compose --profile app up -d --no-deps im-server
  wait_healthy im-server 100
  docker compose --profile app up -d --no-deps im-gateway
  wait_healthy im-gateway 40
  docker compose --profile app up -d --no-deps coturn im-web
else
  docker compose --profile app up -d --no-deps "${APP_UP_SERVICES[@]}"
  if [[ "$APP_SCOPE" == "server" ]]; then wait_healthy im-server 100; fi
  if [[ "$APP_SCOPE" == "gateway" ]]; then wait_healthy im-gateway 40; fi
fi

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
