package com.smartlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.entity.Product;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ProductMapper extends BaseMapper<Product> {

    /**
     * 条件扣减库存并累加销量：where stock >= n 保证不会超卖（原子 SQL 更新）
     */
    @Update("UPDATE tb_product SET stock = stock - #{count}, sales = sales + #{count} " +
            "WHERE id = #{id} AND stock >= #{count} AND status = 1")
    int reduceStock(@Param("id") Long id, @Param("count") Integer count);

    /** 取消订单回补库存 */
    @Update("UPDATE tb_product SET stock = stock + #{count} WHERE id = #{id}")
    int addStock(@Param("id") Long id, @Param("count") Integer count);
}
