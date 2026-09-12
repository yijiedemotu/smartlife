package com.smartlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.entity.Shop;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

public interface ShopMapper extends BaseMapper<Shop> {

    /** 店铺审核状态分布（平台看板：待审核/营业中/已驳回/已停业） */
    @Select("SELECT audit_status AS auditStatus, COUNT(*) AS cnt FROM tb_shop " +
            "GROUP BY audit_status ORDER BY audit_status")
    List<Map<String, Object>> selectAuditDistribution();

    /** 商家带货能力排行（平台端：按有效订单 GMV 排行，附店铺名与商家 id） */
    @Select("SELECT s.id AS shopId, s.name AS shopName, s.merchant_id AS merchantId, " +
            "COUNT(o.id) AS cnt, COALESCE(SUM(CASE WHEN o.status IN (2,3,4,5) THEN o.amount ELSE 0 END), 0) AS gmv " +
            "FROM tb_shop s LEFT JOIN tb_orders o ON o.shop_id = s.id " +
            "GROUP BY s.id, s.name, s.merchant_id ORDER BY gmv DESC LIMIT #{limit}")
    List<Map<String, Object>> selectShopRank(int limit);
}
