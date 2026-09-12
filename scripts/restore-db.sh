#!/usr/bin/env bash
# =============================================================================
# 智联生活 · 数据库还原脚本
#
# 用法：
#   ./scripts/restore-db.sh backup/smartlife_20250912_030000.sql.gz
#
# 说明：还原会覆盖现有同名库表数据，请先停止写入（或直接停 backend 容器）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"

BACKUP_FILE="${1:-}"
if [[ -z "${BACKUP_FILE}" || ! -f "${BACKUP_FILE}" ]]; then
  echo "用法: $0 <备份文件.sql.gz>"
  echo "可用备份："
  ls -lh backup/*.sql.gz 2>/dev/null || echo "  (backup/ 目录为空)"
  exit 1
fi

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "[ERROR] 未找到 ${ENV_FILE}"
  exit 1
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

: "${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD 未设置}"
DB_NAME="${MYSQL_DATABASE:-smartlife}"

echo "即将把 ${BACKUP_FILE} 还原到数据库 ${DB_NAME}，现有数据会被覆盖。"
read -r -p "确认继续？输入 yes 继续：" CONFIRM
if [[ "${CONFIRM}" != "yes" ]]; then
  echo "已取消"
  exit 0
fi

echo "[$(date '+%F %T')] 停止后端写入 ..."
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" stop backend >/dev/null

echo "[$(date '+%F %T')] 还原中 ..."
gunzip -c "${BACKUP_FILE}" | docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" exec -T mysql \
  mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" --default-character-set=utf8mb4 "${DB_NAME}"

echo "[$(date '+%F %T')] 重启后端 ..."
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" start backend >/dev/null

echo "[$(date '+%F %T')] 还原完成。建议执行：docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE} logs -f backend"
