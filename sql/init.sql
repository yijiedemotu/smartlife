-- =============================================================================================
-- 智联生活 · 本地生活与社交聚合平台 · 三端（用户端 / 商家端 / 管理端）数据库初始化脚本
-- MySQL 8.x  字符集 utf8mb4
--
-- 角色模型：role 1=普通用户  2=商家  3=平台管理员
-- 归属模型：一个商家账号绑定一个店铺（tb_shop.merchant_id 唯一）
-- 治理模型：商家自主提交入驻资料 -> 平台管理员审核(audit_status) -> 通过后方可经营
--
-- 幂等：表使用 CREATE TABLE IF NOT EXISTS，种子使用 INSERT IGNORE（固定主键）
-- 旧库升级：请使用 sql/upgrade_v2_three_end.sql，不要直接重跑本脚本
--
-- 演示账号（密码为明文，库中存 salt:sha256hex(password+salt)）
--   平台管理员   13800000000 / admin123
--   商家         13700000001 / merchant123   （蜀香居川菜馆，已过审可经营）
--   商家(待审核) 13700000002 / merchant123   （尚未开店，用于演示"提交入驻 → 平台审核 → 自动开店"）
--   用户         13900000001 / 123456       （13900000002 ~ 13900000009 同密码）
-- =============================================================================================
CREATE DATABASE IF NOT EXISTS smartlife DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smartlife;

-- ---------------------------------------------------------------------------------------------
-- 用户表（三端统一账号体系）
--   role 1=普通用户（饭搭子） 2=商家 3=平台管理员
--   password 存储格式：salt:sha256hex(password+salt)
--   tags 为 JSON 数组，用于饭搭子兴趣匹配
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone       VARCHAR(11)  NOT NULL COMMENT '手机号（登录账号）',
    password    VARCHAR(128) NOT NULL COMMENT 'salt:sha256hex(password+salt)',
    nickname    VARCHAR(32)  DEFAULT NULL COMMENT '昵称',
    avatar      VARCHAR(500) DEFAULT NULL COMMENT '头像',
    gender      TINYINT      DEFAULT 0 COMMENT '0未知 1男 2女',
    tags        JSON         DEFAULT NULL COMMENT '兴趣标签 JSON数组（用户端饭搭子）',
    address     VARCHAR(255) DEFAULT NULL COMMENT '默认收货/活动地址',
    wechat      VARCHAR(64)  DEFAULT NULL COMMENT '微信号(联系方式)',
    role        TINYINT      NOT NULL DEFAULT 1 COMMENT '1用户 2商家 3平台管理员',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0封禁（平台管理员可封禁）',
    create_time DATETIME     DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone),
    KEY idx_role_status (role, status)
) ENGINE = InnoDB COMMENT ='用户（三端统一账号）';

-- ---------------------------------------------------------------------------------------------
-- 店铺类型（平台级字典，仅管理端可维护）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_shop_type (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(32)  NOT NULL,
    icon        VARCHAR(16)  DEFAULT NULL COMMENT 'emoji 图标',
    sort        INT          NOT NULL DEFAULT 0,
    create_time DATETIME DEFAULT NULL,
    update_time DATETIME DEFAULT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT ='店铺类型（平台字典）';

-- ---------------------------------------------------------------------------------------------
-- 店铺（商家实体；含经纬度支撑 Redis GEO 附近搜索）
--   merchant_id  归属商家（一个商家一个店铺，唯一）
--   audit_status 平台治理状态：0待审核 1已通过/营业中 2已驳回 3已停业
--   open_status  商家自主开关：1营业 0休息（仅审核通过后可切换）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_shop (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    name            VARCHAR(64)   NOT NULL,
    merchant_id     BIGINT        DEFAULT NULL COMMENT '归属商家 tb_user.id（role=2）',
    type_id         BIGINT        DEFAULT NULL COMMENT '所属类型',
    area            VARCHAR(32)   DEFAULT NULL COMMENT '商圈（望京/三里屯…）',
    address         VARCHAR(255)  DEFAULT NULL,
    phone           VARCHAR(20)   DEFAULT NULL COMMENT '店铺联系电话',
    images          VARCHAR(1000) DEFAULT NULL COMMENT '图片，逗号分隔',
    description     VARCHAR(500)  DEFAULT NULL COMMENT '店铺简介',
    lon             DECIMAL(10,6) DEFAULT NULL,
    lat             DECIMAL(10,6) DEFAULT NULL,
    score           DECIMAL(3,1)  DEFAULT 5.0 COMMENT '评分',
    popularity      INT           DEFAULT 0 COMMENT '热力值(用于热点预热)',
    audit_status    TINYINT       NOT NULL DEFAULT 1 COMMENT '0待审核 1已通过 2已驳回 3已停业',
    audit_remark    VARCHAR(255)  DEFAULT NULL COMMENT '审核意见/驳回原因',
    audit_time      DATETIME      DEFAULT NULL COMMENT '审核时间',
    open_status     TINYINT       NOT NULL DEFAULT 1 COMMENT '1营业 0休息',
    create_time     DATETIME      DEFAULT NULL,
    update_time     DATETIME      DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant (merchant_id),
    KEY idx_type_score (type_id, popularity),
    KEY idx_audit (audit_status)
) ENGINE = InnoDB COMMENT ='店铺（商家经营主体）';

-- ---------------------------------------------------------------------------------------------
-- 商家入驻/变更申请单（审核留痕，平台可追溯每一次提交）
--   type 1=首次入驻 2=资料变更
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

-- ---------------------------------------------------------------------------------------------
-- 平台操作审计日志（管理端关键操作留痕：审核、封禁、删除等）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT       NOT NULL COMMENT '操作人 tb_user.id',
    actor_role  TINYINT      NOT NULL COMMENT '操作人角色 2商家 3管理员',
    action      VARCHAR(64)  NOT NULL COMMENT '动作标识，如 SHOP_APPROVE',
    target_type VARCHAR(32)  DEFAULT NULL COMMENT '目标类型 SHOP/USER/ORDER/PRODUCT/VOUCHER',
    target_id   BIGINT       DEFAULT NULL COMMENT '目标主键',
    detail      VARCHAR(500) DEFAULT NULL COMMENT '明细（含审核意见等）',
    create_time DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_actor (actor_id),
    KEY idx_action (action, create_time)
) ENGINE = InnoDB COMMENT ='平台操作审计日志';

-- ---------------------------------------------------------------------------------------------
-- 商品（外卖商品，价格单位：分；归属店铺 -> 归属商家）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_product (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    shop_id     BIGINT        NOT NULL,
    name        VARCHAR(64)   NOT NULL,
    images      VARCHAR(500)  DEFAULT NULL,
    description VARCHAR(255)  DEFAULT NULL,
    price       INT           NOT NULL COMMENT '单价(分)',
    stock       INT           NOT NULL DEFAULT 0,
    sales       INT           NOT NULL DEFAULT 0,
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
    create_time DATETIME      DEFAULT NULL,
    update_time DATETIME      DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_shop (shop_id)
) ENGINE = InnoDB COMMENT ='商品';

-- ---------------------------------------------------------------------------------------------
-- 优惠券（type：1=普通代金券 2=秒杀券；audit_status 管理端可下架违规券）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_voucher (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    shop_id      BIGINT       NOT NULL COMMENT '适用店铺',
    title        VARCHAR(64)  NOT NULL,
    sub_title    VARCHAR(128) DEFAULT NULL,
    rules        VARCHAR(255) DEFAULT NULL,
    pay_value    INT          NOT NULL DEFAULT 0 COMMENT '购买价格(分)',
    actual_value INT          NOT NULL COMMENT '券面金额(分)',
    type         TINYINT      NOT NULL DEFAULT 1,
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1上架 0下架（商家自主）',
    audit_status TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0平台强制下架',
    stock        INT          NOT NULL DEFAULT 0 COMMENT '发放库存',
    sold         INT          NOT NULL DEFAULT 0,
    begin_time   DATETIME     DEFAULT NULL COMMENT '秒杀开始',
    end_time     DATETIME     DEFAULT NULL COMMENT '秒杀结束',
    create_time  DATETIME     DEFAULT NULL,
    update_time  DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_shop (shop_id),
    KEY idx_type (type, status)
) ENGINE = InnoDB COMMENT ='优惠券';

-- ---------------------------------------------------------------------------------------------
-- 优惠券订单（一人一单：user_id + voucher_id 唯一）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_voucher_order (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    user_id       BIGINT   NOT NULL,
    voucher_id    BIGINT   NOT NULL,
    shop_id       BIGINT   NOT NULL,
    voucher_title VARCHAR(64) DEFAULT NULL COMMENT '快照',
    actual_value  INT      NOT NULL DEFAULT 0 COMMENT '快照(分)',
    status        TINYINT  NOT NULL DEFAULT 1 COMMENT '1未使用 2已使用 3已过期',
    create_time   DATETIME DEFAULT NULL,
    use_time      DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_voucher (user_id, voucher_id),
    KEY idx_user (user_id),
    KEY idx_shop (shop_id)
) ENGINE = InnoDB COMMENT ='优惠券订单';

-- ---------------------------------------------------------------------------------------------
-- 外卖订单（status：1待支付 2已支付/待接单 3已接单 4配送中 5已完成 6已取消）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_orders (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    number      VARCHAR(40)  NOT NULL COMMENT '订单号',
    user_id     BIGINT       NOT NULL,
    shop_id     BIGINT       NOT NULL,
    shop_name   VARCHAR(64)  DEFAULT NULL COMMENT '快照',
    address     VARCHAR(255) NOT NULL COMMENT '收货地址快照',
    amount      INT          NOT NULL COMMENT '金额(分)',
    status      TINYINT      NOT NULL DEFAULT 1,
    remark      VARCHAR(255) DEFAULT NULL,
    pay_time    DATETIME     DEFAULT NULL,
    create_time DATETIME     DEFAULT NULL,
    update_time DATETIME     DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_number (number),
    KEY idx_user (user_id),
    KEY idx_shop_status (shop_id, status),
    KEY idx_status (status)
) ENGINE = InnoDB COMMENT ='外卖订单';

-- ---------------------------------------------------------------------------------------------
-- 订单明细
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_order_item (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    order_id      BIGINT       NOT NULL,
    product_id    BIGINT       NOT NULL,
    product_name  VARCHAR(64)  DEFAULT NULL COMMENT '快照',
    product_image VARCHAR(500) DEFAULT NULL,
    price         INT          NOT NULL COMMENT '下单单价(分)快照',
    count         INT          NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE = InnoDB COMMENT ='订单明细';

-- ---------------------------------------------------------------------------------------------
-- 饭搭子私信（用户端社交）
-- ---------------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tb_message (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    from_id     BIGINT        NOT NULL COMMENT '发送人',
    to_id       BIGINT        NOT NULL COMMENT '接收人',
    content     VARCHAR(500)  NOT NULL COMMENT '内容',
    read_flag   TINYINT       NOT NULL DEFAULT 0 COMMENT '0未读 1已读',
    create_time DATETIME      DEFAULT NULL,
    PRIMARY KEY (id),
    KEY idx_from (from_id),
    KEY idx_to_read (to_id, read_flag)
) ENGINE = InnoDB COMMENT ='饭搭子私信';

-- =============================================================================================
-- 种子数据
-- =============================================================================================

-- ---------- 账号：1 管理员 + 11 商家 + 9 用户 ----------
-- 管理员 13800000000 / admin123
-- 商家   13700000001 ~ 13700000010 / merchant123（均已过审，对应店铺 1~10 的老板）
-- 商家   13700000011 / merchant123（尚未开店，用于演示入驻审核流程）
-- 用户   13900000001 ~ 13900000009 / 123456
INSERT IGNORE INTO tb_user (id, phone, password, nickname, gender, tags, address, wechat, role, status, create_time, update_time) VALUES
(1,  '13800000000', 'seed:e61ea5c0115e9dc541c26b898ebb7ea8a9beab428a3e6180a10a4df35cc14080', '平台管理员',   0, NULL, NULL, NULL, 3, 1, NOW(), NOW()),
(11, '13700000001', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '蜀香居王老板', 1, NULL, NULL, 'wx_shuxiang', 2, 1, NOW(), NOW()),
(12, '13700000002', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '码头李掌柜',   1, NULL, NULL, 'wx_matou',    2, 1, NOW(), NOW()),
(13, '13700000003', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '慢时光店长',   2, NULL, NULL, 'wx_manshi',   2, 1, NOW(), NOW()),
(14, '13700000004', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '苏氏面馆老板', 1, NULL, NULL, 'wx_sushi',    2, 1, NOW(), NOW()),
(15, '13700000005', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '深夜食堂主厨', 1, NULL, NULL, 'wx_shenye',   2, 1, NOW(), NOW()),
(16, '13700000006', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '茶百味掌柜',   2, NULL, NULL, 'wx_chabai',   2, 1, NOW(), NOW()),
(17, '13700000007', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '砂锅粥陈叔',   1, NULL, NULL, 'wx_shaguo',   2, 1, NOW(), NOW()),
(18, '13700000008', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '觅见烧烤老板', 1, NULL, NULL, 'wx_mijian',   2, 1, NOW(), NOW()),
(19, '13700000009', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '云上瑜伽馆主', 2, NULL, NULL, 'wx_yunshang', 2, 1, NOW(), NOW()),
(20, '13700000010', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '京客便利店长', 1, NULL, NULL, 'wx_jingke',   2, 1, NOW(), NOW()),
(21, '13700000011', 'seed:6969d4663175762598ee07b6216b3e1d007e150e6cc0e2bfd2523ff4fd3a9f37', '待入驻张老板', 1, NULL, NULL, 'wx_ruzhu',    2, 1, NOW(), NOW()),
(2,  '13900000001', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '觅食小鱼',     2, JSON_ARRAY('川菜','夜跑','剧本杀','追剧','养猫'), '北京市朝阳区望京SOHO T1座', 'wx_mishi',    1, 1, NOW(), NOW()),
(3,  '13900000002', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '干饭王阿东',   1, JSON_ARRAY('川菜','火锅','剧本杀','王者'), '北京市朝阳区三里屯SOHO', 'wx_gandan',   1, 1, NOW(), NOW()),
(4,  '13900000003', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '夜跑阿凯',     1, JSON_ARRAY('夜跑','健身','摄影','咖啡'), '北京市海淀区五道口', 'wx_yepao',    1, 1, NOW(), NOW()),
(5,  '13900000004', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '芝士控小鹿',   2, JSON_ARRAY('甜品','咖啡','追剧','手工'), '北京市朝阳区国贸CBD', 'wx_zhishi',   1, 1, NOW(), NOW()),
(6,  '13900000005', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '环球吃货大壮', 1, JSON_ARRAY('日料','火锅','追剧','旅行'), '北京市朝阳区亮马桥', 'wx_dazhuang', 1, 1, NOW(), NOW()),
(7,  '13900000006', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '马拉松毛毛',   1, JSON_ARRAY('夜跑','健身','马拉松','徒步'), '北京市西城区西单', 'wx_malason',  1, 1, NOW(), NOW()),
(8,  '13900000007', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '剧本杀C位琳琳', 2, JSON_ARRAY('剧本杀','川菜','狼人杀','追剧'), '北京市朝阳区望京西园', 'wx_linlin',   1, 1, NOW(), NOW()),
(9,  '13900000008', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '峡谷干饭人',   1, JSON_ARRAY('火锅','夜跑','王者','烧烤'), '北京市海淀区中关村', 'wx_xiagu',    1, 1, NOW(), NOW()),
(10, '13900000009', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '安静的美女子', 2, JSON_ARRAY('咖啡','读书','追剧','烘焙'), '北京市朝阳区朝外大街', 'wx_anning',   1, 1, NOW(), NOW());

INSERT IGNORE INTO tb_shop_type (id, name, icon, sort, create_time, update_time) VALUES
(1, '美食正餐', '🍜', 1, NOW(), NOW()),
(2, '咖啡甜品', '☕', 2, NOW(), NOW()),
(3, '火锅烧烤', '🍲', 3, NOW(), NOW()),
(4, '快餐简餐', '🍱', 4, NOW(), NOW()),
(5, '日料西餐', '🍣', 5, NOW(), NOW()),
(6, '茶饮夜宵', '🧋', 6, NOW(), NOW()),
(7, '运动休闲', '🏃', 7, NOW(), NOW()),
(8, '超市便利', '🏪', 8, NOW(), NOW());

-- ---------- 店铺 1~10：绑定商家 11~20（其中店铺 2 归属 21），全部审核通过 ----------
INSERT IGNORE INTO tb_shop (id, name, merchant_id, type_id, area, address, phone, images, description, lon, lat, score, popularity, audit_status, open_status, audit_time, create_time, update_time) VALUES
(1,  '蜀香居川菜馆',        11, 1, '望京',    '北京市朝阳区望京SOHO T2座',        '010-88880001', 'https://picsum.photos/seed/shop1/600/400',  '地道川味，麻辣鲜香', 116.486000, 39.996000, 4.8, 98, 1, 1, NOW(), NOW(), NOW()),
(2,  '老码头火锅·三里屯',  21, 3, '三里屯',  '北京市朝阳区三里屯SOHO 5号',       '010-88880002', 'https://picsum.photos/seed/shop2/600/400',  '重庆老火锅，牛油锅底', 116.455000, 39.936000, 4.6, 95, 1, 1, NOW(), NOW(), NOW()),
(3,  '慢时光咖啡·甜品',    13, 2, '五道口',  '北京市海淀区五道口华清嘉园',       '010-88880003', 'https://picsum.photos/seed/shop3/600/400',  '手冲咖啡与现烤甜品', 116.338000, 39.992000, 4.7, 82, 1, 1, NOW(), NOW(), NOW()),
(4,  '苏氏牛肉面·国贸',    14, 4, '国贸',    '北京市朝阳区建国门外大街1号',      '010-88880004', 'https://picsum.photos/seed/shop4/600/400',  '一碗好面，三小时吊汤', 116.461000, 39.908000, 4.5, 90, 1, 1, NOW(), NOW(), NOW()),
(5,  '深夜食堂·日料',      15, 5, '亮马桥',  '北京市朝阳区亮马桥路甲20号',       '010-88880005', 'https://picsum.photos/seed/shop5/600/400',  '深夜营业的日式居酒屋', 116.465000, 39.948000, 4.9, 78, 1, 1, NOW(), NOW(), NOW()),
(6,  '茶百味·新式茶饮',    16, 6, '中关村',  '北京市海淀区中关村大街27号',       '010-88880006', 'https://picsum.photos/seed/shop6/600/400',  '现制茶饮，每日鲜果', 116.316000, 39.984000, 4.4, 88, 1, 1, NOW(), NOW(), NOW()),
(7,  '潮汕砂锅粥·前门店',  17, 1, '前门',    '北京市东城区前门大街18号',         '010-88880007', 'https://picsum.photos/seed/shop7/600/400',  '潮汕砂锅粥与广式点心', 116.399000, 39.899000, 4.7, 75, 1, 1, NOW(), NOW(), NOW()),
(8,  '觅见烧烤·夜宵大排档', 18, 6, '朝外',    '北京市朝阳区朝外大街吉祥里',       '010-88880008', 'https://picsum.photos/seed/shop8/600/400',  '炭火烧烤，深夜食堂', 116.443000, 39.923000, 4.6, 85, 1, 1, NOW(), NOW(), NOW()),
(9,  '云上瑜伽·运动空间',  19, 7, '朝阳公园', '北京市朝阳区朝阳公园南路6号',      '010-88880009', 'https://picsum.photos/seed/shop9/600/400',  '哈他/流瑜伽小班课', 116.477000, 39.933000, 4.9, 60, 1, 1, NOW(), NOW(), NOW()),
(10, '京客便利·24小时',    20, 8, '望京',    '北京市朝阳区望京西园四区',         '010-88880010', 'https://picsum.photos/seed/shop10/600/400', '24小时便利店与即食简餐', 116.472000, 39.992000, 4.3, 70, 1, 1, NOW(), NOW(), NOW());

-- ---------- 入驻申请单：1 条已通过（店铺1）+ 1 条待审核（商家13700000002，用于演示审核流程） ----------
-- 注意：待审核申请不关联任何已存在店铺，管理员审核通过后系统会自动创建新店铺
INSERT IGNORE INTO tb_merchant_apply (id, merchant_id, shop_id, type, shop_name, type_id, contact_name, contact_phone, area, address, lon, lat, license_no, license_img, description, status, audit_remark, auditor_id, audit_time, create_time, update_time) VALUES
(1, 11, 1, 1, '蜀香居川菜馆', 1, '王老板', '13700000001', '望京', '北京市朝阳区望京SOHO T2座', 116.486000, 39.996000, '91110105MA001SL001', 'https://picsum.photos/seed/lic1/600/400', '地道川味，麻辣鲜香', 1, '资质齐全，审核通过', 1, NOW(), NOW(), NOW()),
(2, 12, NULL, 1, '老码头火锅·望京二店', 3, '李掌柜', '13700000002', '望京', '北京市朝阳区望京西园三区', 116.474000, 39.993000, '91110105MA001SL002', 'https://picsum.photos/seed/lic2/600/400', '老码头火锅望京二店，主打牛油锅底', 0, NULL, NULL, NULL, NOW(), NOW());

INSERT IGNORE INTO tb_product (id, shop_id, name, images, description, price, stock, sales, status, create_time, update_time) VALUES
(1, 1, '麻婆豆腐', 'https://picsum.photos/seed/p1/400/300', '麻辣鲜香，下饭神器', 1800, 100, 0, 1, NOW(), NOW()),
(2, 1, '水煮鱼', 'https://picsum.photos/seed/p2/400/300', '活鱼现杀，川味经典', 6800, 50, 0, 1, NOW(), NOW()),
(3, 1, '宫保鸡丁', 'https://picsum.photos/seed/p3/400/300', '酸甜微辣，鸡肉嫩滑', 3200, 100, 0, 1, NOW(), NOW()),
(4, 1, '米饭', '', '东北五常大米', 300, 999, 0, 1, NOW(), NOW()),
(5, 1, '冰粉', 'https://picsum.photos/seed/p5/400/300', '川味解辣甜品', 800, 200, 0, 1, NOW(), NOW()),
(6, 2, '鸳鸯锅底', 'https://picsum.photos/seed/p6/400/300', '麻辣+菌汤双拼', 6800, 60, 0, 1, NOW(), NOW()),
(7, 2, '手切鲜羊肉', 'https://picsum.photos/seed/p7/400/300', '现切现卖', 4800, 80, 0, 1, NOW(), NOW()),
(8, 2, '毛肚', 'https://picsum.photos/seed/p8/400/300', '七上八下15秒', 5200, 60, 0, 1, NOW(), NOW()),
(9, 2, '蔬菜拼盘', '', '时令蔬菜组合', 2800, 90, 0, 1, NOW(), NOW()),
(10, 2, '酸梅汤', 'https://picsum.photos/seed/p10/400/300', '解辣必备', 1200, 200, 0, 1, NOW(), NOW()),
(11, 3, '经典美式', 'https://picsum.photos/seed/p11/400/300', 'SOE 单品豆', 2200, 200, 0, 1, NOW(), NOW()),
(12, 3, '燕麦拿铁', 'https://picsum.photos/seed/p12/400/300', '植物奶低负担', 3000, 200, 0, 1, NOW(), NOW()),
(13, 3, '巴斯克芝士蛋糕', 'https://picsum.photos/seed/p13/400/300', '每日现烤', 3200, 40, 0, 1, NOW(), NOW()),
(14, 3, '提拉米苏', 'https://picsum.photos/seed/p14/400/300', '经典意式', 3800, 30, 0, 1, NOW(), NOW()),
(15, 4, '红烧牛肉面', 'https://picsum.photos/seed/p15/400/300', '大块牛肉，汤浓面劲', 2800, 120, 0, 1, NOW(), NOW()),
(16, 4, '兰州牛肉拉面', 'https://picsum.photos/seed/p16/400/300', '一清二白三红四绿', 2200, 150, 0, 1, NOW(), NOW()),
(17, 4, '卤蛋', '', '秘制入味', 200, 500, 0, 1, NOW(), NOW()),
(18, 4, '凉菜拼盘', '', '拍黄瓜+拌木耳', 1500, 80, 0, 1, NOW(), NOW()),
(19, 5, '三文鱼刺身', 'https://picsum.photos/seed/p19/400/300', '挪威冰鲜三文鱼', 5800, 30, 0, 1, NOW(), NOW()),
(20, 5, '鳗鱼饭', 'https://picsum.photos/seed/p20/400/300', '蒲烧鳗鱼盖饭', 4800, 40, 0, 1, NOW(), NOW()),
(21, 5, '玉子烧', '', '日式厚蛋烧', 2600, 50, 0, 1, NOW(), NOW()),
(22, 6, '芝士葡萄', 'https://picsum.photos/seed/p22/400/300', '多肉葡萄+芝士奶盖', 1800, 300, 0, 1, NOW(), NOW()),
(23, 6, '杨枝甘露', 'https://picsum.photos/seed/p23/400/300', '芒果西柚西米露', 2200, 300, 0, 1, NOW(), NOW()),
(24, 6, '珍珠奶茶', 'https://picsum.photos/seed/p24/400/300', '手作珍珠', 1500, 400, 0, 1, NOW(), NOW()),
(25, 7, '鲜虾砂锅粥', 'https://picsum.photos/seed/p25/400/300', '整只鲜虾现熬', 3800, 60, 0, 1, NOW(), NOW()),
(26, 7, '豉汁凤爪', '', '软糯脱骨', 2600, 70, 0, 1, NOW(), NOW()),
(27, 7, '广式肠粉', '', '现蒸肠粉，虾仁馅', 2200, 80, 0, 1, NOW(), NOW()),
(28, 8, '羊肉串(5串)', 'https://picsum.photos/seed/p28/400/300', '炭火现烤', 2500, 300, 0, 1, NOW(), NOW()),
(29, 8, '麻辣小龙虾(中份)', 'https://picsum.photos/seed/p29/400/300', '秘制麻辣卤料', 6800, 60, 0, 1, NOW(), NOW()),
(30, 8, '烤茄子', '', '蒜蓉粉丝烤茄子', 1600, 100, 0, 1, NOW(), NOW()),
(31, 9, '单次瑜伽体验', 'https://picsum.photos/seed/p31/400/300', '哈他/流瑜伽任选', 6800, 50, 0, 1, NOW(), NOW()),
(32, 9, '轻食沙拉餐', '', '课后补给轻食', 3800, 50, 0, 1, NOW(), NOW()),
(33, 10, '农夫山泉(550ml)', '', '天然水', 300, 1000, 0, 1, NOW(), NOW()),
(34, 10, '可口可乐', '', '冰镇畅爽', 400, 1000, 0, 1, NOW(), NOW()),
(35, 10, '照烧鸡腿三明治', 'https://picsum.photos/seed/p35/400/300', '即食简餐', 1200, 200, 0, 1, NOW(), NOW()),
(36, 10, '韩式泡面', '', '深夜加班好搭档', 500, 300, 0, 1, NOW(), NOW());

-- 秒杀券：时间窗 NOW-1天 ~ NOW+7天，保证任何时候都能演示秒杀
INSERT IGNORE INTO tb_voucher (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status, audit_status, stock, sold, begin_time, end_time, create_time, update_time) VALUES
(101, 1, '川菜双人餐代金券', '满100减30 · 每日限量', '每单限用一张，不与其他优惠叠加', 100, 3000, 2, 1, 1, 100, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(102, 4, '牛肉面大满足券', '满50减15 · 手慢无', '仅限堂食/自取', 100, 1500, 2, 1, 1, 100, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(103, 6, '茶百味半价券', '全场通用 · 限量100张', '饮品全场可用', 100, 2000, 2, 1, 1, 100, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(104, 8, '小龙虾5折券', '夜宵党狂喜', '仅限小龙虾品类', 1000, 6800, 2, 1, 1, 50, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(201, 1, '蜀香居无门槛8元券', '新客立减', '无门槛使用', 0, 800, 1, 1, 1, 99999, 0, NULL, NULL, NOW(), NOW()),
(202, 2, '老码头锅底半价券', '到店/外卖可用', '仅限锅底', 0, 3400, 1, 1, 1, 99999, 0, NULL, NULL, NOW(), NOW()),
(203, 3, '咖啡第二杯半价券', '下午茶搭子拼单神器', '同款咖啡两杯起', 0, 1400, 1, 1, 1, 99999, 0, NULL, NULL, NOW(), NOW());

-- 平台审计日志种子（展示审核留痕能力）
INSERT IGNORE INTO tb_audit_log (id, actor_id, actor_role, action, target_type, target_id, detail, create_time) VALUES
(1, 1, 3, 'SHOP_APPROVE', 'SHOP', 1, '审核通过 蜀香居川菜馆「资质齐全，审核通过」', NOW());
