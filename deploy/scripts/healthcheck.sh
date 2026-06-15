#!/usr/bin/env bash
# =============================================================================
# 部署后健康巡检：一眼看全栈是否正常
# 用法：  ./healthcheck.sh
# =============================================================================
set -uo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../docker-compose"

ok()   { printf '  \033[1;32m✓\033[0m %s\n' "$*"; }
bad()  { printf '  \033[1;31m✗\033[0m %s\n' "$*"; }

echo "== 容器状态 =="
docker compose ps

echo ""
echo "== 端点探活 =="
curl -fsS http://127.0.0.1:8081/actuator/health/readiness >/dev/null 2>&1 \
  && ok "im-server REST readiness (8081)" || bad "im-server 未就绪 (8081)"
curl -fsS http://127.0.0.1:8080/health >/dev/null 2>&1 \
  && ok "im-gateway /health (8080)"       || bad "im-gateway 未就绪 (8080)"
curl -fsS http://127.0.0.1:80/ >/dev/null 2>&1 \
  && ok "im-web (80)"                      || bad "im-web 未就绪 (80)"

echo ""
echo "== 中间件连通（读 .env 里的密码）=="
set -a; [[ -f .env ]] && . ./.env; set +a
docker compose exec -T mysql mysqladmin ping -h localhost -p"${MYSQL_ROOT_PASSWORD:-}" >/dev/null 2>&1 \
  && ok "MySQL ping" || bad "MySQL 不可达"
docker compose exec -T redis redis-cli -a "${REDIS_PASSWORD:-}" ping 2>/dev/null | grep -q PONG \
  && ok "Redis PONG" || bad "Redis 不可达"
docker compose exec -T rabbitmq rabbitmq-diagnostics -q ping >/dev/null 2>&1 \
  && ok "RabbitMQ ping" || bad "RabbitMQ 不可达"

echo ""
echo "== MySQL 表数量（应为 15 张）=="
docker compose exec -T mysql mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-}" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='im';" 2>/dev/null \
  | xargs -I{} echo "  im 库表数：{}"
