# 🍜 智联生活 · 三端本地生活与社交聚合平台

> **用户端 / 商家端 / 管理端**三端一体的本地生活平台：`浏览 → 点餐 → 秒杀 → 匹配 → 履约 → 平台治理`
> 单体 Spring Boot 2.7 + Vue3 单应用多端 + MySQL/Redis/RabbitMQ，支持**阿里云单机 Docker 一键部署**。

[![Java](https://img.shields.io/badge/Java-17-orange)]() [![SpringBoot](https://img.shields.io/badge/SpringBoot-2.7.18-green)]() [![Vue](https://img.shields.io/badge/Vue-3.4-42b883)]() [![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)]() [![Redis](https://img.shields.io/badge/Redis-7-red)]() [![Docker](https://img.shields.io/badge/Docker-Compose-2496ed)]()

---

## 一、三端功能全景

| 端 | 角色编码 | 核心能力 |
|---|---|---|
| 👤 **用户端** | `role=1` | 店铺浏览、附近店铺（Redis GEO）、购物车、下单支付、订单追踪、**秒杀券抢购**、我的券包、**饭搭子匹配**（编辑距离 + TopN）、私信、每日签到、个人中心 |
| 🏪 **商家端** | `role=2` | 入驻申请、店铺资料与营业开关、**经营看板**（订单/GMV 趋势、订单状态分布、热销 Top5、券核销概览）、订单履约（接单→配送→完成/取消）、商品管理、优惠券管理（含秒杀券时间窗）、**到店券核销** |
| 🛡️ **管理端** | `role=3` | **入驻审核**（通过即自动开店）、店铺治理（停业整顿/恢复/删除）、全平台订单巡检与客服兜底、商品与优惠券违规治理、账号封禁/解封、类目字典维护、**平台经营看板**、**操作审计日志** |

### 三端如何真正"分开"，而不是只分页面

| 隔离维度 | 实现 |
|---|---|
| **账号与登录** | 同一张 `tb_user` 表用 `role` 区分；登录接口返回 `homePath`，前端据此落到对应端 |
| **接口鉴权** | `AuthInterceptor` 按路径前缀鉴权：`/merchant/**` 仅商家与管理员、`/admin/**` 仅管理员、`/cart /mate /chat /sign` 为用户端专属；`RoleGuard` 在 Service 层二次校验 |
| **数据隔离** | 一个商家绑定一个店铺（`tb_shop.merchant_id` **唯一索引**）；商家端所有查询强制带上自己的 `shopId`，写操作前 `MerchantService.assertShopOwnership()` 校验归属，且**服务端强制重写 shopId**，前端伪造也会被纠正 |
| **实时消息隔离** | `WsSessionManager` 按角色分桶（user / merchant / admin）；新订单只推送给**订单所属店铺**的商家，A 店的新单不会打扰 B 店 |
| **平台治理** | 商家入驻需管理员审核（`tb_merchant_apply`）；审核通过才创建并开通店铺；关键动作全部落 `tb_audit_log` 留痕 |

---

## 二、技术栈

- **后端**：Java 17 · Spring Boot 2.7.18 · MyBatis-Plus 3.5 · MySQL 8 · HikariCP
- **中间件**：Redis 7（多级缓存 / Lua 原子扣减 / GEO / BitMap / HyperLogLog / Redisson 锁）、RabbitMQ 3.12（秒杀削峰 + 死信队列兜底）
- **安全与规范**：JWT（jjwt）二级拦截器链 + ThreadLocal 上下文 · 统一响应体与全局异常 · Knife4j(OpenAPI)
- **前端**：Vue 3 · Vite 5 · Element Plus · Pinia · Vue Router · Axios · ECharts · 原生 WebSocket
- **部署**：Dockerfile ×2（后端容器内 Maven 多阶段构建）· docker-compose ×3 · Nginx（history 路由 + API 反代 + WS 升级）

---

## 三、架构总览

```
┌─────────────────────────── Nginx (frontend 容器, 唯一对外 80) ──────────────────────────┐
│  静态托管 Vue3 单应用（/user/** · /merchant/** · /admin/** 三套布局与路由守卫）            │
│  /api/** 反向代理 → backend:8080        /api/ws WebSocket 升级                          │
└───────────────────────────────────┬────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────▼────────────────┐
                    │   Spring Boot (context-path=/api)│
                    │  AuthInterceptor  三端路径鉴权    │
                    │  RoleGuard        Service 二次校验 │
                    │  MerchantService  店铺归属 + 入驻审核 │
                    │                                  │
                    │  /merchant/**   商家经营          │
                    │  /admin/**      平台治理          │
                    │  其余           用户端业务         │
                    └───┬──────────┬──────────┬────────┘
                        │          │          │
                 ┌──────▼───┐ ┌────▼────┐ ┌───▼─────────┐
                 │  MySQL 8 │ │ Redis 7 │ │ RabbitMQ    │
                 │ 三端数据  │ │ 缓存/GEO │ │ 秒杀削峰+死信 │
                 │ 审核/审计 │ │ 秒杀/GEO │ │             │
                 └──────────┘ └─────────┘ └─────────────┘
```

---

## 四、快速开始（本地开发）

### 方式 A：Docker 起中间件 + IDE 跑后端 + Vite 跑前端（推荐）

```bash
# 1) 起基础设施（MySQL8 + Redis7 + RabbitMQ），首次自动执行 sql/init.sql 建库+三端种子
docker compose up -d

# 2) 后端（默认连 localhost 中间件，MySQL 密码 123456）
cd backend && mvn clean package -DskipTests
java -jar target/smartlife-backend-1.0.0.jar
#   接口文档 http://localhost:8080/api/doc.html

# 3) 前端
cd frontend && npm install && npm run dev
#   http://localhost:5173
```

### 方式 B：全栈一条命令（本地演示）

```bash
docker compose -f docker-compose.full.yml up -d --build
# 前端 http://localhost:5173    后端 http://localhost:8080/api/doc.html
```

### 方式 C：完全手动（无 Docker）

1. 本机准备 MySQL 8 / Redis 7 / RabbitMQ 3.12；
2. 执行 `sql/init.sql`（幂等，可重复执行）；
3. `cd backend && mvn spring-boot:run`（可用 `SPRING_PROFILES_ACTIVE=dev` 打开 SQL 日志）；
4. `cd frontend && npm i && npm run dev`。

### 一键跑通后的自检

仓库内置了 45 条断言的自动化冒烟测试（覆盖三端鉴权、商家数据隔离、入驻审核闭环、下单履约、看板口径、账号封禁）：

```powershell
# 后端已在 127.0.0.1:8080 运行时执行
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
# 期望结尾：PASS: 45    FAIL: 0
```

---

## 五、演示账号

| 角色 | 账号 | 密码 | 说明 |
|---|---|---|---|
| 🛡️ 平台管理员 | `13800000000` | `admin123` | 登录后进入 `/admin/dashboard` |
| 🏪 商家（已过审） | `13700000001` | `merchant123` | 蜀香居川菜馆老板，可直接经营 |
| 🏪 商家（待审核） | `13700000002` | `merchant123` | **尚未开店**，用于演示"提交入驻 → 管理员审核 → 自动开店" |
| 👤 用户 | `13900000001` | `123456` | 已配置饭搭子兴趣标签 |
| 👤 更多用户 | `13900000002` ~ `13900000009` | `123456` | 带不同兴趣标签，用于匹配演示 |

### 10 秒演示三端联动

1. 用**用户端**（`13900000001`）在「蜀香居川菜馆」下单并支付；
2. 用 `13700000001` 登录**商家端**，右下角实时弹出「🔔 新订单提醒」，在「订单履约」里接单 → 配送 → 完成；
3. 用 `13800000000` 登录**管理端**，在「平台看板」看到今日订单/GMV 变化，在「审计日志」看到刚才的操作留痕；
4. 再用 `13700000002` 登录**商家端** → 提示尚未开通经营权限 → 换管理端在「入驻审核」通过其申请 → 回到 `13700000002` 已自动开店可经营。

---

## 六、接口速查

统一返回 `{code:1,msg:"ok",data:...}`；除登录/注册外均需 `Authorization: Bearer <token>`。
`code`：`1` 成功、`401` 未登录/过期、`403` 端侧越权、`0` 业务失败。

### 6.1 认证（免登录）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/auth/login` | 三端统一登录，返回 `role` 与 `homePath` |
| POST | `/auth/register` | 用户端注册（role=1） |
| POST | `/auth/register/merchant` | 商家入驻注册（role=2 + 自动提交入驻申请单） |

### 6.2 用户端

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/shop/list` `/shop/hot` `/shop/nearby` `/shop/{id}` `/shop/{id}/products` | 店铺浏览 / 热点 / 附近(GEO) / 详情(多级缓存) / 商品 |
| GET/POST/DELETE | `/cart/**` | 购物车（Redis Hash） |
| POST | `/order/create` `/order/{id}/pay` `/order/{id}/cancel` | 下单 / 模拟支付 / 取消 |
| GET | `/order/mine` | 我的订单 |
| GET/POST | `/voucher/seckill/list` `/voucher/{id}/seckill` `/voucher/{id}/grab` | 秒杀场次 / 秒杀 / 领券 |
| GET/POST | `/voucher/order/my` `/voucher/order/{id}/use` | 我的券包 / 核销 |
| GET/POST | `/mate/recommend` `/mate/search` `/mate/tags/hot` | 饭搭子匹配 / 搜索 / 热词 |
| GET/POST | `/sign/status` `/sign/today` `/sign/month/{yyyyMM}` | 签到（BitMap） |
| GET/PUT | `/user/me` `/user/me/role` `/user/me/password` | 我的资料 / 我的角色 / 改密码 |

### 6.3 商家端（`role=2`，管理员可代管）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/merchant/dashboard/overview` `/trend` `/orderStatus` `/topProducts` | 经营看板（单店口径） |
| GET | `/merchant/order/page` | 本店订单分页 |
| POST | `/merchant/order/{id}/accept` `/deliver` `/finish` `/cancel` | 订单履约状态机 |
| GET/POST/DELETE | `/merchant/product/list` `/save` `/{id}` | 商品管理 |
| GET/POST/DELETE | `/merchant/voucher/page` `/save` `/{id}` | 优惠券管理（含秒杀时间窗） |
| GET/POST | `/merchant/voucher/order/page` `/voucher/order/{id}/use` | 券核销 |
| GET | `/merchant/shop/mine` | 我的店铺 + 审核状态（管理员返回店铺列表用于代管） |
| POST | `/merchant/shop/profile` `/shop/open` | 维护店铺资料 / 营业开关 |
| POST/GET | `/merchant/apply/submit` `/apply/page` | 提交入驻/变更申请 / 我的申请记录 |

### 6.4 管理端（`role=3`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/admin/apply/page` `/admin/apply/{id}/audit` | 入驻申请分页 / 审核（通过即自动开店） |
| GET/POST/DELETE | `/admin/shop/page` `/admin/shop/{id}/audit` `/admin/shop/{id}` | 店铺治理：查询 / 停业恢复 / 删除 |
| GET/POST | `/admin/order/page` `/admin/order/{id}/{accept\|deliver\|finish\|cancel}` | 全平台订单巡检与兜底处置 |
| GET/POST/DELETE | `/admin/product/page` `/admin/product/{id}/status` `/admin/product/{id}` | 商品巡检与违规处理 |
| GET/POST/DELETE | `/admin/voucher/page` `/admin/voucher/{id}/audit` `/admin/voucher/{id}` | 优惠券治理（强制下架/恢复/删除） |
| GET/POST | `/admin/user/page` `/admin/user/{id}/status` | 账号治理（封禁/解封，管理员账号受保护） |
| GET/POST/DELETE | `/admin/type/list` `/admin/type/save` `/admin/type/{id}` | 类目字典维护 |
| GET | `/admin/stats/overview` `/trend` `/userTrend` `/shopAudit` `/roleDistribution` `/orderStatus` `/shopRank` | 平台看板 |
| GET | `/admin/audit/page` | 平台操作审计日志 |

---

## 七、数据库设计（三端模型）

### 7.1 核心表

| 表 | 关键字段 | 说明 |
|---|---|---|
| `tb_user` | `role`(1用户/2商家/3管理员)、`status`(1正常/0封禁) | 三端统一账号 |
| `tb_shop` | `merchant_id`(**唯一**)、`audit_status`(0待审/1通过/2驳回/3停业)、`open_status`、`lon/lat`、`score` | 商家经营主体 |
| `tb_merchant_apply` | `merchant_id`、`type`(1入驻/2变更)、`license_no`、`status`、`auditor_id`、`audit_remark` | 入驻/变更申请单（平台审核留痕） |
| `tb_audit_log` | `actor_id`、`actor_role`、`action`、`target_type`、`target_id`、`detail` | 平台关键操作审计 |
| `tb_product` | `shop_id`、`price`(分)、`stock`、`status` | 商品（归属店铺→归属商家） |
| `tb_voucher` | `shop_id`、`type`(1代金/2秒杀)、`status`(商家上下架)、`audit_status`(平台治理) | 优惠券 |
| `tb_orders` / `tb_order_item` | `user_id`、`shop_id`、`status`、`amount`、快照字段 | 订单与明细 |
| `tb_voucher_order` | `user_id + voucher_id` 唯一 | 一人一单最终防线 |
| `tb_message` | `from_id`、`to_id`、`read_flag` | 饭搭子私信 |

### 7.2 演示数据规模

`sql/init.sql` 首次启动自动写入：**1 管理员 + 11 商家 + 9 用户 + 10 家北京店铺（含经纬度）+ 36 个商品 + 4 张秒杀券（时间窗 NOW-1天~NOW+7天）+ 3 张代金券 + 2 张入驻申请单（1 待审核）**。

### 7.3 旧库升级

已部署过旧的双端版本？执行幂等升级脚本即可（原 `role=2` 管理员自动提升为 `role=3`，为历史店铺补建商家账号并绑定归属）：

```bash
mysql -uroot -p smartlife < sql/manual/upgrade_v2_three_end.sql
```

---

## 八、核心设计亮点（面试可讲）

| # | 亮点 | 代码位置 |
|---|---|---|
| 1 | **多角色系统的"权限不走样"**：拦截器按路径前缀粗粒度分端 + Service 层 `RoleGuard` 细粒度校验 + 商家归属强校验 + 服务端强制重写 `shopId`，三道防线防越权 | `security/AuthInterceptor.java`、`security/RoleGuard.java`、`service/MerchantService.java` |
| 2 | **平台治理闭环**：入驻申请（`tb_merchant_apply`）→ 管理员审核 → 自动创建并开通店铺（幂等：同一申请单重复审核被拒）→ 全程落审计日志 | `service/MerchantService.java#auditApply`、`controller/AdminMerchantController.java` |
| 3 | **多级缓存治理**：L1 Caffeine(5s) → L2 Redis(空值标记 + 随机 TTL 20~40min) → DB；布隆过滤器拦穿透、互斥锁重建防击穿；**审核未过/休息中的店铺对用户端等价于不存在** | `service/ShopService.java` |
| 4 | **秒杀**：Lua 原子扣减 + 一人一单 → RabbitMQ 异步削峰 → 消费者事务兜底 + 唯一索引幂等 + 死信回补库存；`SECKILL_LUA_ENABLED=false` 一键切换 Redisson 锁方案做对比 | `service/VoucherService.java`、`mq/*.java`、`resources/lua/seckill.lua` |
| 5 | **订单履约 + 多端 WebSocket**：Redis Hash 购物车；原子扣库存 `where stock>=n`；按角色分桶推送（新单只推给该店铺商家、状态变更推给下单用户） | `service/OrderService.java`、`ws/*.java` |
| 6 | **附近/签到/UV**：Redis GEO 附近搜索、BitMap 每日签到（一年 46B/用户）、HyperLogLog UV、定时任务预热（Redisson 锁防多机重复执行） | `service/SignService.java`、`task/CachePreheatTask.java` |
| 7 | **饭搭子匹配**：编辑距离 + 容量 N 优先队列 TopN（内存 O(N)），结果 Redis 缓存 10min | `service/MateService.java`、`util/Levenshtein.java` |

---

## 九、服务器部署（阿里云 Ubuntu 22.04，无域名）

**整机全容器化，一条命令拉起全站**（MySQL + Redis + RabbitMQ + 后端 + Nginx 前端），对外只暴露 80（HTTP）与 443（HTTPS）：

```bash
# 服务器上执行（详见 docs/01-从零部署-上.md 与 02-从零部署-下.md）
cd /opt/smartlife
cp .env.prod.example .env.prod && vim .env.prod    # 改密码/密钥
chmod +x scripts/*.sh
./scripts/deploy.sh --first
# 访问 http://<服务器公网IP>/

# 无域名也能上 HTTPS：签 Let's Encrypt IP 证书（免费，自动续签，无需 ICP 备案）
./scripts/https-enable.sh
# 访问 https://<服务器公网IP>/   （详见 docs/03-启用HTTPS-IP证书.md）
```

| 文档 | 内容 |
|---|---|
| 📘 **[docs/01-从零部署-上.md](docs/01-从零部署-上.md)** | 阶段 0~5：清空机器 → 系统准备（时区/Swap/Docker）→ 本机打包 → 上传解压 → 包完整性校验 → 生成密钥 → 构建镜像 |
| 📘 **[docs/02-从零部署-下.md](docs/02-从零部署-下.md)** | 启动验收 + 排错手册 |
| 🔒 **[docs/03-启用HTTPS-IP证书.md](docs/03-启用HTTPS-IP证书.md)** | 无域名上用 HTTPS：IP 证书签发/续签/回滚、限额依据、浏览器实测清单 |
| 🐳 `docker-compose.prod.yml` | 生产编排（健康检查、密钥注入、端口收敛、日志轮转） |
| 🔧 `scripts/deploy.sh` | 一键部署 / 升级 / 健康检查 / 查看日志（构建后先用新镜像跑 `nginx -t` 预检，配置写错就中止发布） |
| 🔐 `scripts/https-enable.sh` | 一键启用 HTTPS：acme.sh 签发 + 安装 + 定时续签 + reload 容器内 Nginx（`--status` / `--renew` / `--disable`） |
| 💾 `scripts/backup-db.sh` | 数据库备份（可挂 cron，自动清理旧备份） |

---

## 十、JMeter 压测建议（自证优化效果）

```bash
# 1) 店铺详情 /shop/{id}：先预热再压，观察多级缓存带来的 QPS 与 P99
# 2) 秒杀 POST /voucher/{id}/seckill：秒杀券 stock=100、2000 线程并发
#    断言：tb_voucher_order 行数 == 100（不超卖）、tb_voucher.sold == 100
#    对照：SECKILL_LUA_ENABLED=false（Redisson 锁）vs true（Lua + MQ）吞吐与平均耗时
# 3) 结论请以本地实测数字为准，不要照抄网上的绝对数值
```

---

## 十一、常见问题

- **秒杀提示"手慢了"**：Redis 库存缓存与 DB 不同步 → 重启后端（`DataInitRunner` 自动回填）或在商家端重存一次该券。
- **商家端按钮提示"店铺尚未通过审核"**：这是设计如此，先在管理端「入驻审核」通过其申请。
- **用户端看不到某家店**：该店 `audit_status != 1` 或被商家设为休息，用户端等价于不存在（商家端仍可见）。
- **"附近店铺"为空**：种子店铺都在北京（116.3~116.5 / 39.89~40.0）；浏览器定位被拒时会回退到望京坐标。若仍为空，重启后端重建 Redis GEO 索引。
- **MySQL 8 密码插件**：账号用 `mysql_native_password`，或连接串已带 `allowPublicKeyRetrieval=true`。
- **Docker 时区**：容器统一 `TZ=Asia/Shanghai`，签到/秒杀以"东八区"为准。

---

## 📚 学习文档

| 文档 | 内容 |
|---|---|
| 🎓 **[教学文档 · 从零读懂智联生活](docs/教学文档-从零读懂智联生活.md)** | **手把手读代码主线**：7 天计划、三端业务全貌、请求生命周期、三端鉴权与数据隔离（五道防线）、缓存/交易/秒杀/社交逐模块"业务→代码→为什么→练习"、商家端与管理端、速查地图、部署运维、调试排错、改造任务与自测题库 |
| [从零部署（下）](docs/02-从零部署-下.md) | 阶段 6~10：启动服务 → 三端验收 → 浏览器实测 → 运维命令 → 备份恢复 → 升级回滚 → 排错手册 |
| [项目学习手册 ① 架构总览与数据模型](docs/项目学习手册/01-架构总览与数据模型.md) | 三端模型、表结构、请求链路 |
| [项目学习手册 ② 后端基础链路与高并发实现](docs/项目学习手册/02-后端基础链路与高并发实现.md) | 三端鉴权、多级缓存、GEO、签到、UV、匹配 |
| [项目学习手册 ③ 交易链路](docs/项目学习手册/03-交易链路.md) | 秒杀(Lua+MQ)、订单履约、多端 WebSocket、私信 |
| [项目学习手册 ④ 技术选型与更优方案](docs/项目学习手册/04-技术选型与更优方案.md) | 每项技术"为什么这么做 / 代价 / 更优解"大表 |
| [项目学习手册 ⑤ 学习路线与实验](docs/项目学习手册/05-学习路线与实验.md) | 按依赖顺序的精读路线与动手实验 |
| [简历项目写法](docs/简历项目写法.md) | STAR 话术与量化结果模板 |
| [简历差异化包装与优化](docs/简历差异化包装与优化.md) | 差异化定位与反雷同改写 |
| [面试题档案 56 条](docs/面试题档案56条.md) | 高频 → 低频 + **多角色/三端专题**面试题与解析 |

---

## 十二、参考资料（能力来源）

- 苍穹外卖（黑马程序员）：订单状态机、商家端履约、WebSocket 推送、定时任务
- 黑马点评（黑马程序员）：Redis 全场景（缓存治理 / Lua / GEO / BitMap / HyperLogLog / 分布式锁）
- 黑马商城：秒杀削峰（Redis + Lua + RabbitMQ）思路
- 伙伴匹配系统（鱼皮 · 编程导航）：编辑距离 + 优先队列 TopN 匹配

> 本项目在以上思路上做了**三端平台化改造**：把"管理端兼商家端"拆成真正的商家端与平台管理端，补齐入驻审核、数据隔离与审计留痕，使其更接近真实业务的平台治理形态。
