#!/usr/bin/env bash
# =============================================================================
# 智联生活 · 数据库备份脚本（阿里云 Ubuntu 22.04）
#
# 用法：
#   ./scripts/backup-db.sh                 # 备份到 ./backup/ 目录
#   ./scripts/backup-db.sh /data/backup    # 指定备份目录
#
# 定时任务（每天凌晨 3 点备份，保留最近 14 天）：
#   crontab -e
#   0 3 * * * cd /opt/smartlife && ./scripts/backup-db.sh >> /var/log/smartlife-backup.log 2>&1
#
# 依赖：docker compose（使用 mysql 容器内的 mysqldump，无需在宿主机装 MySQL 客户端）
# =============================================================================
set -euo pipefail

# 定位项目根目录（脚本所在目录的上一级）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
BACKUP_DIR="${1:-${PROJECT_DIR}/backup}"
KEEP_DAYS="${KEEP_DAYS:-14}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] 未找到 ${ENV_FILE}，请先 cp .env.prod.example .env.prod 并填写"
  exit 1
fi

# 读取 .env.prod 中的变量（忽略注释与空行）
set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 未设置}"
DB_NAME="${MYSQL_DATABASE:-smartlife}"
TS="$(date +%Y%m%d_%H%M%S)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}_${TS}.sql.gz"

mkdir -p "${BACKUP_DIR}"

echo "[$(date '+%F %T')] 开始备份数据库 ${DB_NAME} ..."

# --single-transaction：InnoDB 一致性快照，不锁表；--routines/--triggers 保证可完整还原
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T mysql \
  mysqldump -uroot -p"${MYSQL_ROOT_PASSWORD}" \
  --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF \
  --default-character-set=utf8mb4 \
  "${DB_NAME}" | gzip > "${OUT_FILE}"

SIZE="$(du -h "${OUT_FILE}" | cut -f1)"
echo "[$(date '+%F %T')] 备份完成：${OUT_FILE} (${SIZE})"

# 清理过期备份
DELETED="$(find "${BACKUP_DIR}" -name "${DB_NAME}_*.sql.gz" -type f -mtime "+${KEEP_DAYS}" -print -delete | wc -l)"
if [[ "${DELETED}" -gt 0 ]]; then
  echo "[$(date '+%F %T')] 已清理 ${DELETED} 个超过 ${KEEP_DAYS} 天的旧备份"
fi

# 可选：同步到阿里云 OSS（需先安装 ossutil 并配置 AK）
# if command -v ossutil64 >/dev/null 2>&1; then
#   ossutil64 cp "${OUT_FILE}" oss://your-bucket/smartlife-backup/ -f
#   echo "[$(date '+%F %T')] 已上传 OSS"
# fi
