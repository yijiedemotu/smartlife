package com.smartlife.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlife.entity.User;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserMapper extends BaseMapper<User> {

    /**
     * 基于 JSON 标签字段精确匹配（JSON_CONTAINS 精确到数组元素，避免 LIKE 子串误命中）
     */
    @Select("SELECT * FROM tb_user WHERE role = 1 AND JSON_CONTAINS(tags, JSON_QUOTE(#{tag}))")
    List<User> selectByTag(@Param("tag") String tag);

    /** 角色分布统计（平台看板：用户/商家/管理员各多少） */
    @Select("SELECT role AS role, COUNT(*) AS cnt FROM tb_user GROUP BY role ORDER BY role")
    List<Map<String, Object>> selectRoleDistribution();

    /** 新增账号趋势（按天，平台看板） */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS stat_date, COUNT(*) AS cnt " +
            "FROM tb_user WHERE create_time >= #{begin} GROUP BY stat_date ORDER BY stat_date")
    List<Map<String, Object>> selectRegisterTrend(@Param("begin") LocalDateTime begin);
}
