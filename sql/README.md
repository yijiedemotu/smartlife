# sql 目录说明

> ⚠️ **只有一个脚本会被自动执行**：`init.sql`。
> `docker-compose.yml` / `docker-compose.prod.yml` 只把 `init.sql` 挂载到 MySQL 容器的
> `/docker-entrypoint-initdb.d/01-init.sql`，且仅在**数据卷为空**时执行一次。
>
> `manual/` 子目录是**手工执行**的脚本，不会被容器自动执行（挪进子目录也是为了避免
> MySQL entrypoint 用 `*.sql` 通配匹配到它们）。

## 文件清单

| 文件 | 用途 | 什么时候用 | 自动执行 |
|---|---|---|---|
| `init.sql` | **三端完整初始化**：建库、建表（含 `tb_merchant_apply` / `tb_audit_log`）、索引、种子数据（1 管理员 + 11 商家 + 9 用户 + 10 店铺 + 36 商品 + 7 张券 + 2 张入驻申请单） | 全新部署（首次启动容器时自动完成） | ✅ 是 |
| `manual/upgrade_v2_three_end.sql` | **v1 双端库 → v2 三端库**的幂等升级：原 `role=2` 管理员提升为 `role=3`；补 `tb_user.status`、`tb_shop.merchant_id/audit_status/open_status/...`、`tb_voucher.audit_status`；补索引；新建 `tb_merchant_apply`、`tb_audit_log`；为历史店铺补建商家账号并绑定归属 | 你已经跑过旧版本、库里**有真实数据**、不想删库重建时 | ❌ 手工执行 |
| `manual/upgrade_chat.legacy.sql` | 历史遗留：为旧库补 `tb_user.wechat` 列与 `tb_message` 表（该内容已并入 `init.sql`） | 仅当你有一个**早于私信功能**的旧库需要补列时 | ❌ 手工执行 |
| `manual/more_seed_data.legacy.sql` | 历史遗留：补充饭搭子种子用户（IDs 11~18） | ⚠️ **不要在新库执行**：三端模型下 IDs 11~20 已被商家账号占用，会主键冲突。仅作为"旧库补数据"留档 | ❌ 手工执行 |

## 常用命令

```bash
# 1) 全新初始化：交给容器自动完成，无需手工执行
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d

# 2) 旧库升级为三端（先备份！）
docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T mysql \
  mysql -uroot -p"$MYSQL_ROOT_PASSWORD" smartlife < sql/manual/upgrade_v2_three_end.sql

# 3) 本地直连 MySQL 执行（本地开发环境）
mysql -uroot -p smartlife < sql/manual/upgrade_v2_three_end.sql

# 4) 彻底重置（删除数据卷后重新初始化）
docker compose --env-file .env.prod -f docker-compose.prod.yml down
docker volume rm smartlife-mysql-data
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

## 升级脚本的幂等性

`manual/upgrade_v2_three_end.sql` 可以**安全重复执行**：MySQL 8 的 `ADD COLUMN` 不支持
`IF NOT EXISTS`，所以脚本用两个存储过程（`sl_add_column` / `sl_add_index`）先查
`information_schema` 再动态 `ALTER`，已存在的列/索引/表自动跳过；补建商家账号用
`NOT EXISTS` 防重复插入；绑定归属用 `WHERE merchant_id IS NULL` 作为幂等条件。

执行顺序上有一处**关键约束**：必须先把 `role=2` 的旧管理员统一提升为 `role=3`
（`UPDATE tb_user SET role = 3 WHERE role = 2;`），否则后面插入 `role=2` 的商家账号时
会与"2 曾是管理员"的旧语义混淆。
