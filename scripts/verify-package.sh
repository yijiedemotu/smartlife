#!/usr/bin/env bash
# =============================================================================
# 智联生活 · 部署包完整性校验（在服务器上解压后执行）
#
# 用法：
#   bash scripts/verify-package.sh          # 只检查，不改动任何文件
#   bash scripts/verify-package.sh --fix    # 检查并自动修复可修的问题
#                                           # （补可执行位、清 CRLF、补 settings.xml）
#
# 退出码：0 = 全部通过；1 = 有阻断项未通过
# =============================================================================
set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${PROJECT_DIR}"

FIX=0
[[ "${1:-}" == "--fix" ]] && FIX=1

PASS=0
FAIL=0
WARN=0

ok()   { echo "  [PASS] $*"; PASS=$((PASS+1)); }
bad()  { echo "  [FAIL] $*"; FAIL=$((FAIL+1)); }
warn() { echo "  [WARN] $*"; WARN=$((WARN+1)); }

echo "==================================================================="
echo " 智联生活 · 部署包校验   目录: ${PROJECT_DIR}"
echo " 模式: $([[ $FIX -eq 1 ]] && echo '检查并自动修复' || echo '仅检查')"
echo "==================================================================="

# ------------------------------------------------------------------ 1. 关键文件
echo
echo "=== 1. 关键文件清单 ==="
# CORE：无论哪种包形态（完整源码包 / 运行版最小包）都必须存在
CORE=(
  .env.prod.example
  backend/.dockerignore
  frontend/nginx.conf
  sql/init.sql
  scripts/deploy.sh
  scripts/backup-db.sh
  scripts/restore-db.sh
)
# 至少要有一种编排文件 + 对应的 Dockerfile
if [[ -f docker-compose.runtime.yml ]]; then
  CORE+=(docker-compose.runtime.yml backend/Dockerfile.runtime frontend/Dockerfile.runtime)
else
  CORE+=(docker-compose.prod.yml backend/Dockerfile frontend/Dockerfile)
fi

# OPTIONAL：完整源码包才有；存在就一并校验，不存在不判失败（运行版包不含源码）
OPTIONAL=(
  docker-compose.prod.yml
  docker-compose.yml
  .gitattributes
  backend/Dockerfile
  backend/Dockerfile.runtime
  backend/pom.xml
  backend/settings.xml
  frontend/Dockerfile
  frontend/Dockerfile.runtime
  frontend/package.json
  sql/README.md
  sql/manual/upgrade_v2_three_end.sql
  backend/src/main/java/com/smartlife/SmartLifeApplication.java
  backend/src/main/java/com/smartlife/common/RoleConstants.java
  backend/src/main/java/com/smartlife/security/RoleGuard.java
  backend/src/main/java/com/smartlife/service/MerchantService.java
  backend/src/main/java/com/smartlife/controller/MerchantController.java
  frontend/src/router/index.js
  frontend/src/views/merchant/Dashboard.vue
  frontend/src/views/admin/Applies.vue
)

MISSING=0
echo "  --- 核心文件（必须有）---"
for f in "${CORE[@]}"; do
  if [[ -f "$f" ]]; then
    printf '  [ OK ] %-58s %s\n' "$f" "$(du -h "$f" | cut -f1)"
  else
    printf '  [MISS] %-58s\n' "$f"
    MISSING=$((MISSING+1))
  fi
done

PRESENT=0
OPT_MISSING=""
for f in "${OPTIONAL[@]}"; do
  [[ -f "$f" ]] && PRESENT=$((PRESENT+1)) || OPT_MISSING="$OPT_MISSING $f"
done
echo "  --- 源码类文件（${PRESENT} 个存在，存在即校验）---"
for f in "${OPTIONAL[@]}"; do
  [[ -f "$f" ]] && printf '  [ OK ] %-58s %s\n' "$f" "$(du -h "$f" | cut -f1)"
done

if [[ $MISSING -eq 0 ]]; then
  ok "核心文件全部存在（${#CORE[@]} 项）"
  # 判断包形态，便于后续分支逻辑
  if [[ -f backend/pom.xml && -f frontend/package.json ]]; then
    PACKAGE_KIND="full"
    ok "识别为【完整源码包】（含 pom.xml 与 package.json，可在服务器内构建）"
  else
    PACKAGE_KIND="runtime"
    ok "识别为【运行版最小包】（不含源码，依赖预编译产物 + 服务器已有镜像）"
  fi
else
  bad "缺失 ${MISSING} 个核心文件，压缩包不完整"
fi

# 不应出现的目录（打包时误带会把构建拖慢甚至失败）
echo
echo "=== 2. 产物目录检查（按包形态区分）==="
PKG_KIND="${PACKAGE_KIND:-runtime}"
for d in node_modules .git .idea; do
  if [[ -e "$d" ]]; then warn "存在 ${d}/（打包时应排除，会让构建变慢）"; else ok "无 ${d}/"; fi
done
if [[ -d frontend/node_modules ]]; then warn "存在 frontend/node_modules/（应排除）"; fi

if [[ "$PKG_KIND" == "runtime" ]]; then
  # 运行版最小包：target/ 与 dist/ 是刻意提供的（各只含预编译产物）
  if [[ -f backend/target/smartlife-backend-1.0.0.jar ]]; then
    target_files=$(find backend/target -type f | wc -l)
    ok "backend/target/ 为刻意提供（${target_files} 个文件，应只有 jar）"
    [[ "$target_files" -gt 2 ]] && warn "backend/target/ 文件偏多（${target_files} 个），建议只保留 jar"
  fi
  if [[ -f frontend/dist/index.html ]]; then
    ok "frontend/dist/ 为刻意提供（$(find frontend/dist -type f | wc -l) 个文件）"
  fi
else
  for d in target dist; do
    if [[ -e "$d" ]]; then warn "存在 ${d}/（完整源码包应排除，会让构建变慢）"; else ok "无 ${d}/"; fi
  done
fi

# ------------------------------------------------------------------ 3. 换行格式
echo
echo "=== 3. 部署关键文件换行格式（CRLF 会让 sh 报 bad interpreter）==="
CRLF_FILES=()
for f in scripts/*.sh backend/Dockerfile frontend/Dockerfile frontend/nginx.conf backend/settings.xml .env.prod.example .gitattributes; do
  [[ -f "$f" ]] || continue
  if grep -qU $'\r' "$f" 2>/dev/null; then
    CRLF_FILES+=("$f")
  fi
done
if [[ ${#CRLF_FILES[@]} -eq 0 ]]; then
  ok "全部为 LF，无 CRLF"
else
  for f in "${CRLF_FILES[@]}"; do bad "含 CRLF: $f"; done
  if [[ $FIX -eq 1 ]]; then
    sed -i 's/\r$//' "${CRLF_FILES[@]}" && ok "已自动转换为 LF"
    # 重新确认
    STILL=0
    for f in "${CRLF_FILES[@]}"; do grep -qU $'\r' "$f" && STILL=$((STILL+1)); done
    [[ $STILL -eq 0 ]] && ok "修复后二次确认通过" || bad "仍有 ${STILL} 个文件含 CRLF"
  else
    echo "        → 可用 --fix 自动修复，或执行: sed -i 's/\\r\$//' ${CRLF_FILES[*]}"
  fi
fi

# ------------------------------------------------------------------ 4. 可执行位
echo
echo "=== 4. 脚本可执行权限 ==="
NOEXEC=()
for f in scripts/*.sh; do
  [[ -x "$f" ]] || NOEXEC+=("$f")
done
if [[ ${#NOEXEC[@]} -eq 0 ]]; then
  ok "scripts/*.sh 均有可执行位"
else
  # Windows 上 core.filemode=false，打包时拿不到执行位 —— 这是最常见的情况，必须修好才算通过
  for f in "${NOEXEC[@]}"; do echo "  [待修] 缺可执行位: $f"; done
  if [[ $FIX -eq 1 ]]; then
    chmod +x scripts/*.sh
    REMAIN=0
    for f in scripts/*.sh; do [[ -x "$f" ]] || REMAIN=$((REMAIN+1)); done
    [[ $REMAIN -eq 0 ]] && ok "已补上可执行位（${#NOEXEC[@]} 个文件）" || bad "仍有 ${REMAIN} 个脚本不可执行"
  else
    bad "有 ${#NOEXEC[@]} 个脚本缺可执行位，./scripts/deploy.sh 会报 Permission denied"
    echo "        → 修复: bash scripts/verify-package.sh --fix   （或手动 chmod +x scripts/*.sh）"
  fi
fi

# ------------------------------------------------------------------ 5. 构建上下文
echo
echo "=== 5. 构建上下文健康度（上次部署失败的根因）==="
if [[ -f backend/.dockerignore ]] && grep -qE '^[[:space:]]*settings\.xml[[:space:]]*$' backend/.dockerignore; then
  bad "backend/.dockerignore 排除了 settings.xml，构建会报 \"/settings.xml\": not found"
  if [[ $FIX -eq 1 ]]; then
    sed -i '/^[[:space:]]*settings\.xml[[:space:]]*$/d' backend/.dockerignore && ok "已删除该排除行"
  fi
else
  ok "backend/.dockerignore 未排除 settings.xml"
fi

if [[ -f backend/settings.xml ]]; then
  if grep -q 'aliyun' backend/settings.xml; then
    ok "backend/settings.xml 存在且配置了阿里云 Maven 源"
  else
    warn "backend/settings.xml 存在但未找到 aliyun 源（构建会走默认源，可能较慢）"
  fi
else
  warn "backend/settings.xml 缺失（新版 Dockerfile 会自动生成兜底配置，不阻断构建）"
  if [[ $FIX -eq 1 ]]; then
    printf '%s\n' \
      '<?xml version="1.0" encoding="UTF-8"?>' \
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">' \
      '  <mirrors>' \
      '    <mirror>' \
      '      <id>aliyun-central</id>' \
      '      <url>https://maven.aliyun.com/repository/public</url>' \
      '      <mirrorOf>central</mirrorOf>' \
      '    </mirror>' \
      '  </mirrors>' \
      '</settings>' > backend/settings.xml && ok "已生成 backend/settings.xml"
  fi
fi

# ---- 运行版（Dockerfile.runtime）的前提：target/ 与 dist/ 不能被 .dockerignore 排除 ----
# backend/.dockerignore 若含 target，Dockerfile.runtime 的 COPY target/*.jar 必然失败（真实踩过的坑）
for pair in "backend:target" "frontend:dist"; do
  d="${pair%%:*}"; pat="${pair##*:}"
  di="${d}/.dockerignore"
  if [[ -f "$di" ]] && grep -qE "^[[:space:]]*${pat}[[:space:]]*$" "$di"; then
    bad "${di} 排除了 ${pat}/，Dockerfile.runtime 的 COPY ${pat} 会失败"
    if [[ $FIX -eq 1 ]]; then
      sed -i "/^[[:space:]]*${pat}[[:space:]]*$/d" "$di" && ok "已从 ${di} 移除 ${pat}"
    fi
  else
    ok "${di} 未排除 ${pat}/（运行版可构建）"
  fi
done

# ---- 运行版预编译产物是否随包提供 ----
if [[ -f backend/Dockerfile.runtime ]]; then
  if [[ -f backend/target/smartlife-backend-1.0.0.jar ]]; then
    ok "jar 已随包提供：backend/target/smartlife-backend-1.0.0.jar ($(du -h backend/target/smartlife-backend-1.0.0.jar | cut -f1))"
  else
    warn "jar 不在包内（backend/target/smartlife-backend-1.0.0.jar）—— 运行版后端镜像会构建失败"
  fi
fi
if [[ -f frontend/Dockerfile.runtime ]]; then
  if [[ -f frontend/dist/index.html ]]; then
    ok "dist 已随包提供：frontend/dist/（$(find frontend/dist -type f | wc -l) 个文件）"
  else
    warn "dist 不在包内（frontend/dist/）—— 运行版前端镜像会构建失败"
  fi
fi

# 逐个校验 Dockerfile 的 COPY 源是否真的在上下文里（排除 .dockerignore 之后）
check_copy_sources() {
  local ctx="$1" df="$2"
  [[ -f "$df" ]] || return 0
  local src
  while read -r src; do
    [[ -n "$src" ]] || continue
    case "$src" in
      --from=*) continue ;;
      .|./|*) continue ;;
    esac
    if [[ "$src" == *"["* ]]; then
      ok "${ctx}: COPY 源 ${src} 为可选写法（缺失时由 RUN 兜底）"
      continue
    fi
    if [[ -e "${ctx}/${src}" ]]; then
      ok "${ctx}: COPY 源 ${src} 存在"
    else
      bad "${ctx}: COPY 源 ${src} 在构建上下文中不存在"
    fi
  done < <(grep -E '^[[:space:]]*COPY ' "$df" | awk '{print $2}')
}
check_copy_sources backend backend/Dockerfile
check_copy_sources frontend frontend/Dockerfile

# ------------------------------------------------------------------ 6. deploy.sh 逻辑
echo
echo "=== 6. deploy.sh 占位符校验逻辑（注释行不应触发误报）==="
if [[ -f scripts/deploy.sh ]]; then
  if grep -q 'PLACEHOLDER_RE' scripts/deploy.sh; then
    ok "含 PLACEHOLDER_RE（新版：只匹配未注释的赋值行）"
  else
    warn "为旧版逻辑（用裸 grep CHANGE_ME，会因模板注释行误报）"
  fi
  if bash -n scripts/deploy.sh 2>/dev/null; then
    ok "bash 语法检查通过（bash -n）"
  else
    bad "bash 语法检查失败，请运行: bash -n scripts/deploy.sh"
  fi
fi

# ------------------------------------------------------------------ 7. 环境变量
echo
echo "=== 7. .env.prod 状态 ==="
if [[ -f .env.prod ]]; then
  if grep -qE '^[ \t]*[A-Za-z_][A-Za-z0-9_]*=.*CHANGE_ME' .env.prod; then
    warn ".env.prod 存在但仍有未替换的占位密码（阶段 3 处理后即可）"
  else
    ok ".env.prod 存在且无占位符残留"
  fi
  PERM=$(stat -c '%a' .env.prod 2>/dev/null || echo '?')
  [[ "$PERM" == "600" ]] && ok ".env.prod 权限 600" || warn ".env.prod 权限为 ${PERM}（建议 chmod 600）"
else
  warn ".env.prod 尚未创建（阶段 3 会从 .env.prod.example 生成）"
fi

# ------------------------------------------------------------------ 8. Docker 环境
echo
echo "=== 8. 运行环境（在服务器上这些必须全部可用）==="
DOCKER_OK=1
if command -v docker >/dev/null 2>&1; then
  ok "docker 已安装：$(docker --version)"
  if docker compose version >/dev/null 2>&1; then
    ok "compose 插件可用：$(docker compose version)"
  else
    bad "docker compose 插件不可用（apt-get install -y docker-compose-plugin）"
    DOCKER_OK=0
  fi
  if docker info >/dev/null 2>&1; then
    ok "Docker 守护进程可访问"
  else
    bad "无法访问 Docker 守护进程（systemctl status docker）"
    DOCKER_OK=0
  fi
else
  # 本机/CI 上校验部署包时没有 docker 属正常，不作为「包不完整」的阻断项
  warn '未检测到 docker：若这是服务器，请先安装 Docker（见 docs/01-从零部署-上.md 阶段 1）'
  warn '  —— 该提示不影响「部署包完整性」判定'
  DOCKER_OK=0
fi

# ------------------------------------------------------------------ 汇总
echo
echo "==================================================================="
echo " 结果：PASS=${PASS}  FAIL=${FAIL}  WARN=${WARN}"
if [[ $FAIL -eq 0 ]]; then
  if [[ $DOCKER_OK -eq 1 ]]; then
    echo ' ✅ 部署包与运行环境校验全部通过，可以进入下一步（构建镜像）'
  else
    echo ' ✅ 部署包校验通过（运行环境请按上面的 WARN 处理）'
  fi
  echo "==================================================================="
  exit 0
else
  echo " ❌ 有 ${FAIL} 项未通过，请按上面的 [FAIL] 处理后重跑"
  [[ $FIX -eq 0 ]] && echo "    提示：bash scripts/verify-package.sh --fix 可自动修复大部分问题"
  echo "==================================================================="
  exit 1
fi
