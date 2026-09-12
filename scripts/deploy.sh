#!/usr/bin/env bash
# =============================================================================
# 智联生活 · 一键部署 / 升级脚本（阿里云 Ubuntu 22.04）
#
# 用法：
#   ./scripts/deploy.sh              # 拉取代码后重新构建并启动（升级）
#   ./scripts/deploy.sh --first      # 首次部署（校验环境 + 构建 + 启动 + 健康检查）
#   ./scripts/deploy.sh --logs       # 查看后端日志
#   ./scripts/deploy.sh --status     # 查看容器状态
#   ./scripts/deploy.sh --down       # 停止全部服务（保留数据卷）
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE="docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE}"

log() { echo -e "\033[32m[$(date '+%F %T')]\033[0m $*"; }
err() { echo -e "\033[31m[$(date '+%F %T')] [ERROR]\033[0m $*" >&2; }

case "${1:-}" in
  --logs)
    ${COMPOSE} logs -f --tail=200 backend
    exit 0
    ;;
  --status)
    ${COMPOSE} ps
    exit 0
    ;;
  --down)
    log "停止全部服务（数据卷保留）"
    ${COMPOSE} down
    exit 0
    ;;
esac

# ---------------------------------------------------------------- 环境校验
log "校验运行环境 ..."
if ! command -v docker >/dev/null 2>&1; then
  err "未安装 Docker，请先执行：curl -fsSL https://get.docker.com | bash"
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  err "未安装 Docker Compose 插件，请执行：apt-get install -y docker-compose-plugin"
  exit 1
fi
if [[ ! -f "${ENV_FILE}" ]]; then
  err "未找到 ${ENV_FILE}，请先执行：cp .env.prod.example .env.prod && vim .env.prod"
  exit 1
fi
if grep -q "CHANGE_ME" "${ENV_FILE}"; then
  err "${ENV_FILE} 中仍有 CHANGE_ME 占位密码，请先修改为强密码"
  grep -n "CHANGE_ME" "${ENV_FILE}" || true
  exit 1
fi

# 内存检查：全栈约需 2GB 以上可用内存
MEM_MB="$(free -m | awk '/^Mem:/{print $7}')"
log "当前可用内存：${MEM_MB} MB"
if [[ "${MEM_MB}" -lt 1500 ]]; then
  err "可用内存不足 1.5G，建议升级到 2C4G 或先增加 Swap（见 docs/部署教程.md FAQ）"
  exit 1
fi

mkdir -p backup

# ---------------------------------------------------------------- 构建启动
if [[ "${1:-}" == "--first" ]]; then
  log "首次部署：构建镜像（首次约 5~15 分钟，取决于服务器带宽）..."
  ${COMPOSE} build --pull
else
  log "升级部署：重新构建变更镜像 ..."
  ${COMPOSE} build
fi

log "启动服务 ..."
${COMPOSE} up -d --remove-orphans

# ---------------------------------------------------------------- 健康检查
log "等待服务就绪（最多 180 秒）..."
HTTP_PORT_VALUE="$(grep -E '^HTTP_PORT=' "${ENV_FILE}" | cut -d= -f2 | tr -d ' ')"
HTTP_PORT_VALUE="${HTTP_PORT_VALUE:-80}"

for i in $(seq 1 36); do
  if curl -fsS "http://127.0.0.1:${HTTP_PORT_VALUE}/" >/dev/null 2>&1; then
    log "前端已就绪 ✅"
    break
  fi
  sleep 5
  if [[ "${i}" -eq 36 ]]; then
    err "前端未在 180 秒内就绪，请查看日志：${COMPOSE} logs --tail=200 frontend backend"
    exit 1
  fi
done

for i in $(seq 1 36); do
  if curl -fsS -X POST "http://127.0.0.1:${HTTP_PORT_VALUE}/api/auth/login" \
      -H 'Content-Type: application/json' \
      -d '{"phone":"13800000000","password":"admin123"}' 2>/dev/null | grep -q '"code":1'; then
    log "后端 API 已就绪 ✅（管理员演示账号可登录）"
    break
  fi
  sleep 5
  if [[ "${i}" -eq 36 ]]; then
    err "后端 API 未在 180 秒内就绪，请查看日志：${COMPOSE} logs --tail=200 backend"
    exit 1
  fi
done

echo
log "部署完成 🎉"
PUBLIC_IP="$(curl -fsS --max-time 3 https://api.ipify.org 2>/dev/null || echo '服务器公网IP')"
echo "  用户端：http://${PUBLIC_IP}/user/home"
echo "  商家端：http://${PUBLIC_IP}/merchant/dashboard"
echo "  管理端：http://${PUBLIC_IP}/admin/dashboard"
echo "  演示账号：管理员 13800000000/admin123 ｜ 商家 13700000001/merchant123 ｜ 用户 13900000001/123456"
echo
echo "常用命令："
echo "  查看状态：./scripts/deploy.sh --status"
echo "  查看日志：./scripts/deploy.sh --logs"
echo "  备份数据：./scripts/backup-db.sh"
