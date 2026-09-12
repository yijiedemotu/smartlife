package com.smartlife.common;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Redis Key 规范集中管理（防止散落字符串写错）
 */
public class RedisConstants {

    /** 商铺详情缓存  cache:shop:{id}  含逻辑过期/空值标记 */
    public static final String SHOP_CACHE_KEY = "cache:shop:";
    /** 商铺详情互斥锁（缓存击穿重建锁） lock:shop:{id} */
    public static final String SHOP_LOCK_KEY = "lock:shop:";
    /** 店铺类型缓存 */
    public static final String SHOP_TYPE_KEY = "cache:shopType";
    /** 热点商铺列表（定时任务预热） cache:shop:hot */
    public static final String HOT_SHOP_KEY = "cache:shop:hot";
    /** 某店铺可领取优惠券列表缓存 cache:voucher:shop:{id} */
    public static final String SHOP_VOUCHER_KEY = "cache:voucher:shop:";
    /** 秒杀场次券列表缓存 cache:seckill:list */
    public static final String SECKILL_LIST_KEY = "cache:seckill:list";
    /** 秒杀库存(Redis 原子扣减) seckill:stock:{voucherId} */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    /** 秒杀一人一单集合 seckill:user:{voucherId} */
    public static final String SECKILL_USER_KEY = "seckill:user:";
    /** 店铺 GEO 索引（typeId=0 表示全部） shop:geo:{typeId} */
    public static final String SHOP_GEO_KEY = "shop:geo:";
    /** 购物车 Hash cart:{userId}  field=productId value=数量 */
    public static final String CART_KEY = "cart:";
    /** 饭搭子推荐结果缓存 mate:recommend:{userId} */
    public static final String MATE_RECOMMEND_KEY = "mate:recommend:";
    /** 推荐结果缓存 TTL 10 分钟 */
    public static final long MATE_RECOMMEND_TTL = 10 * 60;

    public static String shopCacheKey(Long id) { return SHOP_CACHE_KEY + id; }

    public static String shopLockKey(Long id) { return SHOP_LOCK_KEY + id; }

    public static String shopGeoKey(Long typeId) { return SHOP_GEO_KEY + (typeId == null ? 0 : typeId); }

    public static String seckillStockKey(Long voucherId) { return SECKILL_STOCK_KEY + voucherId; }

    public static String seckillUserKey(Long voucherId) { return SECKILL_USER_KEY + voucherId; }

    public static String cartKey(Long userId) { return CART_KEY + userId; }

    public static String mateRecommendKey(Long userId) { return MATE_RECOMMEND_KEY + userId; }

    /** 签到 BitMap: sign:{userId}:{yyyyMM} */
    public static String signKey(Long userId, LocalDate date) {
        return "sign:" + userId + ":" + date.format(DateTimeFormatter.ofPattern("yyyyMM"));
    }

    /** UV HyperLogLog: uv:{yyyy-MM-dd}:{pageKey} */
    public static String uvKey(LocalDate date, String page) {
        return "uv:" + date.format(DateTimeFormatter.ISO_LOCAL_DATE) + ":" + page;
    }
}
