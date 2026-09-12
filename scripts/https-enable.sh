#!/usr/bin/env bash
# =============================================================================
# 智联生活 · 一键启用 HTTPS（Let's Encrypt IP 证书 / 域名证书）
#
# 背景
#   Let's Encrypt 自 2026-01-15 起正式为「公网 IP」签发免费证书，因此没有域名、
#   也不想走 ICP 备案时，可以直接把 http://<公网IP>/ 升级为 https://<公网IP>/。
#   代价：IP 证书只能是 shortlived 短期证书（约 6 天），必须自动化续签，
#   本脚本用 acme.sh 完成签发 + 安装 + 定时续签 + reload 容器内 Nginx。
#
# 用法
#   ./scripts/https-enable.sh                 # 启用（自动识别公网 IP；幂等，可反复执行）
#   ./scripts/https-enable.sh --verify-renew  # 首次建议：额外做一次强制续签，验证续签链路真的通
#   ./scripts/https-enable.sh --staging       # 用 LE 测试环境走一遍（证书不被浏览器信任，仅验证流程）
#   ./scripts/https-enable.sh --status        # 看证书有效期 / 定时任务 / 端口 / 探活
#   ./scripts/https-enable.sh --renew         # 手动跑一次续签（未到期不会真续，不浪费限额）
#   ./scripts/https-enable.sh --disable       # 回滚为纯 HTTP（证书与定时任务保留）
#   CERT_ID=life.example.com ./scripts/https-enable.sh   # 以后有备案域名了，一条命令换成域名证书
#
# 前置条件（脚本会逐项自检并给出修复命令）
#   1) 服务器已部署：./scripts/deploy.sh 跑过一次（前端镜像里有 site.inc、编排挂了 certs/nginx-extra/acme-webroot）
#   2) 阿里云安全组放行 80 与 443（80 用于 LE 校验，443 用于访问）
#   3) root 权限（写 /root/.acme.sh、执行 docker exec）
#
# 为什么不需要重启容器、不需要重建镜像
#   证书装在宿主 ./certs/，只读挂载进容器 /etc/nginx/certs/；
#   443 站点与跳转配置写在宿主 ./nginx-extra/，只读挂载进 /etc/nginx/conf.d-extra/。
#   续签只是覆盖这两个目录里的文件 + docker exec nginx -s reload。
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

ENV_FILE="${ENV_FILE:-.env.prod}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
COMPOSE="docker compose --env-file ${ENV_FILE} -f ${COMPOSE_FILE}"

FRONTEND_CONTAINER="${FRONTEND_CONTAINER:-smartlife-frontend}"
ACME_HOME="${ACME_HOME:-/root/.acme.sh}"
ACME="${ACME_HOME}/acme.sh"
# acme.sh 的续签由 crontab 拉起，cron 的 PATH 很干净，这里写绝对路径最稳
DOCKER_BIN="$(command -v docker || echo /usr/bin/docker)"
# acme.sh 支持哪个 profile 参数（3.1.x 是 --cert-profile，网上教程常写成 --certificate-profile）
PROFILE_FLAG=""
CERT_DIR="${PROJECT_DIR}/certs"
NGINX_EXTRA_DIR="${PROJECT_DIR}/nginx-extra"
EXTRA_HTTP_DIR="${NGINX_EXTRA_DIR}/http"
EXTRA_REDIRECT_DIR="${NGINX_EXTRA_DIR}/redirect"
WEBROOT="${PROJECT_DIR}/acme-webroot"
TEMPLATE_DIR="${SCRIPT_DIR}/templates"
LE_SERVER="${LE_SERVER:-letsencrypt}"
# 6 天期证书：剩余不足 3 天就续，约每 3.6 天续一次（远低于 LE「同一标识集 5 张/7 天」限额）
RENEW_DAYS="${RENEW_DAYS:-3}"
# 域名证书（非 IP）默认用 90 天期，剩余 30 天再续
DOMAIN_RENEW_DAYS="${DOMAIN_RENEW_DAYS:-30}"

log()  { echo -e "\033[32m[$(date '+%F %T')]\033[0m $*"; }
warn() { echo -e "\033[33m[$(date '+%F %T')] [WARN]\033[0m $*"; }
err()  { echo -e "\033[31m[$(date '+%F %T')] [ERROR]\033[0m $*" >&2; }
die()  { err "$*"; exit 1; }

usage() { sed -n '2,28p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

# ---------------------------------------------------------------- 参数解析
MODE=enable
STAGING=0
FORCE=0
VERIFY_RENEW=0
SHORTLIVED=0
CERT_ID="${CERT_ID:-}"
ACME_EMAIL="${ACME_EMAIL:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --enable)       MODE=enable ;;
    --disable)      MODE=disable ;;
    --status)       MODE=status ;;
    --renew)        MODE=renew ;;
    --staging)      STAGING=1 ;;
    --force)        FORCE=1 ;;
    --verify-renew) VERIFY_RENEW=1 ;;
    --shortlived)   SHORTLIVED=1 ;;
    --email)        ACME_EMAIL="${2:-}"; shift ;;
    -h|--help)      usage; exit 0 ;;
    *)              die "未知参数：$1（用 --help 查看用法）" ;;
  esac
  shift
done

# ---------------------------------------------------------------- 环境读取
read_env() { # $1=KEY，读 .env.prod，去掉行尾 CR（Windows 编辑过的文件常见）
  [[ -f "${ENV_FILE}" ]] || return 0
  sed -n "s/^[[:space:]]*$1=//p" "${ENV_FILE}" | tail -1 | tr -d '\r'
}

HTTP_PORT="$(read_env HTTP_PORT)";   HTTP_PORT="${HTTP_PORT:-80}"
HTTPS_PORT="$(read_env HTTPS_PORT)"; HTTPS_PORT="${HTTPS_PORT:-443}"
if [[ -z "${ACME_EMAIL}" ]]; then ACME_EMAIL="$(read_env ACME_EMAIL)"; fi

is_ipv4() { [[ "$1" =~ ^([0-9]{1,3}\.){3}[0-9]{1,3}$ ]]; }
is_ipv6() { [[ "$1" == *:* ]]; }
is_ip()   { is_ipv4 "$1" || is_ipv6 "$1"; }

# ---------------------------------------------------------------- 只读动作
show_cert_files() {
  if [[ -s "${CERT_DIR}/fullchain.pem" ]]; then
    openssl x509 -in "${CERT_DIR}/fullchain.pem" -noout -subject -issuer -dates 2>/dev/null \
      | sed 's/^/    /'
    local end_ts now_ts left_days
    end_ts="$(openssl x509 -in "${CERT_DIR}/fullchain.pem" -noout -enddate 2>/dev/null | cut -d= -f2)"
    now_ts="$(date -d "${end_ts}" +%s 2>/dev/null || echo 0)"
    if [[ "${now_ts}" -le 0 ]]; then
      warn "无法解析证书到期时间（${end_ts}），请手动确认"
      return 0
    fi
    left_days="$(( ( now_ts - $(date +%s) ) / 86400 ))"
    if [[ "${left_days}" -lt 0 ]]; then
      err "证书已过期！立刻执行：./scripts/https-enable.sh --renew"
      err "并检查 acme.sh 定时任务是否还在：crontab -l | grep acme.sh"
    elif [[ "${left_days}" -lt 2 ]]; then
      warn "证书剩余 ${left_days} 天，续签可能卡住了：./scripts/https-enable.sh --renew 看报错"
    else
      log "证书剩余约 ${left_days} 天（到期前 acme.sh 会自动续签并 reload）"
    fi
  else
    warn "未找到证书文件 ${CERT_DIR}/fullchain.pem（当前应为纯 HTTP 模式）"
  fi
}

do_status() {
  echo "==================== HTTPS 状态 ===================="
  echo "[证书]"
  show_cert_files
  echo
  echo "[acme.sh 定时任务]（acme.sh 安装时自动写入 crontab，每天检查一次是否该续）"
  if [[ "${EUID}" -ne 0 ]]; then
    warn "当前不是 root：acme.sh 的定时任务是装在 root 的 crontab 里的，下面可能显示不出来，请用 sudo 再跑一次"
  fi
  crontab -l 2>/dev/null | grep -i 'acme.sh' | sed 's/^/    /' || echo "    （无：说明 acme.sh 没装或 cron 被清了，重跑 --enable 可补）"
  echo
  echo "[acme.sh 记录的证书信息]"
  [[ -x "${ACME}" ]] && "${ACME}" --list 2>/dev/null | sed 's/^/    /' || echo "    （未安装 acme.sh）"
  echo
  echo "[已启用的 Nginx 附加配置]"
  ls -1 "${EXTRA_HTTP_DIR}"/*.conf "${EXTRA_REDIRECT_DIR}"/*.conf 2>/dev/null | sed 's/^/    /' || echo "    （无：纯 HTTP 模式）"
  echo
  echo "[容器端口]"
  docker port "${FRONTEND_CONTAINER}" 2>/dev/null | sed 's/^/    /' || echo "    （容器未运行）"
  echo
  echo "[探活]"
  local c1 c2
  c1="$(curl -k -sS -o /dev/null -w '%{http_code}' --max-time 8 "https://127.0.0.1/" 2>/dev/null || true)"
  c2="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1/" 2>/dev/null || true)"
  echo "    https://127.0.0.1/  → ${c1:-失败}"
  echo "    http://127.0.0.1/   → ${c2:-失败}"
  echo "===================================================="
}

# ---------------------------------------------------------------- 前置自检
require_root() {
  [[ "${EUID}" -eq 0 ]] || die "需要 root 权限（要写 ${ACME_HOME} 并 reload 容器）：sudo $0 $*"
}

require_env_file() {
  [[ -f "${ENV_FILE}" ]] || die "未找到 ${ENV_FILE}，请先 cp .env.prod.example .env.prod 并改好密码"
}

require_running_stack() {
  command -v docker >/dev/null 2>&1 || die "未安装 Docker"
  docker inspect -f '{{.State.Running}}' "${FRONTEND_CONTAINER}" 2>/dev/null | grep -qx true \
    || die "前端容器 ${FRONTEND_CONTAINER} 未运行，请先执行：${COMPOSE} up -d"
}

# 新版编排/镜像才挂载了这几个路径；旧版直接启用 HTTPS 会写不进去
require_new_deploy() {
  docker exec "${FRONTEND_CONTAINER}" test -f /etc/nginx/snippets/site.inc >/dev/null 2>&1 \
    || die "容器内缺少 /etc/nginx/snippets/site.inc —— 当前跑的是旧镜像。
  请先执行：./scripts/deploy.sh   （会重建前端镜像并把 80/443 共用配置打进去）"
  docker exec "${FRONTEND_CONTAINER}" test -d /etc/nginx/conf.d-extra >/dev/null 2>&1 \
    || die "容器内未挂载 /etc/nginx/conf.d-extra —— compose 文件还是旧版。
  请先执行：./scripts/deploy.sh"
  docker exec "${FRONTEND_CONTAINER}" test -d /var/www/acme >/dev/null 2>&1 \
    || die "容器内未挂载 /var/www/acme —— compose 文件还是旧版。
  请先执行：./scripts/deploy.sh"
}

# ---------------------------------------------------------------- 标识符
detect_identifier() {
  if [[ -n "${CERT_ID}" ]]; then echo "${CERT_ID}"; return 0; fi
  local ip=""
  for u in https://api.ipify.org https://ifconfig.me/ip https://ipinfo.io/ip; do
    ip="$(curl -fsS --max-time 5 "${u}" 2>/dev/null | tr -d '[:space:]' || true)"
    is_ipv4 "${ip}" && { echo "${ip}"; return 0; }
  done
  ip="$(ip route get 1.1.1.1 2>/dev/null | sed -n 's/.* src \([0-9.]*\).*/\1/p' | head -1 || true)"
  is_ipv4 "${ip}" && { echo "${ip}"; return 0; }
  return 1
}

# ---------------------------------------------------------------- ACME 前置校验
preflight_webroot() {
  mkdir -p "${WEBROOT}/.well-known/acme-challenge"
  local token="smartlife-selfcheck-$$" public_body local_body
  echo "ok" > "${WEBROOT}/.well-known/acme-challenge/${token}"

  public_body="$(curl -fsS --max-time 10 "http://${CERT_ID}/.well-known/acme-challenge/${token}" 2>/dev/null || true)"
  local_body="$(curl -fsS --max-time 10 "http://127.0.0.1/.well-known/acme-challenge/${token}" 2>/dev/null || true)"
  rm -f "${WEBROOT}/.well-known/acme-challenge/${token}"

  if [[ "${public_body}" == "ok" ]]; then
    log "ACME 校验目录自检通过：http://${CERT_ID}/.well-known/acme-challenge/ 可读"
    return 0
  fi
  if [[ "${local_body}" == "ok" ]]; then
    warn "本机 127.0.0.1 上的校验目录正常，但用公网地址 http://${CERT_ID}/ 取不到。
  可能是安全组没放行 80，或服务器访问自己的公网 IP 不走回环（NAT 回流）。
  Let's Encrypt 是从公网来校验的，若 80 未放行会签发失败，请先确认安全组。"
    return 0
  fi
  die "ACME 校验目录不通：http://${CERT_ID}/.well-known/acme-challenge/ 拿不到自检文件。
  排查顺序：
    1) 阿里云安全组 / 系统防火墙是否放行 80 端口
    2) 是否已执行 ./scripts/deploy.sh（旧编排没有把 ./acme-webroot 挂进容器）
    3) 前面是否套了 CDN/WAF，把 /.well-known/ 拦截或跳转了"
}

ensure_acme() {
  if [[ -x "${ACME}" ]]; then
    log "已安装 acme.sh：$("${ACME}" --version 2>/dev/null | head -1)"
    if "${ACME}" --upgrade --auto-upgrade >/dev/null 2>&1; then
      log "acme.sh 已升级到最新版：$("${ACME}" --version 2>/dev/null | head -1)"
    else
      warn "acme.sh 在线升级失败（不影响使用，但短期证书续签尽量用最新版）"
    fi
  else
    log "安装 acme.sh ..."
    curl -fsSL --max-time 90 https://get.acme.sh | sh -s email="${ACME_EMAIL}" </dev/null || true
  fi
  [[ -x "${ACME}" ]] || die "acme.sh 安装失败。国内网络可改用 git 方式：
    git clone --depth 1 https://github.com/acmesh-official/acme.sh.git /tmp/acme.sh
    cd /tmp/acme.sh && ./acme.sh --install -m ${ACME_EMAIL:-you@example.com}
  装好后重新执行本脚本。"

  # acme.sh 默认 CA 是 ZeroSSL，它不支持 IP 证书 → 必须锁定 Let's Encrypt
  "${ACME}" --set-default-ca --server "${LE_SERVER}" >/dev/null 2>&1 || true

  # 校验 acme.sh 是否支持证书 profile（IP 证书强制要求 shortlived profile）
  PROFILE_FLAG=""
  if "${ACME}" --help 2>&1 | grep -q -- '--cert-profile'; then
    PROFILE_FLAG="--cert-profile"
  elif "${ACME}" --help 2>&1 | grep -q -- '--certificate-profile'; then
    PROFILE_FLAG="--certificate-profile"
  fi
}

ensure_account() {
  local out
  local reg_args=(--register-account --server "${LE_SERVER}")
  [[ -n "${ACME_EMAIL}" ]] && reg_args+=(-m "${ACME_EMAIL}")
  if out="$("${ACME}" "${reg_args[@]}" </dev/null 2>&1)"; then
    log "Let's Encrypt 账号就绪${ACME_EMAIL:+（${ACME_EMAIL}）}"
    return 0
  fi
  if grep -qiE 'already|exist' <<<"${out}"; then
    log "Let's Encrypt 账号已存在"
    return 0
  fi
  err "${out}"
  if [[ -z "${ACME_EMAIL}" ]]; then
    die "注册账号失败。请在 ${ENV_FILE} 里加一行邮箱后重试：
    ACME_EMAIL=you@example.com"
  fi
  die "注册 Let's Encrypt 账号失败，请检查服务器能否访问 https://acme-v02.api.letsencrypt.org/"
}

# ---------------------------------------------------------------- 签发 / 安装
issue_cert() {
  local args=(--issue --server "${LE_SERVER}" -d "${CERT_ID}" --keylength ec-256 -w "${WEBROOT}")

  if is_ip "${CERT_ID}" || [[ "${SHORTLIVED}" == 1 ]]; then
    [[ -n "${PROFILE_FLAG}" ]] || die "当前 acme.sh 不支持证书 profile（${ACME} --help 里没有 --cert-profile）。
  IP 证书强制要求 shortlived profile，请先升级：${ACME} --upgrade"
    args+=("${PROFILE_FLAG}" shortlived --days "${RENEW_DAYS}")
    log "签发 IP 证书：${CERT_ID}（shortlived / 约 6 天有效 / 剩余 ${RENEW_DAYS} 天自动续）"
  else
    args+=(--days "${DOMAIN_RENEW_DAYS}")
    log "签发域名证书：${CERT_ID}（90 天有效 / 剩余 ${DOMAIN_RENEW_DAYS} 天自动续）"
  fi
  [[ "${STAGING}" == 1 ]] && { args+=(--staging); warn "使用 LE 测试环境签发：证书不会被浏览器信任，仅用于验证流程"; }
  [[ "${FORCE}" == 1 ]] && { args+=(--force); warn "强制重签会消耗 LE 的「同一标识集 5 张/7 天」额度，调试请优先用 --staging"; }

  local rc=0
  set +e
  "${ACME}" "${args[@]}" </dev/null
  rc=$?
  set -e
  # acme.sh 用退出码 2 表示"证书未到期，跳过续签"，这对幂等重跑是正常结果
  if [[ "${rc}" -ne 0 && "${rc}" -ne 2 ]]; then
    die "签发失败（acme.sh 退出码 ${rc}）。若上面提示网络超时，检查服务器能否访问 Let's Encrypt API。"
  fi
  [[ "${rc}" -eq 2 ]] && log "证书还在有效期内，跳过签发（如需强制重签加 --force）"
  return 0
}

install_cert() {
  local args=(--install-cert -d "${CERT_ID}" --ecc
    --key-file "${CERT_DIR}/privkey.pem"
    --fullchain-file "${CERT_DIR}/fullchain.pem"
    # 续签成功后 acme.sh 会自动执行这条命令，容器内 Nginx 重载即生效
    --reloadcmd "${DOCKER_BIN} exec ${FRONTEND_CONTAINER} nginx -s reload")
  [[ "${STAGING}" == 1 ]] && args+=(--staging)

  "${ACME}" "${args[@]}" </dev/null

  [[ -s "${CERT_DIR}/fullchain.pem" ]] || die "证书文件未生成：${CERT_DIR}/fullchain.pem"
  chmod 600 "${CERT_DIR}/privkey.pem"
  chmod 644 "${CERT_DIR}/fullchain.pem"

  # 校验证书真的覆盖了本次标识符，避免把错证书装进 Nginx 才发现
  if is_ip "${CERT_ID}"; then
    openssl x509 -in "${CERT_DIR}/fullchain.pem" -noout -checkip "${CERT_ID}" >/dev/null 2>&1 \
      || die "证书内容与 ${CERT_ID} 不匹配，已中止（不会改动 Nginx 配置）"
  else
    openssl x509 -in "${CERT_DIR}/fullchain.pem" -noout -checkhost "${CERT_ID}" >/dev/null 2>&1 \
      || die "证书内容与 ${CERT_ID} 不匹配，已中止（不会改动 Nginx 配置）"
  fi
  log "证书已安装到 ${CERT_DIR}/（容器内 /etc/nginx/certs/，只读挂载）"
}

# ---------------------------------------------------------------- 切换 Nginx 配置
# 先写 .tmp 再 mv：nginx 的 include 只认 *.conf，能保证"要么完全生效、要么完全没动"
activate_conf() { # $1=模版文件 $2=目标 *.conf
  local src="$1" dst="$2" backup="" rc=0
  mkdir -p "$(dirname "${dst}")"
  cp "${src}" "${dst}.tmp"
  if [[ -f "${dst}" ]]; then backup="$(mktemp)"; cp "${dst}" "${backup}"; fi
  mv "${dst}.tmp" "${dst}"

  set +e
  docker exec "${FRONTEND_CONTAINER}" nginx -t >/tmp/smartlife-nginx-t.log 2>&1
  rc=$?
  set -e
  if [[ "${rc}" -ne 0 ]]; then
    err "Nginx 配置校验未通过，正在回滚 ${dst}："
    sed 's/^/    /' /tmp/smartlife-nginx-t.log >&2
    if [[ -n "${backup}" ]]; then mv "${backup}" "${dst}"; else rm -f "${dst}"; fi
    die "已回滚，线上仍是改动前的状态。请把上面的报错发出来排查。"
  fi
  [[ -n "${backup}" ]] && rm -f "${backup}"
  return 0
}

reload_frontend() {
  docker exec "${FRONTEND_CONTAINER}" nginx -t >/dev/null 2>&1 || die "nginx -t 失败，未执行 reload"
  docker exec "${FRONTEND_CONTAINER}" nginx -s reload
  log "已在容器内执行 nginx -s reload"
}

verify_https() {
  local local_code public_code
  local_code="$(curl -k -sS -o /dev/null -w '%{http_code}' --max-time 10 "https://127.0.0.1/" 2>/dev/null || true)"
  [[ -n "${local_code}" && "${local_code}" != "000" ]] \
    || die "容器内 443 端口没起来（本机 https://127.0.0.1/ 无响应）。
  排查：docker exec ${FRONTEND_CONTAINER} nginx -t ；docker logs --tail=50 ${FRONTEND_CONTAINER}"
  log "本机 443 已就绪（https://127.0.0.1/ → HTTP ${local_code}）"

  public_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 15 "https://${CERT_ID}/" 2>/dev/null || true)"
  if [[ -n "${public_code}" && "${public_code}" != "000" ]]; then
    log "公网访问已就绪：https://${CERT_ID}/ → HTTP ${public_code}（证书被系统信任，否则 curl 会直接报错）"
  else
    warn "用公网地址 https://${CERT_ID}/ 探活失败。常见原因：
    1) 阿里云安全组没放行 ${HTTPS_PORT} 端口 —— 去控制台加上入方向 TCP ${HTTPS_PORT}
    2) 服务器访问自己的公网 IP 不走回环（NAT 回流），换手机/别的电脑访问即可确认
  本机 443 已经正常，配置本身没问题。"
  fi
}

# ---------------------------------------------------------------- 动作
do_enable() {
  require_root
  require_env_file
  require_running_stack
  require_new_deploy

  if [[ -z "${CERT_ID}" ]]; then
    CERT_ID="$(detect_identifier || true)"
    [[ -n "${CERT_ID}" ]] || die "识别公网 IP 失败，请显式指定：CERT_ID=1.2.3.4 $0"
    log "自动识别到公网 IP：${CERT_ID}"
  fi
  # IPv6 的 URL/校验文件路径写法与 IPv4 不同（要加方括号），本脚本只覆盖 IPv4，
  # 与其构造出错误 URL 让 LE 校验失败，不如直接说清楚。
  if is_ipv6 "${CERT_ID}"; then
    die "本脚本暂只支持 IPv4 地址（收到 ${CERT_ID}）。IPv6 需要给 URL 加方括号，请手动用 acme.sh 处理。"
  fi

  log "目标标识符：${CERT_ID}（$(is_ip "${CERT_ID}" && echo 'IP 证书 · 约 6 天有效期，必须自动续签' || echo '域名证书 · 90 天有效期')）"
  log "对外端口：HTTP ${HTTP_PORT} / HTTPS ${HTTPS_PORT}"

  preflight_webroot
  ensure_acme
  ensure_account
  mkdir -p "${CERT_DIR}"
  issue_cert
  install_cert

  # 顺序很重要：先立起 443，再让 80 跳过去，避免出现"能跳转但 443 还是空的"中间态
  activate_conf "${TEMPLATE_DIR}/https.conf" "${EXTRA_HTTP_DIR}/https.conf"
  activate_conf "${TEMPLATE_DIR}/http-to-https.conf" "${EXTRA_REDIRECT_DIR}/http.conf"
  reload_frontend
  verify_https

  if [[ "${VERIFY_RENEW}" == 1 ]]; then
    log "额外做一次强制续签，验证 6 天期证书的续签链路（会多消耗 1 次签发额度）..."
    FORCE=1
    issue_cert
    install_cert
    reload_frontend
    FORCE=0
    log "续签链路验证通过 ✅（acme.sh 的 --reloadcmd 会负责续签后自动 reload）"
  fi

  echo
  log "HTTPS 已启用 🎉"
  echo "  访问地址：https://${CERT_ID}/user/home"
  echo "  商家端：  https://${CERT_ID}/merchant/dashboard"
  echo "  管理端：  https://${CERT_ID}/admin/dashboard"
  echo
  echo "  证书有效期只有约 6 天，续签靠 acme.sh 的 crontab 自动完成，"
  echo "  它会同时执行 nginx -s reload。确认定时任务："
  echo "    ./scripts/https-enable.sh --status"
  echo "  出问题回滚成纯 HTTP（几秒完成，不影响数据）："
  echo "    ./scripts/https-enable.sh --disable"
}

do_disable() {
  require_root
  require_running_stack
  # 顺序与启用相反：先撤跳转，再撤 443，避免把用户跳到已经关掉的端口
  rm -f "${EXTRA_REDIRECT_DIR}/http.conf" "${EXTRA_HTTP_DIR}/https.conf"
  reload_frontend
  log "已回滚为纯 HTTP。证书与 acme.sh 定时任务保留，随时可 --enable 恢复。"
}

do_renew() {
  require_root
  [[ -x "${ACME}" ]] || die "未安装 acme.sh，请先执行 --enable"
  log "执行一次续签检查（未到期不会真续，不消耗限额）..."
  # acme.sh --cron 在「续签失败」时才返回非零；这里不能让它直接触发 set -e 中断，
  # 否则用户看不到下面 show_cert_files 的关键信息（比如证书其实已经过期了）。
  local rc=0
  set +e
  "${ACME}" --cron --home "${ACME_HOME}" </dev/null
  rc=$?
  set -e
  if [[ "${rc}" -ne 0 ]]; then
    err "acme.sh --cron 退出码 ${rc}：续签可能失败了。看上面的报错，常见原因是 80 端口被安全组拦、"
    err "或服务器访问不了 Let's Encrypt API。可先加 --staging 演练。"
  fi
  # 续签成功时 acme.sh 会自动跑 --reloadcmd；再兜底 reload 一次确保生效
  docker inspect -f '{{.State.Running}}' "${FRONTEND_CONTAINER}" 2>/dev/null | grep -qx true \
    && docker exec "${FRONTEND_CONTAINER}" nginx -s reload >/dev/null 2>&1 || true
  show_cert_files
}

case "${MODE}" in
  enable)  do_enable ;;
  disable) do_disable ;;
  status)  do_status ;;
  renew)   do_renew ;;
esac
