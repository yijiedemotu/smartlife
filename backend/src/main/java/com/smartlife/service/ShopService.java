package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.smartlife.common.BusinessException;
import com.smartlife.common.JsonUtils;
import com.smartlife.common.RedisConstants;
import com.smartlife.common.RoleConstants;
import com.smartlife.entity.Shop;
import com.smartlife.entity.ShopType;
import com.smartlife.mapper.ShopMapper;
import com.smartlife.mapper.ShopTypeMapper;
import com.smartlife.vo.ShopNearVO;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 商铺服务 —— 高并发查询治理核心
 *
 * 多级缓存架构：L1 Caffeine 本地缓存(5s) -> L2 Redis(随机TTL) -> DB
 * 缓存保护：布隆过滤器(穿透拦截) + 空值缓存(穿透) + 互斥锁重建(击穿) + 随机TTL(雪崩)
 * LBS：Redis GEO 实现附近店铺搜索
 */
@Service
public class ShopService {

    /** Redis 缓存中的空值标记（防穿透） */
    private static final String MARKER = "EMPTY";

    private final ShopMapper shopMapper;
    private final ShopTypeMapper shopTypeMapper;
    private final StringRedisTemplate redis;

    /** L1 本地缓存：热点店铺详情 5s */
    private final Cache<Long, Shop> localCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(Duration.ofSeconds(5))
            .build();

    /** 布隆过滤器：拦截不存在 id 的请求，避免穿透打到 DB */
    private final BloomFilter<Long> bloom = BloomFilter.create(Funnels.longFunnel(), 10000, 0.01);

    private final Random random = new Random();

    public ShopService(ShopMapper shopMapper, ShopTypeMapper shopTypeMapper, StringRedisTemplate redis) {
        this.shopMapper = shopMapper;
        this.shopTypeMapper = shopTypeMapper;
        this.redis = redis;
    }

    // ==================== 查询 ====================

    /**
     * 店铺详情：本地缓存 -> Redis(空值/随机TTL) -> 互斥锁重建 -> DB
     *
     * 用户端可见性：仅审核通过且正常营业的店铺对外可见；
     * 未通过/已停业的店铺对用户端返回 null（商家在自己的后台通过 merchantShop() 查看原貌）。
     */
    public Shop queryById(Long id) {
        // L1 本地缓存
        Shop shop = localCache.getIfPresent(id);
        if (shop != null) {
            return shop;
        }
        // 布隆过滤器：不存在的 id 直接返回，杜绝穿透
        if (!bloom.mightContain(id)) {
            return null;
        }
        // L2 Redis 缓存
        String key = RedisConstants.shopCacheKey(id);
        String json = redis.opsForValue().get(key);
        if (json != null) {
            if (MARKER.equals(json)) {
                return null;
            }
            shop = JsonUtils.fromJson(json, Shop.class);
            localCache.put(id, shop);
            return shop;
        }
        // 缓存击穿：互斥锁重建，防止热点 key 过期瞬间大量请求打到 DB
        return rebuildByMutex(id, key);
    }

    /** 互斥锁重建缓存：抢到锁的线程查 DB 并回填缓存，未抢到锁的线程短暂自旋等待 */
    private Shop rebuildByMutex(Long id, String key) {
        String lockKey = RedisConstants.shopLockKey(id);
        String token = UUID.randomUUID().toString();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, token, 10, TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(locked)) {
            try {
                // 双重检查：可能其它线程已重建
                String again = redis.opsForValue().get(key);
                if (again != null) {
                    return MARKER.equals(again) ? null : JsonUtils.fromJson(again, Shop.class);
                }
                Shop db = shopMapper.selectById(id);
                if (db == null) {
                    // 空值缓存 5 分钟，防止恶意遍历不存在 id
                    redis.opsForValue().set(key, MARKER, 5, TimeUnit.MINUTES);
                    return null;
                }
                // 审核未通过 / 已停业的店铺对用户端不可见（等价于不存在）
                if (!RoleConstants.visibleToUser(db)) {
                    redis.opsForValue().set(key, MARKER, 2, TimeUnit.MINUTES);
                    return null;
                }
                writeDetail(db);
                return db;
            } finally {
                // 只有持有者才释放锁（防止误删他人锁）
                String val = redis.opsForValue().get(lockKey);
                if (token.equals(val)) {
                    redis.delete(lockKey);
                }
            }
        }
        // 未抢到锁：短暂自旋等待其它线程重建，超时后兜底查库（仍保证最终一致）
        for (int i = 0; i < 10; i++) {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            String v = redis.opsForValue().get(key);
            if (v != null) {
                return MARKER.equals(v) ? null : JsonUtils.fromJson(v, Shop.class);
            }
        }
        Shop db = shopMapper.selectById(id);
        if (db == null) {
            return null;
        }
        writeDetail(db);
        return db;
    }

    /** 写入详情缓存：随机 TTL 20~40min，错峰过期避免雪崩 */
    private void writeDetail(Shop shop) {
        long ttl = 1200 + random.nextInt(1200);
        redis.opsForValue().set(RedisConstants.shopCacheKey(shop.getId()),
                JsonUtils.toJson(shop), ttl, TimeUnit.SECONDS);
    }

    /**
     * 分页浏览店铺（typeId 为空查全部；keyword 匹配店名/商圈）
     * 用户端口径：仅审核通过 + 正常营业的店铺
     */
    public List<Shop> pageShops(Integer page, Integer size, Long typeId, String keyword) {
        LambdaQueryWrapper<Shop> wrapper = new LambdaQueryWrapper<Shop>()
                .eq(Shop::getAuditStatus, RoleConstants.SHOP_AUDIT_APPROVED)
                .eq(Shop::getOpenStatus, RoleConstants.SHOP_OPEN)
                .eq(typeId != null, Shop::getTypeId, typeId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Shop::getName, keyword).or().like(Shop::getArea, keyword))
                .orderByDesc(Shop::getPopularity);
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 50);
        return shopMapper.selectList(wrapper.last("LIMIT " + (p - 1) * s + "," + s));
    }

    /** 平台/商家端店铺分页：可按审核状态、归属商家、关键字过滤（不限制可见性） */
    public List<Shop> pageShopsForAdmin(Integer page, Integer size, Long typeId, Long merchantId,
                                        Integer auditStatus, String keyword) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        return shopMapper.selectList(buildAdminWrapper(typeId, merchantId, auditStatus, keyword)
                .orderByDesc(Shop::getCreateTime)
                .last("LIMIT " + (p - 1) * s + "," + s));
    }

    /** 平台/商家端店铺分页总数 */
    public long countShopsForAdmin(Long typeId, Long merchantId, Integer auditStatus, String keyword) {
        Long count = shopMapper.selectCount(buildAdminWrapper(typeId, merchantId, auditStatus, keyword));
        return count == null ? 0 : count;
    }

    private LambdaQueryWrapper<Shop> buildAdminWrapper(Long typeId, Long merchantId,
                                                       Integer auditStatus, String keyword) {
        return new LambdaQueryWrapper<Shop>()
                .eq(typeId != null, Shop::getTypeId, typeId)
                .eq(merchantId != null, Shop::getMerchantId, merchantId)
                .eq(auditStatus != null, Shop::getAuditStatus, auditStatus)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Shop::getName, keyword).or().like(Shop::getArea, keyword));
    }

    /**
     * 附近店铺：Redis GEO 检索
     */
    public List<ShopNearVO> nearby(Double lon, Double lat, Long typeId, Double radiusKm) {
        String key = RedisConstants.shopGeoKey(typeId);
        List<ShopNearVO> result = new ArrayList<>();
        if (lon == null || lat == null) {
            return result;
        }
        RedisGeoCommands.GeoRadiusCommandArgs args = RedisGeoCommands.GeoRadiusCommandArgs
                .newGeoRadiusArgs().includeDistance().sortAscending().limit(20);
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults =
                redis.opsForGeo().radius(key,
                        new Circle(new Point(lon, lat), new Distance(radiusKm == null ? 5 : radiusKm,
                                RedisGeoCommands.DistanceUnit.KILOMETERS)), args);
        if (geoResults == null) {
            return result;
        }
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : geoResults.getContent()) {
            Long shopId = Long.valueOf(r.getContent().getName());
            Shop shop = queryById(shopId); // 命中多级缓存
            if (shop == null) {
                continue;
            }
            ShopNearVO vo = new ShopNearVO();
            vo.setShop(shop);
            vo.setDistanceM((int) Math.round(r.getDistance().getValue() * 1000));
            result.add(vo);
        }
        return result;
    }

    /** 热点店铺：优先读定时任务预热好的缓存，miss 时 DB 兜底并回填 */
    public List<Shop> hotShops() {
        String json = redis.opsForValue().get(RedisConstants.HOT_SHOP_KEY);
        if (json != null) {
            List<Shop> cached = JsonUtils.toList(json, Shop.class);
            if (cached != null && !cached.isEmpty()) {
                return cached;
            }
        }
        List<Shop> shops = shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getAuditStatus, RoleConstants.SHOP_AUDIT_APPROVED)
                .eq(Shop::getOpenStatus, RoleConstants.SHOP_OPEN)
                .orderByDesc(Shop::getPopularity).last("LIMIT 20"));
        if (!shops.isEmpty()) {
            redis.opsForValue().set(RedisConstants.HOT_SHOP_KEY, JsonUtils.toJson(shops),
                    30, TimeUnit.MINUTES);
        }
        return shops;
    }

    // ==================== 店铺类型 ====================

    public List<ShopType> listTypes() {
        String json = redis.opsForValue().get(RedisConstants.SHOP_TYPE_KEY);
        if (json != null) {
            List<ShopType> cached = JsonUtils.toList(json, ShopType.class);
            if (cached != null) {
                return cached;
            }
        }
        List<ShopType> types = shopTypeMapper.selectList(
                new LambdaQueryWrapper<ShopType>().orderByAsc(ShopType::getSort));
        redis.opsForValue().set(RedisConstants.SHOP_TYPE_KEY, JsonUtils.toJson(types),
                12, TimeUnit.HOURS);
        return types;
    }

    public void saveType(ShopType type) {
        if (type.getId() == null) {
            shopTypeMapper.insert(type);
        } else {
            shopTypeMapper.updateById(type);
        }
        redis.delete(RedisConstants.SHOP_TYPE_KEY);
    }

    public void removeType(Long id) {
        shopTypeMapper.deleteById(id);
        redis.delete(RedisConstants.SHOP_TYPE_KEY);
    }

    /**
     * 管理端/商家端保存店铺（保证缓存/索引一致性）
     * 注意：商家端调用前必须已通过 MerchantService.assertShopOwnership 归属校验
     */
    public void saveShop(Shop shop) {
        if (shop.getId() == null) {
            shopMapper.insert(shop);
        } else {
            shopMapper.updateById(shop);
        }
        // 本地缓存立即失效，避免商家改价/改状态后用户端读到旧值
        localCache.invalidate(shop.getId());
        if (RoleConstants.visibleToUser(shop)) {
            // 回填多级缓存
            writeDetail(shop);
            addToGeo(shop);
            bloom.put(shop.getId());
        } else {
            // 状态变为不可见：清掉详情缓存与 GEO 索引
            redis.delete(RedisConstants.shopCacheKey(shop.getId()));
            redis.opsForGeo().remove(RedisConstants.shopGeoKey(0L), String.valueOf(shop.getId()));
            if (shop.getTypeId() != null) {
                redis.opsForGeo().remove(RedisConstants.shopGeoKey(shop.getTypeId()), String.valueOf(shop.getId()));
            }
        }
        // 热点列表与旧缓存作废
        redis.delete(RedisConstants.HOT_SHOP_KEY);
        if (shop.getTypeId() != null) {
            redis.delete(RedisConstants.SHOP_TYPE_KEY);
        }
    }

    public void removeShop(Long id) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            return;
        }
        shopMapper.deleteById(id);
        localCache.invalidate(id);
        redis.delete(RedisConstants.shopCacheKey(id));
        redis.opsForGeo().remove(RedisConstants.shopGeoKey(0L), String.valueOf(id));
        if (shop.getTypeId() != null) {
            redis.opsForGeo().remove(RedisConstants.shopGeoKey(shop.getTypeId()), String.valueOf(id));
        }
        redis.delete(RedisConstants.HOT_SHOP_KEY);
        // 布隆过滤器不支持删除，靠定期重建索引兜底
    }

    private void addToGeo(Shop shop) {
        String member = String.valueOf(shop.getId());
        Point point = new Point(shop.getLon(), shop.getLat());
        redis.opsForGeo().add(RedisConstants.shopGeoKey(0L), point, member);
        if (shop.getTypeId() != null) {
            redis.opsForGeo().add(RedisConstants.shopGeoKey(shop.getTypeId()), point, member);
        }
    }

    /**
     * 启动/重建索引：GEO 全量重建 + 布隆过滤器装载全量店铺 id
     * 只装载"可对用户展示"的店铺，未过审/休息中的店铺不进索引
     */
    public void rebuildIndexes(List<Shop> allShops) {
        // 清理旧 GEO 索引
        Set<String> geoKeys = redis.keys(RedisConstants.SHOP_GEO_KEY + "*");
        if (geoKeys != null && !geoKeys.isEmpty()) {
            redis.delete(geoKeys);
        }
        for (Shop shop : allShops) {
            if (!RoleConstants.visibleToUser(shop)) {
                continue;
            }
            bloom.put(shop.getId());
            if (shop.getLon() != null && shop.getLat() != null) {
                addToGeo(shop);
            }
        }
        // 预热店铺类型缓存
        redis.delete(RedisConstants.SHOP_TYPE_KEY);
        listTypes();
    }

    /** 全平台店铺（管理端列表，不做可见性过滤） */
    public List<Shop> listAll() {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .orderByDesc(Shop::getPopularity));
    }

    /** 可对用户展示的店铺（启动索引重建用） */
    public List<Shop> listVisible() {
        return shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getAuditStatus, RoleConstants.SHOP_AUDIT_APPROVED)
                .eq(Shop::getOpenStatus, RoleConstants.SHOP_OPEN)
                .orderByDesc(Shop::getPopularity));
    }

    /** 按 id 查询（不隐藏任何状态，供管理端/商家端使用） */
    public Shop getById(Long id) {
        Shop shop = shopMapper.selectById(id);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        return shop;
    }
}
