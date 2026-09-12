package com.smartlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.entity.Orders;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface OrdersMapper extends BaseMapper<Orders> {

    /** 近 N 天订单量/GMV 趋势（剔除已取消），全平台 */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS stat_date, COUNT(*) AS cnt, " +
            "COALESCE(SUM(CASE WHEN status IN (2,3,4,5) THEN amount ELSE 0 END), 0) AS gmv " +
            "FROM tb_orders WHERE create_time >= #{begin} AND status <> 6 " +
            "GROUP BY stat_date ORDER BY stat_date")
    List<Map<String, Object>> selectDailyTrend(@Param("begin") LocalDateTime begin);

    /** 近 N 天订单量/GMV 趋势（单店铺，商家端看板） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS stat_date, COUNT(*) AS cnt, " +
            "COALESCE(SUM(CASE WHEN status IN (2,3,4,5) THEN amount ELSE 0 END), 0) AS gmv " +
            "FROM tb_orders WHERE shop_id = #{shopId} AND create_time >= #{begin} AND status <> 6 " +
            "GROUP BY stat_date ORDER BY stat_date")
    List<Map<String, Object>> selectDailyTrendByShop(@Param("shopId") Long shopId,
                                                     @Param("begin") LocalDateTime begin);

    /** 销售额 Top 店铺（平台端） */
    @Select("SELECT s.id AS shopId, s.name AS shopName, COUNT(o.id) AS cnt, " +
            "COALESCE(SUM(o.amount), 0) AS gmv " +
            "FROM tb_orders o JOIN tb_shop s ON o.shop_id = s.id " +
            "WHERE o.status IN (2,3,4,5) " +
            "GROUP BY s.id, s.name ORDER BY gmv DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopShops(@Param("limit") int limit);

    /**
     * 订单量统计：shopId / begin / status 均可为空表示不限制
     * 统计口径：GMV 只计已支付及之后的有效单，订单量含全部状态
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM tb_orders WHERE 1 = 1 " +
            "<if test='shopId != null'> AND shop_id = #{shopId} </if>" +
            "<if test='begin != null'> AND create_time &gt;= #{begin} </if>" +
            "<if test='status != null'> AND status = #{status} </if>" +
            "</script>")
    long countOrders(@Param("shopId") Long shopId,
                     @Param("begin") LocalDateTime begin,
                     @Param("status") Integer status);

    /** GMV 统计（仅计 status in (2,3,4,5) 的有效单） */
    @Select("<script>" +
            "SELECT COALESCE(SUM(amount), 0) FROM tb_orders WHERE status IN (2,3,4,5) " +
            "<if test='shopId != null'> AND shop_id = #{shopId} </if>" +
            "<if test='begin != null'> AND create_time &gt;= #{begin} </if>" +
            "</script>")
    long sumGmv(@Param("shopId") Long shopId, @Param("begin") LocalDateTime begin);

    /** 订单状态分布（平台端/商家端看板饼图） */
    @Select("<script>" +
            "SELECT status AS status, COUNT(*) AS cnt FROM tb_orders WHERE 1 = 1 " +
            "<if test='shopId != null'> AND shop_id = #{shopId} </if>" +
            "GROUP BY status ORDER BY status" +
            "</script>")
    List<Map<String, Object>> selectStatusDistribution(@Param("shopId") Long shopId);

    /** 店铺热销商品 TopN（按累计成交件数，商家端看板） */
    @Select("SELECT i.product_id AS productId, i.product_name AS productName, " +
            "SUM(i.count) AS sales, SUM(i.count * i.price) AS gmv " +
            "FROM tb_order_item i JOIN tb_orders o ON o.id = i.order_id " +
            "WHERE o.shop_id = #{shopId} AND o.status IN (2,3,4,5) " +
            "GROUP BY i.product_id, i.product_name ORDER BY sales DESC LIMIT #{limit}")
    List<Map<String, Object>> selectTopProducts(@Param("shopId") Long shopId, @Param("limit") int limit);
}
