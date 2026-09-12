-- =============================================================
-- 智联生活 · 数据扩充脚本（一次性）
-- 用法：mysql ... < sql/more_seed_data.sql
-- 说明：固定主键 + INSERT IGNORE；订单/券订单等按外键一致性设计
-- =============================================================
USE smartlife;

-- ---------- 1. tb_user：再补 8 位饭搭子 ----------
INSERT IGNORE INTO tb_user (id, phone, password, nickname, gender, tags, address, wechat, role, create_time, update_time) VALUES
(11, '13900000010', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '夜猫子小柒', 2, JSON_ARRAY('夜跑','烧烤','追剧','狼人杀'), '北京市朝阳区双井', 'wx_xiaoqi', 1, NOW(), NOW()),
(12, '13900000011', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '川菜胃老饕', 1, JSON_ARRAY('川菜','火锅','美食探店','啤酒'), '北京市朝阳区团结湖', 'wx_laotao', 1, NOW(), NOW()),
(13, '13900000012', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '咖啡因研究所', 1, JSON_ARRAY('咖啡','烘焙','读书','自习'), '北京市海淀区中关村', 'wx_cafe', 1, NOW(), NOW()),
(14, '13900000013', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '羽毛球小王子', 1, JSON_ARRAY('运动','夜跑','奶茶','健身'), '北京市丰台区方庄', 'wx_badminton', 1, NOW(), NOW()),
(15, '13900000014', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '桌游组局王', 1, JSON_ARRAY('剧本杀','狼人杀','川菜','桌游'), '北京市海淀区五道口', 'wx_zhuoyou', 1, NOW(), NOW()),
(16, '13900000015', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '甜品暴击选手', 2, JSON_ARRAY('甜品','追剧','咖啡','养猫'), '北京市朝阳区国贸', 'wx_tianpin', 1, NOW(), NOW()),
(17, '13900000016', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '面食爱好者', 1, JSON_ARRAY('面食','简餐','日料','旅行'), '北京市东城区东直门', 'wx_mianshi', 1, NOW(), NOW()),
(18, '13900000017', 'seed:5f7ddeeb7625016332e4d62f529ba782312294e62c531a5b67ffd93ef04b2042', '环球飞行中', 2, JSON_ARRAY('旅行','美食','摄影','咖啡'), '北京市顺义区空港', 'wx_fly', 1, NOW(), NOW());

-- ---------- 2. tb_shop_type：加两类 ----------
INSERT IGNORE INTO tb_shop_type (id, name, icon, sort, create_time, update_time) VALUES
(9, '面包烘焙', '🥐', 9, NOW(), NOW()),
(10, '书店自习', '📚', 10, NOW(), NOW());

-- ---------- 3. tb_shop：补 6 家店 ----------
INSERT IGNORE INTO tb_shop (id, name, type_id, area, address, images, lon, lat, score, popularity, create_time, update_time) VALUES
(11, '麦香烘焙坊', 9, '望京', '北京市朝阳区望京凯德MALL一层', 'https://picsum.photos/seed/shop11/600/400', 116.469000, 39.994000, 4.7, 66, NOW(), NOW()),
(12, '单向空间·自习书店', 10, '中关村', '北京市海淀区中关村创业大街', 'https://picsum.photos/seed/shop12/600/400', 116.310000, 39.983000, 4.8, 72, NOW(), NOW()),
(13, '老北京炸酱面·什刹海', 1, '什刹海', '北京市西城区地安门外大街', 'https://picsum.photos/seed/shop13/600/400', 116.390000, 39.940000, 4.6, 80, NOW(), NOW()),
(14, '谭鸭血老火锅·双井', 3, '双井', '北京市朝阳区广渠路36号', 'https://picsum.photos/seed/shop14/600/400', 116.463000, 39.892000, 4.5, 88, NOW(), NOW()),
(15, '茶屿·鲜果茶', 6, '大望路', '北京市朝阳区西大望路万达广场', 'https://picsum.photos/seed/shop15/600/400', 116.478000, 39.908000, 4.4, 76, NOW(), NOW()),
(16, '好麦客·西式快餐', 4, '回龙观', '北京市昌平区回龙观西大街', 'https://picsum.photos/seed/shop16/600/400', 116.337000, 40.066000, 4.3, 62, NOW(), NOW());

-- ---------- 4. tb_product：补商品（id 101 起） ----------
INSERT IGNORE INTO tb_product (id, shop_id, name, images, description, price, stock, sales, status, create_time, update_time) VALUES
(101, 11, '手作生吐司(整条)', 'https://picsum.photos/seed/p101/400/300', '当日现烤，奶香浓郁', 2800, 120, 0, 1, NOW(), NOW()),
(102, 11, '黄油可颂', 'https://picsum.photos/seed/p102/400/300', '27层酥皮', 1500, 200, 0, 1, NOW(), NOW()),
(103, 11, '海盐卷', 'https://picsum.photos/seed/p103/400/300', '外脆内软', 1200, 160, 0, 1, NOW(), NOW()),
(111, 12, '美式咖啡', 'https://picsum.photos/seed/p111/400/300', '自习伴侣', 1800, 300, 0, 1, NOW(), NOW()),
(112, 12, '自习3小时体验券', '', '安静工位+WiFi+免费续水', 1500, 200, 0, 1, NOW(), NOW()),
(113, 12, '冰柠茶', 'https://picsum.photos/seed/p113/400/300', '清爽解乏', 1600, 260, 0, 1, NOW(), NOW()),
(121, 13, '老北京炸酱面', 'https://picsum.photos/seed/p121/400/300', '菜码八样，肉丁酱香', 2600, 150, 0, 1, NOW(), NOW()),
(122, 13, '猪肉大葱打卤面', '', '浓卤浇头', 2400, 140, 0, 1, NOW(), NOW()),
(123, 13, '老北京炒肝', '', '蒜香浓郁', 1800, 120, 0, 1, NOW(), NOW()),
(124, 13, '北冰洋汽水', '', '一口回到童年', 800, 400, 0, 1, NOW(), NOW()),
(131, 14, '招牌鸭血', 'https://picsum.photos/seed/p131/400/300', '每日空运鲜鸭血', 2600, 90, 0, 1, NOW(), NOW()),
(132, 14, '手切牛肉', 'https://picsum.photos/seed/p132/400/300', '不注水好肉', 4600, 100, 0, 1, NOW(), NOW()),
(133, 14, '千层肚', '', '脆爽弹牙', 4200, 80, 0, 1, NOW(), NOW()),
(134, 14, '红糖冰粉', '', '解辣甜品', 800, 150, 0, 1, NOW(), NOW()),
(141, 15, '芝士葡萄', 'https://picsum.photos/seed/p141/400/300', '大颗葡萄+芝士奶盖', 1800, 320, 0, 1, NOW(), NOW()),
(142, 15, '牛油果奶昔', 'https://picsum.photos/seed/p142/400/300', '现切牛油果', 2200, 200, 0, 1, NOW(), NOW()),
(143, 15, '脆啵啵奶茶', '', '黑糖珍珠脆啵啵', 1600, 360, 0, 1, NOW(), NOW()),
(151, 16, '巨无霸鸡肉卷', 'https://picsum.photos/seed/p151/400/300', '整块鸡腿肉', 2200, 260, 0, 1, NOW(), NOW()),
(152, 16, '黄金薯条', '', '现炸香脆', 900, 500, 0, 1, NOW(), NOW()),
(153, 16, '冰镇可乐', '', '畅快解腻', 500, 600, 0, 1, NOW(), NOW()),
(154, 16, '香辣鸡翅(3只)', 'https://picsum.photos/seed/p154/400/300', '外酥里嫩', 1400, 280, 0, 1, NOW(), NOW());

-- ---------- 5. tb_voucher：新店优惠券（含 2 张秒杀券） ----------
INSERT IGNORE INTO tb_voucher (id, shop_id, title, sub_title, rules, pay_value, actual_value, type, status, stock, sold, begin_time, end_time, create_time, update_time) VALUES
(301, 11, '麦香满30减10券', '烘焙新品尝鲜', '单笔满30可用', 0, 1000, 1, 1, 99999, 0, NULL, NULL, NOW(), NOW()),
(302, 12, '自习畅饮秒杀券', '含美式/冰柠任选 · 限60张', '仅限堂食自习使用', 100, 1500, 2, 1, 60, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(303, 13, '老北京面满40减12', '家常味也要省', '单笔满40可用', 0, 1200, 1, 1, 99999, 0, NULL, NULL, NOW(), NOW()),
(304, 14, '招牌鸭血免单券', '点锅即送 · 限80张', '仅限鸭血品类', 200, 2600, 2, 1, 80, 0, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 7 DAY), NOW(), NOW()),
(305, 15, '鲜果茶第二杯半价券', '和搭子拼单吧', '同款两杯起用', 0, 800, 1, 1, 99999, 0, NULL, NULL, NOW(), NOW());

-- ---------- 6. tb_voucher_order：普通券领用记录 ----------
INSERT IGNORE INTO tb_voucher_order (id, user_id, voucher_id, shop_id, voucher_title, actual_value, status, create_time, use_time) VALUES
(1001, 2, 201, 1, '蜀香居无门槛8元券', 800, 2, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(1002, 3, 201, 1, '蜀香居无门槛8元券', 800, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), NULL),
(1003, 4, 202, 2, '老码头锅底半价券', 3400, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), NULL),
(1004, 5, 203, 3, '咖啡第二杯半价券', 1400, 1, DATE_SUB(NOW(), INTERVAL 12 HOUR), NULL),
(1005, 12, 201, 1, '蜀香居无门槛8元券', 800, 1, NOW(), NULL),
(1006, 13, 303, 13, '老北京面满40减12', 1200, 1, NOW(), NULL);

-- ---------- 7. tb_orders / tb_order_item：历史订单与明细 ----------
INSERT IGNORE INTO tb_orders (id, number, user_id, shop_id, shop_name, address, amount, status, remark, pay_time, create_time, update_time) VALUES
(1, 'SL1000000000001', 2, 1, '蜀香居川菜馆', '北京市朝阳区望京SOHO T1座', 3200, 5, '不要香菜', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 'SL1000000000002', 3, 2, '老码头火锅·三里屯', '北京市朝阳区三里屯SOHO', 8000, 5, NULL, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
(3, 'SL1000000000003', 4, 6, '茶百味·新式茶饮', '北京市海淀区五道口', 5100, 4, '少冰', DATE_SUB(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 45 MINUTE), NOW()),
(4, 'SL1000000000004', 5, 3, '慢时光咖啡·甜品', '北京市朝阳区国贸CBD', 5400, 3, NULL, DATE_SUB(NOW(), INTERVAL 1 HOUR), DATE_SUB(NOW(), INTERVAL 90 MINUTE), NOW()),
(5, 'SL1000000000005', 6, 5, '深夜食堂·日料', '北京市朝阳区亮马桥', 10600, 2, '刺身多放芥末', DATE_SUB(NOW(), INTERVAL 10 MINUTE), DATE_SUB(NOW(), INTERVAL 15 MINUTE), NOW()),
(6, 'SL1000000000006', 2, 8, '觅见烧烤·夜宵大排档', '北京市朝阳区望京SOHO T1座', 14300, 5, NULL, DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
(7, 'SL1000000000007', 7, 10, '京客便利·24小时', '北京市西城区西单', 1900, 1, '放门口即可', NULL, DATE_SUB(NOW(), INTERVAL 5 MINUTE), NOW()),
(8, 'SL1000000000008', 3, 1, '蜀香居川菜馆', '北京市朝阳区三里屯SOHO', 3500, 6, NULL, NULL, DATE_SUB(NOW(), INTERVAL 1 DAY), NOW());

INSERT IGNORE INTO tb_order_item (id, order_id, product_id, product_name, product_image, price, count) VALUES
(101, 1, 1, '麻婆豆腐', 'https://picsum.photos/seed/p1/400/300', 1800, 1),
(102, 1, 4, '米饭', '', 300, 2),
(103, 1, 5, '冰粉', 'https://picsum.photos/seed/p5/400/300', 800, 1),
(201, 2, 6, '鸳鸯锅底', 'https://picsum.photos/seed/p6/400/300', 6800, 1),
(202, 2, 10, '酸梅汤', 'https://picsum.photos/seed/p10/400/300', 1200, 1),
(301, 3, 22, '芝士葡萄', 'https://picsum.photos/seed/p22/400/300', 1800, 2),
(302, 3, 24, '珍珠奶茶', 'https://picsum.photos/seed/p24/400/300', 1500, 1),
(401, 4, 13, '巴斯克芝士蛋糕', 'https://picsum.photos/seed/p13/400/300', 3200, 1),
(402, 4, 11, '经典美式', 'https://picsum.photos/seed/p11/400/300', 2200, 1),
(501, 5, 19, '三文鱼刺身', 'https://picsum.photos/seed/p19/400/300', 5800, 1),
(502, 5, 20, '鳗鱼饭', 'https://picsum.photos/seed/p20/400/300', 4800, 1),
(601, 6, 28, '羊肉串(5串)', 'https://picsum.photos/seed/p28/400/300', 2500, 3),
(602, 6, 29, '麻辣小龙虾(中份)', 'https://picsum.photos/seed/p29/400/300', 6800, 1),
(701, 7, 33, '农夫山泉(550ml)', '', 300, 2),
(702, 7, 34, '可口可乐', '', 400, 2),
(703, 7, 36, '韩式泡面', '', 500, 1),
(801, 8, 3, '宫保鸡丁', 'https://picsum.photos/seed/p3/400/300', 3200, 1),
(802, 8, 4, '米饭', '', 300, 1);

-- ---------- 8. 一致性回写：库存/销量按已支付及以上订单扣减 ----------
UPDATE tb_product p
JOIN (SELECT oi.product_id, SUM(oi.count) AS c
      FROM tb_order_item oi
      JOIN tb_orders o ON oi.order_id = o.id
      WHERE o.status <> 6
      GROUP BY oi.product_id) t ON p.id = t.product_id
SET p.sales = p.sales + t.c,
    p.stock = GREATEST(0, p.stock - t.c);

-- 券销量/库存回写
UPDATE tb_voucher v
JOIN (SELECT voucher_id, COUNT(*) AS c FROM tb_voucher_order GROUP BY voucher_id) t ON v.id = t.voucher_id
SET v.sold = v.sold + t.c,
    v.stock = GREATEST(0, v.stock - t.c);

-- ---------- 9. tb_message：补一些搭子私信（并清理早期乱码测试消息） ----------
DELETE FROM tb_message WHERE id IN (1, 2);

INSERT IGNORE INTO tb_message (id, from_id, to_id, content, read_flag, create_time) VALUES
(1001, 8, 2, '嗨小鱼！看到你也玩剧本杀，周末正好有人组局，要不要一起来？', 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(1002, 2, 8, '好呀好呀！我剧本杀有点菜但超爱玩，大概几点呀？', 0, DATE_SUB(NOW(), INTERVAL 110 MINUTE)),
(1003, 8, 2, '周六下午两点，望京这边的店，我拉你进群~', 0, DATE_SUB(NOW(), INTERVAL 100 MINUTE)),
(1004, 4, 2, '明晚七点奥森南门夜跑约不约？跑完一起撸串', 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
(1005, 5, 2, '国贸那家巴斯克蛋糕你尝了吗？想找搭子拼单下午茶', 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
(1006, 12, 3, '老哥，周末找两家川菜馆子探店？我知道一家新开的', 0, DATE_SUB(NOW(), INTERVAL 20 MINUTE)),
(1007, 13, 5, '自习完想喝杯咖啡，单向空间新出的冰柠茶不错', 0, DATE_SUB(NOW(), INTERVAL 3 HOUR)),
(1008, 15, 3, '下周四狼人杀缺一个高配，来不来？', 0, DATE_SUB(NOW(), INTERVAL 15 MINUTE));
