# =============================================================================
# 智联生活前端镜像（多阶段构建）
#   阶段 1：node 容器内 npm build（Vite 产物）
#   阶段 2：nginx 托管静态文件 + 反向代理后端
#
# 构建（在仓库根目录执行）：
#   docker build -f frontend/Dockerfile -t smartlife-frontend:2.0.0 ./frontend
# =============================================================================

# ---------- 阶段 1：构建 ----------
FROM node:20-alpine AS build
WORKDIR /app

# 优先拷贝依赖清单，利用 Docker 层缓存
COPY package.json package-lock.json* ./
# 国内服务器可取消注释下一行，使用淘宝 npm 源加速
# RUN npm config set registry https://registry.npmmirror.com
RUN npm install --no-audit --no-fund

COPY . .
RUN npm run build

# ---------- 阶段 2：运行 ----------
FROM nginx:1.25-alpine
LABEL maintainer="smartlife" \
      description="智联生活三端平台前端（Nginx + 反向代理）"

ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 站点配置：history 路由回退 + /api 反向代理 + WebSocket 升级
COPY nginx.conf /etc/nginx/conf.d/default.conf
# 80 与 443 共用的站点配置片段（启用 HTTPS 后 443 也要 include 这一份）
COPY site.inc /etc/nginx/snippets/site.inc
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80 443

# 探活打 /index.html：精确匹配 location，启用 HTTPS 后不会被 301 跳走
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1/index.html || exit 1

CMD ["nginx", "-g", "daemon off;"]
