#!/usr/bin/env bash
# NutriAI — MySQL 每日备份（Docker Compose）
set -euo pipefail
cd "$(dirname "$0")"

BACKUP_DIR="${BACKUP_DIR:-./backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"

if [ ! -f ../.env ]; then
  echo "[ERROR] 缺少 ../.env"
  exit 1
fi

echo "==> 备份数据库到 ${BACKUP_DIR}/nutriai-${STAMP}.sql.gz"
docker compose exec -T db sh -c 'exec mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" --single-transaction --quick --routines --events "$MYSQL_DATABASE"' \
  | gzip -9 > "${BACKUP_DIR}/nutriai-${STAMP}.sql.gz"

# 校验文件非空
if [ ! -s "${BACKUP_DIR}/nutriai-${STAMP}.sql.gz" ]; then
  echo "[ERROR] 备份文件为空"
  exit 1
fi

echo "==> 清理 ${RETENTION_DAYS} 天前的备份"
find "$BACKUP_DIR" -name 'nutriai-*.sql.gz' -type f -mtime +"$RETENTION_DAYS" -delete

echo "✅ 完成：${BACKUP_DIR}/nutriai-${STAMP}.sql.gz"
