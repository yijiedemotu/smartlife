-- =============================================================================================
-- 智联生活 · 旧库升级脚本（v1 双端 -> v2 三端）
-- 适用：已经在跑 v1（role 1=用户 2=管理员）的数据库，不想删库重建
-- 用法：mysql -uroot -p smartlife < sql/manual/upgrade_v2_three_end.sql
--
-- ★ 编码关键（勿删下面的 SET NAMES）★
--   本文件是 UTF-8 编码。若客户端连接字符集是 latin1，脚本里的中文（如"历史数据迁移
--   自动通过"）会被双重编码写坏。下面强制本次会话使用 utf8mb4。
--
-- 脚本做了什么：
--   1. 角色重定义：1=用户 2=商家 3=平台管理员，原 role=2 的账号自动升为 role=3
--   2. tb_user 增加 status 字段
--   3. tb_shop 增加 merchant_id / phone / description / audit_status / audit_remark / audit_time / open_status
--   4. 新建 tb_merchant_apply（入驻申请单）与 tb_audit_log（操作审计）
--   5. tb_voucher 增加 audit_status（平台可强制下架）
--   6. 为已有 10 家店铺补建商家账号并绑定
--
-- 幂等性说明：MySQL 8 的 ADD COLUMN 不支持 IF NOT EXISTS，故用 information_schema 判断后动态执行；
--            可安全重复执行（已存在的列/表/索引会跳过，商家账号用 NOT EXISTS 防重复插入）。
-- =============================================================================================
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

USE smartlife;

-- ---------------------------------------------------------------------------------------------
-- 0. 把 v1 的管理员（role=2）提升为平台管理员（role=3），必须先于后续商家账号插入执行
-- ---------------------------------------------------------------------------------------------
UPDATE tb_user SET role = 3 WHERE role = 2;

-- ---------------------------------------------------------------------------------------------
-- 1. tb_user.status
-- ---------------------------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sl_add_column;
DELIMITER $$
CREATE PROCEDURE sl_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS sl_add_index;
DELIMITER $$
CREATE PROCEDURE sl_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl VARCHAR(500))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL sl_add_column('tb_user', 'status', "`status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0封禁' AFTER `role`");
CALL sl_add_column('tb_shop', 'merchant_id', "`merchant_id` BIGINT DEFAULT NULL COMMENT '归属商家 tb_user.id' AFTER `name`");
CALL sl_add_column('tb_shop', 'phone', "`phone` VARCHAR(20) DEFAULT NULL COMMENT '店铺电话' AFTER `address`");
CALL sl_add_column('tb_shop', 'description', "`description` VARCHAR(500) DEFAULT NULL COMMENT '店铺简介' AFTER `images`");
CALL sl_add_column('tb_shop', 'audit_status', "`audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '0待审核 1已通过 2已驳回 3已停业' AFTER `popularity`");
CALL sl_add_column('tb_shop', 'audit_remark', "`audit_remark` VARCHAR(255) DEFAULT NULL COMMENT '审核意见' AFTER `audit_status`");
CALL sl_add_column('tb_shop', 'audit_time', "`audit_time` DATETIME DEFAULT NULL COMMENT '审核时间' AFTER `audit_remark`");
CALL sl_add_column('tb_shop', 'open_status', "`open_status` TINYINT NOT NULL DEFAULT 1 COMMENT '1营业 0休息' AFTER `audit_time`");
CALL sl_add_column('tb_voucher', 'audit_status', "`audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0平台下架' AFTER `status`");

CALL sl_add_index('tb_user', 'idx_role_status', 'ADD KEY `idx_role_status` (`role`, `status`)');
CALL sl_add_index('tb_shop', 'uk_merchant', 'ADD UNIQUE KEY `uk_merchant` (`merchant_id`)');
CALL sl_add_index('tb_shop', 'idx_audit', 'ADD KEY `idx_audit` (`audit_status`)');
CALL sl_add_index('tb_orders', 'idx_shop_status', 'ADD KEY `idx_shop_status` (`shop_id`, `status`)');
CALL sl_add_index('tb_voucher_order', 'idx_shop', 'ADD KEY `idx_shop` (`shop_id`)');

DROP PROCEDURE IF EXISTS sl_add_column;
DROP PROCEDURE IF EXISTS sl_add_index;

-- ---------------------------------------------------------------------------------------------
-- 2. 新建表：入驻申请单 / 平台审计日志
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_merchant_apply (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    merchant_id     BIGINT        NOT NULL COMMENT '申请商家 tb_user.id',
    shop_id         BIGINT        DEFAULT NULL COMMENT '关联店铺（首次入驻通过后回填）',
    type            TINYINT       NOT NULL DEFAULT 1 COMMENT '1首次入驻 2资料变更',
    shop_name       VARCHAR(64)   NOT NULL COMMENT '店铺名称',
    type_id         BIGINT        DEFAULT NULL COMMENT '经营类目',
    contact_name    VARCHAR(32)   DEFAULT NULL COMMENT '联系人',
    contact_phone   VARCHAR(20)   DEFAULT NULL COMMENT '联系电话',
    area            VARCHAR(32)   DEFAULT NULL,
    address         VARCHAR(255)  DEFAULT NULL,
    lon             DECIMAL(10,6) DEFAULT NULL,
    lat             DECIMAL(10,6) DEFAULT NULL,
    license_no      VARCHAR(64)   DEFAULT NULL COMMENT '营业执照号',
    license_img     VARCHAR(500)  DEFAULT NULL COMMENT '营业执照图片',
    id_card_img     VARCHAR(500)  DEFAULT NULL COMMENT '法人身份证图片',
    description     VARCHAR(500)  DEFAULT NULL COMMENT '店铺简介',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0待审核 1已通过 2已驳回',
    audit_remark    VARCHAR(255)  DEFAULT NULL COMMENT '审核意见',
    auditor_id      BIGINT        DEFAULT NULL COMMENT '审核人 tb_user.id（管理员）',
    audit_time      DATETIME      DEFAULT NULL,
    create_time     DATETIME      DEFAULT NULL,
    update_time     DATETIME      DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_merchant (merchant_id, status),
    KEY idx_status (status, create_time)
) ENGINE = InnoDB COMMENT ='商家入驻/变更申请单';

CREATE TABLE IF NOT EXISTS tb_audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NOT NULL COMMENT '操作人 tb_user.id',
    actor_role  TINYINT      NOT NULL COMMENT '操作人角色 2商家 3管理员',
    action      VARCHAR(64)  NOT NULL COMMENT '动作标识',
    target_type VARCHAR(32)  DEFAULT NULL COMMENT '目标类型',
    target_id   BIGINT       DEFAULT NULL COMMENT '目标主键',
    detail      VARCHAR(500) DEFAULT NULL COMMENT '明细',
    create_time DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_actor (actor_id),
    KEY idx_action (action, create_time)
) ENGINE = InnoDB COMMENT ='平台操作审计日志';

-- ---------------------------------------------------------------------------------------------
-- 3. 为已有店铺补建商家账号（13700000001 ~ 13700000010 / merchant123）并绑定归属
--    密码哈希 = sha256hex('merchant123' + 'seed')，与 PasswordUtil 校验规则一致
-- ---------------------------------------------------------------------------------------------
INSERT INTO tb_user (id, phone, password, nickname, gender, role, status, wechat, create_time, update_time)
SELECT * FROM (
    SELECT 11 AS id, '13700000001' AS phone, 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37' AS password, '蜀香居王老板' AS nickname, 1 AS gender, 2 AS role, 1 AS status, 'wx_shuxiang' AS wechat, NOW() AS create_time, NOW() AS update_time
    UNION ALL SELECT 12, '13700000002', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '码头李掌柜',   1, 2, 1, 'wx_matou',    NOW(), NOW()
    UNION ALL SELECT 13, '13700000003', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '慢时光店长',   2, 2, 1, 'wx_manshi',   NOW(), NOW()
    UNION ALL SELECT 14, '13700000004', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '苏氏面馆老板', 1, 2, 1, 'wx_sushi',    NOW(), NOW()
    UNION ALL SELECT 15, '13700000005', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '深夜食堂主厨', 1, 2, 1, 'wx_shenye',   NOW(), NOW()
    UNION ALL SELECT 16, '13700000006', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '茶百味掌柜',   2, 2, 1, 'wx_chabai',   NOW(), NOW()
    UNION ALL SELECT 17, '13700000007', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '砂锅粥陈叔',   1, 2, 1, 'wx-shaguo',   NOW(), NOW()
    UNION ALL SELECT 18, '13700000008', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '觅见烧烤老板', 1, 2, 1, 'wx_mijian',   NOW(), NOW()
    UNION ALL SELECT 19, '13700000009', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '云上瑜伽馆主', 2, 2, 1, 'wx_yunshang', NOW(), NOW()
    UNION ALL SELECT 20, '13700000010', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '京客便利店长', 1, 2, 1, 'wx_jingke',   NOW(), NOW()
) AS seed_merchant
WHERE NOT EXISTS (SELECT 1 FROM tb_user u WHERE u.phone = seed_merchant.phone);

-- 店铺 1~10 依次绑定商家 11~20（仅绑定尚未绑定归属的店铺，保证幂等）
UPDATE tb_shop s
JOIN (SELECT 1 AS shop_id, 11 AS merchant_id
      UNION ALL SELECT 2, 12 UNION ALL SELECT 3, 13 UNION ALL SELECT 4, 14 UNION ALL SELECT 5, 15
      UNION ALL SELECT 6, 16 UNION ALL SELECT 7, 17 UNION ALL SELECT 8, 18 UNION ALL SELECT 9, 19
      UNION ALL SELECT 10, 20) m ON m.shop_id = s.id
SET s.merchant_id = m.merchant_id,
    s.audit_status = 1,
    s.open_status = 1,
    s.audit_time = IFNULL(s.audit_time, NOW()),
    s.audit_remark = IFNULL(s.audit_remark, '历史数据迁移自动通过')
WHERE s.merchant_id IS NULL;

-- 为已绑定的店铺补建「已通过」的入驻申请单（便于管理端审核记录完整）
INSERT INTO tb_merchant_apply (merchant_id, shop_id, type, shop_name, type_id, contact_name, contact_phone,
                               area, address, lon, lat, description, status, audit_remark, auditor_id, audit_time,
                               create_time, update_time)
SELECT s.merchant_id, s.id, 1, s.name, s.type_id, u.nickname, u.phone, s.area, s.address, s.lon, s.lat,
       s.description, 1, '历史数据迁移自动通过', 1, NOW(), NOW(), NOW()
FROM tb_shop s
JOIN tb_user u ON u.id = s.merchant_id
WHERE s.merchant_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM tb_merchant_apply a WHERE a.shop_id = s.id);

-- ---------------------------------------------------------------------------------------------
-- 4. 修正注释（可选，仅影响可读性）
-- ---------------------------------------------------------------------------------------------
ALTER TABLE tb_user MODIFY COLUMN role TINYINT NOT NULL DEFAULT 1 COMMENT '1用户 2商家 3平台管理员';

-- 校验输出
SELECT '升级完成' AS step;
SELECT role, COUNT(*) AS cnt FROM tb_user GROUP BY role;
SELECT audit_status, COUNT(*) AS cnt FROM tb_shop GROUP BY audit_status;
