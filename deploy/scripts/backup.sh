#!/usr/bin/env bash
# =============================================================================
# 每日备份：MySQL 全库 + Redis AOF + MinIO 媒体。建议 crontab 每天调用。
# 用法：  ./backup.sh
# crontab 示例（每天 03:30）：
#   30 3 * * * /opt/im_chat/deploy/scripts/backup.sh >> /var/log/im-backup.log 2>&1
# =============================================================================
set -euo pipefail

# 【可改】备份输出根目录。生产建议放在独立磁盘或远端挂载，再同步到异机/对象存储。
BACKUP_ROOT="/opt/im-backups"
# 【可改】保留天数，超过自动清理。
RETENTION_DAYS=14

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../docker-compose"
set -a; . ./.env; set +a

TS="$(date '+%Y%m%d-%H%M%S')"
DEST="$BACKUP_ROOT/$TS"
mkdir -p "$DEST"
echo "[backup] 输出目录：$DEST"

# 1) MySQL 全库导出（单事务，不锁表）
echo "[backup] dump MySQL…"
docker compose exec -T mysql mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers --databases im \
  | gzip > "$DEST/mysql-im.sql.gz"

# 2) Redis：触发一次 AOF/RDB 落盘后拷贝数据卷
echo "[backup] Redis 落盘…"
docker compose exec -T redis redis-cli -a "$REDIS_PASSWORD" BGSAVE >/dev/null 2>&1 || true
sleep 3
docker run --rm -v im-project_redis-data:/data -v "$DEST":/backup alpine \
  tar czf /backup/redis-data.tar.gz -C /data . 2>/dev/null || \
  echo "[backup] (跳过 Redis 卷打包：卷名可能不同，用 'docker volume ls' 确认 *_redis-data)"

# 3) MinIO 媒体卷
echo "[backup] MinIO 媒体…"
docker run --rm -v im-project_minio-data:/data -v "$DEST":/backup alpine \
  tar czf /backup/minio-data.tar.gz -C /data . 2>/dev/null || \
  echo "[backup] (跳过 MinIO 卷打包：卷名可能不同，用 'docker volume ls' 确认 *_minio-data)"

# 4) 清理过期备份
find "$BACKUP_ROOT" -maxdepth 1 -type d -mtime +"$RETENTION_DAYS" -exec rm -rf {} \; 2>/dev/null || true

echo "[backup] 完成：$DEST"
du -sh "$DEST"/* 2>/dev/null || true
