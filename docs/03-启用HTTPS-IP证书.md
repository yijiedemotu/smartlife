# 03 · 启用 HTTPS（无域名也能签，免费 IP 证书）

> 目标：把 `http://<公网IP>/` 升级为 `https://<公网IP>/`，浏览器不报"不安全"，并且**长期自动续签、不用管**。
>
> 前置：`./scripts/deploy.sh` 已经成功跑过一次（本机无域名、无 ICP 备案也能做）。

---

## 一、先回答最关键的问题：能一直续签、长期用吗？

**技术结论：能。免费、可无限期自动续签。** 依据如下：

| 关注点 | 实际情况 |
|---|---|
| 谁能签 IP 证书 | Let's Encrypt 自 **2026-01-15 起 GA**，支持 IPv4/IPv6，免费（[官方公告](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability)） |
| 有效期 | **160 小时（约 6.6 天）**，且 IP 证书**强制**用 `shortlived` profile，不能签 90 天 |
| 续签次数会不会超限 | 不会。IP 按"整个 IPv4 地址"算一个 registered domain，限额是 **50 张 / 7 天**；同一标识集另有 **5 张 / 7 天**上限。本方案剩余 3 天就续，约 **2 张 / 周**，余量充足（[速率限额文档](https://letsencrypt.org/docs/rate-limits/)） |
| 还能更省吗 | 能。支持 ARI（ACME Renewal Info）的客户端走 ARI 续签**完全不计限额**，acme.sh 会在自动升级时用上 |
| 会不会哪天不发了 | Let's Encrypt 的短期证书与 IP 证书已是正式服务，不属于实验特性；但**6 天期意味着你对 LE 可用性依赖很紧**，续签链路不能断 |

**但"一直用"还取决于三件事，脚本已按最坏情况处理：**

1. **续签自动化不能断。** 断了第 7 天全站变红。→ acme.sh 安装时写入 crontab，续签成功后自动 `docker exec smartlife-frontend nginx -s reload`；`--status` 可随时看剩余天数。
2. **浏览器/微信内置浏览器是否信任 IP 证书，要自己实测一遍。** 这一步没有权威结论可抄，请按第六节的自测清单在 Chrome / 手机 / 微信里各开一次。
3. **阿里云按域名 + SNI 做备案拦截，裸 IP 直连 80/443 目前不受影响**（[V2EX 实测讨论](https://global.v2ex.co/t/1180965)：多人反馈阿里云 IP+443 长期可用，检测的是域名而非服务器）。但这是策略问题、不是技术保证，将来收紧也只能换域名。

**一句话建议**：现在（无域名、要快速可验收）用 IP 证书；如果这个站要长期对外、写进简历给面试官看，仍然推荐"域名 + ICP 备案 + 90 天证书"，本脚本已经预留了域名迁移路径（第五节，一条命令）。

---

## 二、方案对比

| 方案 | 是否需要域名/备案 | 有效期 | 浏览器是否信任 | 适用场景 |
|---|---|---|---|---|
| **A. Let's Encrypt IP 证书（本方案）** | 都不要 | 约 6 天，自动续 | 是（需实测，见第六节） | 当前阶段：无域名、先跑起来 |
| B. 域名 + Let's Encrypt | 域名 + **国内服务器必须 ICP 备案** | 90 天 | 是 | 长期对外、正式项目 |
| C. 自签证书 | 都不要 | 自定义 | **否**，浏览器报"不安全" | 仅内网/答辩演示点"继续访问" |

---

## 三、操作步骤（服务器上执行，约 2 分钟）

```bash
cd /opt/smartlife

# 1) 先把新版编排与前端镜像发上去（会重建前端镜像：443 端口、证书挂载、共用配置）
./scripts/deploy.sh

# 2) 一键启用 HTTPS：自动识别公网 IP → 签发 → 安装 → 挂 443 站点 → 80 跳 443
./scripts/https-enable.sh --verify-renew
```

`--verify-renew` 会在签发后再强制续签一次，**当场验证"6 天期证书的续签链路真的通"**（多消耗 1 次签发额度，上限 5 次/7 天，首次部署值得花）。不想多消耗就省略该参数。

完成后访问：

```
https://<你的公网IP>/user/home
https://<你的公网IP>/merchant/dashboard
https://<你的公网IP>/admin/dashboard
```

> 别忘了**阿里云安全组放行 443**（入方向 TCP 443）。脚本探活失败时会明确提示这一点。

> ⚠️ **必须跑 `./scripts/deploy.sh`（重建前端镜像），不能只 `docker compose up -d`。**
> 本版把 Nginx 站点配置抽成了共用片段 `frontend/site.inc`，`nginx.conf` 会 include 它。
> 如果沿用旧的 `smartlife-frontend` 镜像（镜像里没有这个文件），Nginx 会直接启动失败。
> `deploy.sh` 里已加"用新镜像跑 `nginx -t`"的预检，配置有问题会中止发布而不是把站点打挂。

### 脚本做了什么

```
acme.sh --issue -d <公网IP> -w ./acme-webroot --cert-profile shortlived --days 3
   ↓ 证书安装到宿主 ./certs/（容器内只读挂载 /etc/nginx/certs/）
   ↓ 写入 ./nginx-extra/http/https.conf（443 站点）
   ↓ 写入 ./nginx-extra/redirect/http.conf（页面请求 301 跳 HTTPS）
   ↓ docker exec smartlife-frontend nginx -s reload
```

因此**续签不需要重建镜像、不需要重启容器**：acme.sh 覆盖 `./certs/` 两个文件后 reload 即可。

---

## 四、常用命令与回滚

```bash
./scripts/https-enable.sh --status      # 证书剩余天数、crontab、端口、探活
./scripts/https-enable.sh --renew       # 手动跑一次续签检查（未到期不会真续，不浪费额度）
./scripts/https-enable.sh --disable     # 回滚为纯 HTTP（证书和定时任务保留）
./scripts/https-enable.sh --staging     # 用 LE 测试环境演练（证书不被信任，仅验证流程）
```

排错提示：

- **探活失败但日志正常**：99% 是安全组没放行 443，或服务器访问自己的公网 IP 不走回环（换手机访问确认）。
- **签发失败**：看报错，若是网络超时说明服务器连不上 Let's Encrypt API；反复失败别硬刷，加 `--staging` 调试。
- **`没有 --cert-profile`**：acme.sh 版本太老，`/root/.acme.sh/acme.sh --upgrade` 后再试。
- **证书过期了**：`crontab -l | grep acme.sh` 看定时任务是否还在，`--renew` 手动补一次。

---

## 五、以后有域名了怎么切（一条命令）

备案通过、域名解析到本机后：

```bash
CERT_ID=life.example.com ./scripts/https-enable.sh
```

脚本会：识别出这是域名 → 自动改用 90 天期证书（`--days 30` 续签）→ 签发并替换 443 站点证书 → reload。
旧 IP 证书的 acme.sh 记录留着不影响，验证没问题后可以 `acme.sh --remove -d <旧IP>` 清掉。

> 注意：域名解析到国内 ECS，**未备案时 80/443 会被云厂商拦**（裸 IP 不受影响，这也是 IP 证书方案能立刻用的原因）。

---

## 六、上线后请自己实测这三件事（重要）

IP 证书是 2025 年底才放开的新东西，浏览器/微信的支持情况请以**你的真实环境**为准，不要只看文档：

1. **桌面 Chrome / Edge** 打开 `https://<公网IP>/user/home`，地址栏应为锁头、无"不安全"提示。
2. **手机浏览器**（Android Chrome / iOS Safari）再开一次。
3. **微信内置浏览器**开一次（如果链接要在微信里传播）。

如果出现证书警告，说明该环境的根证书/策略还不认 IP 证书。此时的选择：

- 只自己看/答辩演示 → 点"继续访问"即可，或退回 `--disable` 用 HTTP；
- 要给外人稳定访问 → 走方案 B（域名 + 备案）。

---

## 七、实现细节（想改配置时看这里）

```
frontend/nginx.conf            80 入口：ACME 校验目录 + include 共用配置 + 可选跳转
frontend/site.inc              80/443 共用的站点功能（静态资源 / /api 反代 / WebSocket）
scripts/templates/https.conf   443 站点模版（TLS 参数 + include site.inc）
scripts/templates/http-to-https.conf   80→443 跳转片段（`return 301`）
scripts/https-enable.sh        签发 / 续签 / 回滚 / 自检
```

几个刻意的设计决定：

- **80 与 443 共用 `site.inc`**：避免"HTTP 正常、HTTPS 某个接口 404"这类双份配置漂移。
- **跳转只对页面生效**：`/\.well-known/acme-challenge/`（验签）、`/assets/`、`/api/` 各有独立 location，不会被 301 带走；`/index.html` 是精确匹配也不跳，所以容器健康检查在两种模式下都稳定返回 200。
- **跳转片段单独一个文件**：`--disable` 只删这一个文件就能恢复 HTTP，且 80 端口始终可用（证书万一过期，HTTP 仍能进站排查）。
- **不开 HSTS**：IP 访问时浏览器本就忽略该头（RFC 6797 只针对域名），而一旦换成域名，长 max-age 会在续签失败那几天把用户锁死在打不开的 HTTPS 上。
- **不开 OCSP stapling**：6 天期证书不提供 OCSP/CRL 地址（LE 官方说明），开了只会刷错误日志。
- **发布前 `nginx -t` 闸门**：`deploy.sh` 会用新镜像预检配置，配置写错就中止发布，线上老容器继续跑。
