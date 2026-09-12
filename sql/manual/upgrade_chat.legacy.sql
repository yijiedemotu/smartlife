-- =============================================================
-- 智联生活 · 搭子私信/联系方式 增量升级脚本（对已初始化过的库执行一次即可）
-- 用法：mysql ... < sql/upgrade_chat.sql
-- =============================================================
USE smartlife;

-- 1) 用户表新增“微信号/联系方式”列（可空）
ALTER TABLE tb_user
    ADD COLUMN wechat VARCHAR(64) DEFAULT NULL COMMENT '微信号(联系方式)' AFTER address;

-- 2) 私信表
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

-- 3) 给演示用户补齐微信号，便于演示“联系搭子”
UPDATE tb_user SET wechat = 'wx_mishi'    WHERE phone = '13900000001';
UPDATE tb_user SET wechat = 'wx_gandan'   WHERE phone = '13900000002';
UPDATE tb_user SET wechat = 'wx_yepao'    WHERE phone = '13900000003';
UPDATE tb_user SET wechat = 'wx_zhishi'   WHERE phone = '13900000004';
UPDATE tb_user SET wechat = 'wx_dazhuang' WHERE phone = '13900000005';
UPDATE tb_user SET wechat = 'wx_malason'  WHERE phone = '13900000006';
UPDATE tb_user SET wechat = 'wx_linlin'   WHERE phone = '13900000007';
UPDATE tb_user SET wechat = 'wx_xiagu'    WHERE phone = '13900000008';
UPDATE tb_user SET wechat = 'wx_anning'   WHERE phone = '13900000009';
