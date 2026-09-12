package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.common.JsonUtils;
import com.smartlife.common.MQConstants;
import com.smartlife.common.RedisConstants;
import com.smartlife.dto.SeckillMessage;
import com.smartlife.entity.Shop;
import com.smartlife.entity.Voucher;
import com.smartlife.mapper.ShopMapper;
import com.smartlife.mapper.VoucherMapper;
import com.smartlife.vo.VoucherVO;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 优惠券/秒杀服务
 *
 * 秒杀高并发链路（参考黑马点评+黑马商城）：
 * Redis Lua 原子校验(库存+一人一单) -> RabbitMQ 异步削峰下单 -> 消费者 DB 兜底事务
 * Lua 关闭时降级为 Redisson 分布式锁方案（可配置演示两种实现）
 */
@Service
public class VoucherService {

    private static final String SECKILL_LOCK_PREFIX = "smartlife:seckill:lock:";
    private static final String NORMAL_LOCK_PREFIX = "smartlife:voucher:grab:";

    private final VoucherMapper voucherMapper;
    private final VoucherOrderService voucherOrderService;
    private final ShopMapper shopMapper;
    private final StringRedisTemplate redis;
    private final RabbitTemplate rabbitTemplate;
    private final RedissonClient redisson;

    /** Redis Lua 秒杀脚本（库存扣减+一人一单原子执行） */
    private final DefaultRedisScript<Long> seckillScript = new DefaultRedisScript<>();

    @Value("${smartlife.seckill.lua-enabled:true}")
    private boolean luaEnabled;

    public VoucherService(VoucherMapper voucherMapper, VoucherOrderService voucherOrderService,
                          ShopMapper shopMapper, StringRedisTemplate redis,
                          RabbitTemplate rabbitTemplate, RedissonClient redisson) {
        this.voucherMapper = voucherMapper;
        this.voucherOrderService = voucherOrderService;
        this.shopMapper = shopMapper;
        this.redis = redis;
        this.rabbitTemplate = rabbitTemplate;
        this.redisson = redisson;
        seckillScript.setLocation(new ClassPathResource("lua/seckill.lua"));
        seckillScript.setResultType(Long.class);
    }

    // ==================== 查询 ====================

    /** 店铺可领/可抢券（普通券 + 进行中秒杀券），30s 缓存 */
    public List<VoucherVO> shopVouchers(Long shopId) {
        String cacheKey = RedisConstants.SHOP_VOUCHER_KEY + shopId;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            List<VoucherVO> list = JsonUtils.toList(cached, VoucherVO.class);
            if (list != null) {
                return list;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getShopId, shopId)
                .eq(Voucher::getStatus, 1)
                .eq(Voucher::getAuditStatus, 1)
                .and(w -> w.eq(Voucher::getType, Voucher.TYPE_NORMAL)
                        .or(o -> o.eq(Voucher::getType, Voucher.TYPE_SECOND_KILL)
                                .le(Voucher::getBeginTime, now)
                                .ge(Voucher::getEndTime, now)))
                .orderByAsc(Voucher::getType).orderByDesc(Voucher::getId));
        List<VoucherVO> result = toVOs(vouchers);
        redis.opsForValue().set(cacheKey, JsonUtils.toJson(result), 30, TimeUnit.SECONDS);
        return result;
    }

    /** 秒杀场次列表（全平台进行中），5s 缓存 */
    public List<VoucherVO> seckillList() {
        String cached = redis.opsForValue().get(RedisConstants.SECKILL_LIST_KEY);
        if (cached != null) {
            List<VoucherVO> list = JsonUtils.toList(cached, VoucherVO.class);
            if (list != null) {
                return list;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        List<Voucher> vouchers = voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getType, Voucher.TYPE_SECOND_KILL)
                .eq(Voucher::getStatus, 1)
                .eq(Voucher::getAuditStatus, 1)
                .le(Voucher::getBeginTime, now)
                .ge(Voucher::getEndTime, now)
                .orderByAsc(Voucher::getEndTime));
        List<VoucherVO> result = toVOs(vouchers);
        redis.opsForValue().set(RedisConstants.SECKILL_LIST_KEY, JsonUtils.toJson(result),
                5, TimeUnit.SECONDS);
        return result;
    }

    private List<VoucherVO> toVOs(List<Voucher> vouchers) {
        List<Long> shopIds = vouchers.stream().map(Voucher::getShopId).distinct().collect(Collectors.toList());
        List<Shop> shops = shopIds.isEmpty() ? new ArrayList<>()
                : shopMapper.selectBatchIds(shopIds);
        return vouchers.stream().map(v -> {
            VoucherVO vo = new VoucherVO();
            vo.setId(v.getId());
            vo.setShopId(v.getShopId());
            vo.setTitle(v.getTitle());
            vo.setSubTitle(v.getSubTitle());
            vo.setRules(v.getRules());
            vo.setPayValue(v.getPayValue());
            vo.setActualValue(v.getActualValue());
            vo.setType(v.getType());
            vo.setBeginTime(v.getBeginTime());
            vo.setEndTime(v.getEndTime());
            // 剩余库存：秒杀券读 Redis 实时值（已售罄置 0）
            if (v.getType() == Voucher.TYPE_SECOND_KILL) {
                String stock = redis.opsForValue().get(RedisConstants.seckillStockKey(v.getId()));
                vo.setStockLeft(stock == null ? 0 : Integer.parseInt(stock));
            } else {
                vo.setStockLeft(v.getStock());
            }
            Shop shop = shops.stream().filter(s -> s.getId().equals(v.getShopId())).findFirst().orElse(null);
            if (shop != null) {
                vo.setShopName(shop.getName());
                vo.setShopImage(shop.getImages());
                vo.setShopArea(shop.getArea());
            }
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 秒杀 ====================

    /**
     * 秒杀主流程：
     * Lua 开启：Lua 原子预扣库存并标记一人一单 -> 发送 MQ 异步下单
     * Lua 关闭：Redisson 分布式锁（看门狗自动续期）互斥 -> 校验 -> 扣减 -> 发送 MQ
     */
    public void grabSeckill(Long userId, Long voucherId) {
        Voucher voucher = voucherMapper.selectById(voucherId);
        if (voucher == null || voucher.getStatus() != 1 || voucher.getType() != Voucher.TYPE_SECOND_KILL) {
            throw new BusinessException("秒杀券不存在或已下架");
        }
        if (voucher.getAuditStatus() != null && voucher.getAuditStatus() != 1) {
            throw new BusinessException("该券已被平台下架");
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getBeginTime() != null && now.isBefore(voucher.getBeginTime())) {
            throw new BusinessException("秒杀尚未开始");
        }
        if (voucher.getEndTime() != null && now.isAfter(voucher.getEndTime())) {
            throw new BusinessException("秒杀已结束");
        }
        ensureStockCache(voucher);

        long code;
        if (luaEnabled) {
            code = luaReserve(userId, voucherId);
        } else {
            code = lockReserve(userId, voucherId);
        }
        if (code == 1) {
            throw new BusinessException("手慢了，已被抢光");
        }
        if (code == 2) {
            throw new BusinessException("每人限购一单，请勿重复抢购");
        }
        // 预扣成功 -> MQ 异步下单，削峰填谷
        rabbitTemplate.convertAndSend(MQConstants.SECKILL_EXCHANGE, MQConstants.SECKILL_ORDER_ROUTING,
                JsonUtils.toJson(new SeckillMessage(userId, voucherId)));
    }

    /** Lua 原子扣减：0成功 1库存不足 2重复下单 */
    private long luaReserve(Long userId, Long voucherId) {
        Long result = redis.execute(seckillScript,
                List.of(RedisConstants.seckillStockKey(voucherId),
                        RedisConstants.seckillUserKey(voucherId)),
                String.valueOf(userId));
        return result == null ? 1 : result;
    }

    /** Redisson 分布式锁兜底方案（演示集群互斥 + 看门狗续期） */
    private long lockReserve(Long userId, Long voucherId) {
        RLock lock = redisson.getLock(SECKILL_LOCK_PREFIX + voucherId);
        lock.lock();
        try {
            // 已购校验（DB 唯一索引对应）
            if (voucherOrderService.countByUserAndVoucher(userId, voucherId) > 0) {
                return 2;
            }
            String stockKey = RedisConstants.seckillStockKey(voucherId);
            String stock = redis.opsForValue().get(stockKey);
            int left = stock == null ? 0 : Integer.parseInt(stock);
            if (left <= 0) {
                return 1;
            }
            redis.opsForValue().decrement(stockKey);
            redis.opsForSet().add(RedisConstants.seckillUserKey(voucherId), String.valueOf(userId));
            return 0;
        } finally {
            lock.unlock();
        }
    }

    /** 普通代金券领取：Redisson 分布式锁互斥，DB 库存兜底 */
    public void grabNormal(Long userId, Long voucherId) {
        RLock lock = redisson.getLock(NORMAL_LOCK_PREFIX + voucherId);
        lock.lock();
        try {
            voucherOrderService.createVoucherOrderTx(userId, voucherId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 回补 Redis 库存并移除用户标记：异步下单失败时的补偿（MQ 死信兜底）
     */
    public void restoreStock(Long voucherId, Long userId) {
        String stockKey = RedisConstants.seckillStockKey(voucherId);
        if (Boolean.FALSE.equals(redis.hasKey(stockKey))) {
            Voucher voucher = voucherMapper.selectById(voucherId);
            if (voucher != null && voucher.getStock() != null) {
                redis.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
            } else {
                return;
            }
        }
        redis.opsForValue().increment(stockKey);
        redis.opsForSet().remove(RedisConstants.seckillUserKey(voucherId), String.valueOf(userId));
    }

    /** 库存缓存兜底：缓存被清/重启丢失时从 DB 回填 */
    private void ensureStockCache(Voucher voucher) {
        String stockKey = RedisConstants.seckillStockKey(voucher.getId());
        if (Boolean.FALSE.equals(redis.hasKey(stockKey)) && voucher.getStock() != null && voucher.getStock() > 0) {
            redis.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
        }
    }

    // ==================== 管理端 ====================

    /**
     * 创建/更新优惠券：同步 Redis 秒杀库存快照，并清理相关缓存
     * 商家端调用前必须由 MerchantService.assertShopOwnership 校验归属
     */
    @Transactional(rollbackFor = Exception.class)
    public void adminSave(Voucher voucher) {
        if (voucher.getType() == null) {
            voucher.setType(Voucher.TYPE_NORMAL);
        }
        if (voucher.getStatus() == null) {
            voucher.setStatus(1);
        }
        if (voucher.getAuditStatus() == null) {
            voucher.setAuditStatus(1);
        }
        if (voucher.getSold() == null) {
            voucher.setSold(0);
        }
        if (voucher.getStock() == null) {
            voucher.setStock(0);
        }
        if (voucher.getId() == null) {
            voucherMapper.insert(voucher);
        } else {
            voucherMapper.updateById(voucher);
        }
        if (voucher.getType() == Voucher.TYPE_SECOND_KILL) {
            String stockKey = RedisConstants.seckillStockKey(voucher.getId());
            redis.delete(stockKey);
            if (voucher.getStock() != null && voucher.getStock() > 0) {
                redis.opsForValue().set(stockKey, String.valueOf(voucher.getStock()));
            }
        }
        evictCache(voucher.getShopId());
    }

    /**
     * 平台强制下架/恢复优惠券（商家自主上下架走 status，平台治理走 audit_status）
     */
    public void changeAuditStatus(Long id, Integer auditStatus) {
        Voucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new BusinessException("优惠券不存在");
        }
        voucher.setAuditStatus(auditStatus == null ? 1 : auditStatus);
        voucherMapper.updateById(voucher);
        evictCache(voucher.getShopId());
    }

    public void adminRemove(Long id) {
        Voucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            return;
        }
        voucherMapper.deleteById(id);
        if (voucher.getType() == Voucher.TYPE_SECOND_KILL) {
            redis.delete(RedisConstants.seckillStockKey(id));
            redis.delete(RedisConstants.seckillUserKey(id));
        }
        evictCache(voucher.getShopId());
    }

    // ==================== 商家端 / 管理端 ====================

    /** 优惠券分页：shopId != null 为商家端（仅本店），null 为平台端（全平台） */
    public List<Voucher> adminPage(Long shopId, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        return voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(shopId != null, Voucher::getShopId, shopId)
                .orderByDesc(Voucher::getCreateTime)
                .last("LIMIT " + (p - 1) * s + "," + s));
    }

    public long countVouchers(Long shopId) {
        Long count = voucherMapper.selectCount(new LambdaQueryWrapper<Voucher>()
                .eq(shopId != null, Voucher::getShopId, shopId));
        return count == null ? 0 : count;
    }

    /** 按 id 查询券（不存在抛业务异常，供归属校验使用） */
    public Voucher getById(Long id) {
        Voucher voucher = voucherMapper.selectById(id);
        if (voucher == null) {
            throw new BusinessException("优惠券不存在");
        }
        return voucher;
    }

    /** 秒杀券库存缓存巡检：缓存缺失时从 DB 回填（启动/定时任务调用） */
    public void syncSeckillStocks() {
        List<Voucher> seckillVouchers = voucherMapper.selectList(new LambdaQueryWrapper<Voucher>()
                .eq(Voucher::getType, Voucher.TYPE_SECOND_KILL)
                .eq(Voucher::getStatus, 1));
        for (Voucher v : seckillVouchers) {
            ensureStockCache(v);
        }
    }

    private void evictCache(Long shopId) {
        if (shopId != null) {
            redis.delete(RedisConstants.SHOP_VOUCHER_KEY + shopId);
        }
        redis.delete(RedisConstants.SECKILL_LIST_KEY);
    }
}
