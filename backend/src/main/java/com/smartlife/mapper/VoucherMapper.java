package com.smartlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.entity.Voucher;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface VoucherMapper extends BaseMapper<Voucher> {

    /**
     * DB 层兜底扣库存：乐观条件更新 stock>0，防数据库层面超卖
     */
    @Update("UPDATE tb_voucher SET stock = stock - 1, sold = sold + 1 WHERE id = #{id} AND stock > 0")
    int cutStock(@Param("id") Long id);
}
