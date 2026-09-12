# =============================================================================
# 智联生活 · 站点共享配置（Nginx server 上下文片段）
#
# 为什么单独抽一个文件：
#   80（HTTP）与 443（HTTPS）两个 server 需要完全一致的静态资源 / API 反代 /
#   WebSocket 行为。若各写一份，日后加一个 location 很容易只改一边，导致
#   "HTTP 正常、HTTPS 上某个接口 404"。所以两侧都 include 本文件。
#
# 被谁 include：
#   1) frontend/nginx.conf            → 80 端口 server
#   2) nginx-extra/http/https.conf    → 443 端口 server（scripts/https-enable.sh 安装）
# 镜像内路径：/etc/nginx/snippets/site.inc
#
# 刻意不包含 `location /`（SPA 回退）：
#   80 端口版本要在此挂 http→https 跳转片段，而 443 版本不能挂（否则无限跳转），
#   因此那两行留在各自的 server 里。
# =============================================================================

root /usr/share/nginx/html;
index index.html;

charset utf-8;
client_max_body_size 20m;

# ---------- 访问日志 ----------
access_log /var/log/nginx/access.log;
error_log  /var/log/nginx/error.log warn;

# ---------- gzip 压缩：显著降低 JS/CSS 传输体积 ----------
gzip on;
gzip_comp_level 5;
gzip_min_length 1024;
gzip_proxied any;
gzip_vary on;
gzip_types text/plain text/css application/json application/javascript
           application/x-javascript text/xml application/xml image/svg+xml;

# ---------- 静态资源长缓存（文件名带 hash，可放心长缓存） ----------
location /assets/ {
    expires 1y;
    add_header Cache-Control "public, immutable";
    try_files $uri =404;
}

# ---------- 入口 HTML 不缓存，保证发版后立即生效 ----------
# 精确匹配，优先于 `location /`：因此即使开了 http→https 跳转，容器健康检查
# 打 /index.html 也不会被 301 带走。
location = /index.html {
    add_header Cache-Control "no-store, no-cache, must-revalidate";
}

# ---------- API 反向代理 ----------
# 注意：后端 context-path=/api，location 与 proxy_pass 都带 /api 前缀
location /api/ {
    proxy_pass http://backend:8080/api/;
    proxy_http_version 1.1;
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    # $scheme 随请求而来：80 入口是 http、443 入口是 https，无需分别配置
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 10s;
    proxy_send_timeout    60s;
    proxy_read_timeout    60s;
    # Token 滑动续期响应头需要透传
    proxy_pass_header X-Auth-Refresh;
}

# ---------- WebSocket 升级（订单/秒杀实时推送） ----------
# HTTPS 下浏览器会自动用 wss://，前端 frontend/src/utils/ws.js 按
# location.protocol 判断，这里只要把 Upgrade 头透传即可。
location /api/ws {
    proxy_pass http://backend:8080/api/ws;
    proxy_http_version 1.1;
    proxy_set_header Upgrade    $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host       $host;
    proxy_set_header X-Real-IP  $remote_addr;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}

# ---------- Knife4j 接口文档（生产环境可按需关闭/加访问控制） ----------
location /api/doc.html {
    proxy_pass http://backend:8080/api/doc.html;
    proxy_set_header Host $host;
}

# ---------- 基础安全响应头 ----------
add_header X-Content-Type-Options nosniff;
add_header X-Frame-Options SAMEORIGIN;
add_header Referrer-Policy strict-origin-when-cross-origin;
