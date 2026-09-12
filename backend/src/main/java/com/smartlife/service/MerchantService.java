package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.common.LoginUser;
import com.smartlife.common.RoleConstants;
import com.smartlife.dto.AuditDTO;
import com.smartlife.dto.MerchantApplyDTO;
import com.smartlife.entity.AuditLog;
import com.smartlife.entity.MerchantApply;
import com.smartlife.entity.Shop;
import com.smartlife.mapper.AuditLogMapper;
import com.smartlife.mapper.MerchantApplyMapper;
import com.smartlife.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家入驻与店铺归属服务 —— 三端隔离的关键
 *
 * 职责：
 *   1) 商家 -> 店铺 归属解析（带进程内缓存，避免每次请求查库）
 *   2) 入驻申请单的提交与平台审核（审核通过才创建/开通店铺）
 *   3) 店铺营业开关与资料维护
 *   4) 关键动作写审计日志
 */
@Slf4j
@Service
public class MerchantService {

    /** 归属缓存 60s，足够吸收高频请求，又能感知审核结果变化 */
    private static final long CACHE_MS = 60_000L;

    private final ShopMapper shopMapper;
    private final MerchantApplyMapper applyMapper;
    private final AuditLogMapper auditLogMapper;
    /** 店铺写操作统一走 ShopService，保证多级缓存/GEO/热点列表一致 */
    private final ShopService shopService;

    private final Map<Long, CacheEntry> shopCache = new HashMap<>();

    public MerchantService(ShopMapper shopMapper, MerchantApplyMapper applyMapper,
                           AuditLogMapper auditLogMapper, ShopService shopService) {
        this.shopMapper = shopMapper;
        this.applyMapper = applyMapper;
        this.auditLogMapper = auditLogMapper;
        this.shopService = shopService;
    }

    // ==================== 归属解析 ====================

    /**
     * 取商家名下的店铺（不存在返回 null）。
     * 注意：非商家角色（如平台管理员）没有个人店铺，调用方需自行判断。
     */
    public Shop shopOfMerchant(Long merchantId) {
        if (merchantId == null) {
            return null;
        }
        CacheEntry entry = shopCache.get(merchantId);
        long now = System.currentTimeMillis();
        if (entry != null && now - entry.at < CACHE_MS) {
            return entry.shop;
        }
        Shop shop = shopMapper.selectOne(new LambdaQueryWrapper<Shop>()
                .eq(Shop::getMerchantId, merchantId).last("LIMIT 1"));
        shopCache.put(merchantId, new CacheEntry(shop, now));
        return shop;
    }

    /** 当前登录商家必须已有店铺，否则提示先入驻 */
    public Shop requireShop(Long merchantId) {
        Shop shop = shopOfMerchant(merchantId);
        if (shop == null) {
            throw new BusinessException("您还没有店铺，请先提交入驻申请");
        }
        return shop;
    }

    /** 当前登录商家必须已通过审核且未停业 */
    public Shop requireActiveShop(Long merchantId) {
        Shop shop = requireShop(merchantId);
        if (shop.getAuditStatus() == null || shop.getAuditStatus() != RoleConstants.SHOP_AUDIT_APPROVED) {
            throw new BusinessException("店铺当前状态为「" + RoleConstants.shopAuditText(shop.getAuditStatus())
                    + "」，审核通过后才能进行经营操作");
        }
        return shop;
    }

    /**
     * 归属校验：目标店铺必须属于该商家（平台管理员传 merchantId=null 时跳过校验，具备代管能力）
     */
    public void assertShopOwnership(Long shopId, Long merchantId) {
        if (shopId == null) {
            throw new BusinessException("缺少店铺参数");
        }
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        if (merchantId != null && !merchantId.equals(shop.getMerchantId())) {
            throw new BusinessException("无权操作他人店铺的数据");
        }
    }

    /** 商家变更后清理归属缓存（审核通过/驳回/资料变更都要调） */
    public void evictCache(Long merchantId) {
        if (merchantId != null) {
            shopCache.remove(merchantId);
        }
    }

    /**
     * 解析"当前要操作的店铺"：
     *   商家  -> 自己的店铺（可通过 mustActive=true 要求已过审）
     *   管理员 -> 必须显式传 shopId（平台代管场景），未传则报错引导选择店铺
     * 返回值一定非空，否则抛业务异常。
     */
    public Shop resolveOperateShop(LoginUser user, Long shopId, boolean mustActive) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.isAdmin()) {
            if (shopId == null) {
                throw new BusinessException("请先选择要管理的店铺");
            }
            Shop shop = shopMapper.selectById(shopId);
            if (shop == null) {
                throw new BusinessException("店铺不存在");
            }
            return shop;
        }
        return mustActive ? requireActiveShop(user.getId()) : requireShop(user.getId());
    }

    /**
     * 校验"当前登录者是否有权操作该店铺"，返回该店铺用于后续查询：
     *   商家 -> 只能是自己的店铺
     *   管理员 -> 任意店铺（平台代管）
     */
    public Shop resolveQueryShop(LoginUser user, Long shopId) {
        if (user == null) {
            throw new BusinessException("未登录");
        }
        if (user.isAdmin()) {
            if (shopId == null) {
                // 管理员不传 shopId：全平台口径，由调用方用 null 处理
                return null;
            }
            return shopMapper.selectById(shopId);
        }
        Shop shop = requireShop(user.getId());
        if (shopId != null && !shopId.equals(shop.getId())) {
            throw new BusinessException("无权查看其它店铺的数据");
        }
        return shop;
    }

    // ==================== 入驻申请 ====================

    /** 商家提交入驻或资料变更申请；已有待审核单时禁止重复提交 */
    @Transactional(rollbackFor = Exception.class)
    public MerchantApply submitApply(Long merchantId, String nickname, String phone, MerchantApplyDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getShopName())) {
            throw new BusinessException("请填写店铺名称");
        }
        Long pending = applyMapper.selectCount(new LambdaQueryWrapper<MerchantApply>()
                .eq(MerchantApply::getMerchantId, merchantId)
                .eq(MerchantApply::getStatus, RoleConstants.APPLY_PENDING));
        if (pending != null && pending > 0) {
            throw new BusinessException("您有一张待审核的入驻申请，请等待平台处理");
        }
        Shop shop = shopOfMerchant(merchantId);
        MerchantApply apply = new MerchantApply();
        apply.setMerchantId(merchantId);
        apply.setShopId(shop == null ? null : shop.getId());
        apply.setType(shop == null ? RoleConstants.APPLY_TYPE_FIRST : RoleConstants.APPLY_TYPE_UPDATE);
        apply.setShopName(dto.getShopName());
        apply.setTypeId(dto.getTypeId());
        apply.setContactName(StringUtils.hasText(dto.getContactName()) ? dto.getContactName() : nickname);
        apply.setContactPhone(StringUtils.hasText(dto.getContactPhone()) ? dto.getContactPhone() : phone);
        apply.setArea(dto.getArea());
        apply.setAddress(dto.getAddress());
        apply.setLon(dto.getLon());
        apply.setLat(dto.getLat());
        apply.setLicenseNo(dto.getLicenseNo());
        apply.setLicenseImg(dto.getLicenseImg());
        apply.setIdCardImg(dto.getIdCardImg());
        apply.setDescription(dto.getDescription());
        apply.setStatus(RoleConstants.APPLY_PENDING);
        applyMapper.insert(apply);
        log.info("商家 {} 提交入驻申请单 {}", merchantId, apply.getId());
        return apply;
    }

    /** 商家查看自己的申请记录（倒序） */
    public List<MerchantApply> myApplies(Long merchantId, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 50);
        return applyMapper.selectList(new LambdaQueryWrapper<MerchantApply>()
                .eq(MerchantApply::getMerchantId, merchantId)
                .orderByDesc(MerchantApply::getCreateTime)
                .last("LIMIT " + (p - 1) * s + "," + s));
    }

    public long countMyApplies(Long merchantId) {
        Long count = applyMapper.selectCount(new LambdaQueryWrapper<MerchantApply>()
                .eq(MerchantApply::getMerchantId, merchantId));
        return count == null ? 0 : count;
    }

    /** 该商家是否存在待审核申请（商家端首屏提示"等待平台审核"） */
    public long countPendingApplies(Long merchantId) {
        Long count = applyMapper.selectCount(new LambdaQueryWrapper<MerchantApply>()
                .eq(MerchantApply::getMerchantId, merchantId)
                .eq(MerchantApply::getStatus, RoleConstants.APPLY_PENDING));
        return count == null ? 0 : count;
    }

    /** 申请单详情（平台审核时查看资质） */
    public MerchantApply getApply(Long applyId) {
        MerchantApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请单不存在");
        }
        return apply;
    }

    /** 平台端：入驻申请分页（status 为空查全部） */
    public Map<String, Object> adminApplyPage(Integer status, Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 50);
        LambdaQueryWrapper<MerchantApply> wrapper = new LambdaQueryWrapper<MerchantApply>()
                .eq(status != null, MerchantApply::getStatus, status)
                .orderByAsc(MerchantApply::getStatus)
                .orderByDesc(MerchantApply::getCreateTime);
        long total = count(applyMapper.selectCount(wrapper));
        List<MerchantApply> records = applyMapper.selectList(
                wrapper.last("LIMIT " + (p - 1) * s + "," + s));
        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("records", records);
        return map;
    }

    /**
     * 平台审核入驻申请：
     *   通过 -> 首次入驻则创建并开通店铺（audit_status=1），资料变更则同步更新店铺
     *   驳回 -> 记录驳回原因，店铺保持原状（首次入驻不创建店铺）
     */
    @Transactional(rollbackFor = Exception.class)
    public void auditApply(Long adminId, Long applyId, AuditDTO dto) {
        MerchantApply apply = applyMapper.selectById(applyId);
        if (apply == null) {
            throw new BusinessException("申请单不存在");
        }
        if (apply.getStatus() != RoleConstants.APPLY_PENDING) {
            throw new BusinessException("该申请单已处理，请勿重复审核");
        }
        if (dto == null || dto.getApproved() == null) {
            throw new BusinessException("请选择审核结果");
        }
        boolean approved = Boolean.TRUE.equals(dto.getApproved());
        String remark = StringUtils.hasText(dto.getRemark())
                ? dto.getRemark()
                : (approved ? "资质齐全，审核通过" : "资料不完整，请补充后重新提交");

        apply.setStatus(approved ? RoleConstants.APPLY_APPROVED : RoleConstants.APPLY_REJECTED);
        apply.setAuditRemark(remark);
        apply.setAuditorId(adminId);
        apply.setAuditTime(LocalDateTime.now());
        applyMapper.updateById(apply);

        Shop shop = apply.getShopId() == null ? null : shopMapper.selectById(apply.getShopId());
        if (approved) {
            if (shop == null) {
                // 首次入驻：审核通过才真正产生店铺
                shop = new Shop();
                shop.setName(apply.getShopName());
                shop.setMerchantId(apply.getMerchantId());
                shop.setTypeId(apply.getTypeId());
                shop.setArea(apply.getArea());
                shop.setAddress(apply.getAddress());
                shop.setPhone(apply.getContactPhone());
                shop.setDescription(apply.getDescription());
                shop.setLon(apply.getLon());
                shop.setLat(apply.getLat());
                shop.setScore(5.0);
                shop.setPopularity(0);
                shop.setOpenStatus(RoleConstants.SHOP_OPEN);
                shop.setAuditStatus(RoleConstants.SHOP_AUDIT_APPROVED);
                shop.setAuditRemark("入驻审核通过：" + remark);
                shop.setAuditTime(LocalDateTime.now());
                shopService.saveShop(shop);
                apply.setShopId(shop.getId());
                applyMapper.updateById(apply);
            } else {
                // 资料变更：同步到店铺并复营业
                shop.setName(apply.getShopName());
                shop.setTypeId(apply.getTypeId());
                shop.setArea(apply.getArea());
                shop.setAddress(apply.getAddress());
                shop.setPhone(apply.getContactPhone());
                shop.setDescription(apply.getDescription());
                if (apply.getLon() != null) {
                    shop.setLon(apply.getLon());
                }
                if (apply.getLat() != null) {
                    shop.setLat(apply.getLat());
                }
                shop.setAuditStatus(RoleConstants.SHOP_AUDIT_APPROVED);
                shop.setAuditRemark("变更审核通过：" + remark);
                shop.setAuditTime(LocalDateTime.now());
                shopService.saveShop(shop);
            }
        } else if (shop != null && apply.getType() == RoleConstants.APPLY_TYPE_FIRST) {
            // 首次入驻被驳回但已存在店铺记录（历史数据）时标记驳回
            shop.setAuditStatus(RoleConstants.SHOP_AUDIT_REJECTED);
            shop.setAuditRemark(remark);
            shop.setAuditTime(LocalDateTime.now());
            shopService.saveShop(shop);
        }
        evictCache(apply.getMerchantId());
        writeAuditLog(adminId, RoleConstants.ROLE_ADMIN,
                approved ? "APPLY_APPROVE" : "APPLY_REJECT",
                "APPLY", applyId, apply.getShopName() + "「" + remark + "」");
    }

    // ==================== 店铺经营状态 ====================

    /** 商家切换营业/休息（仅审核通过的店铺可操作） */
    public void toggleOpen(Long merchantId, boolean open) {
        Shop shop = requireActiveShop(merchantId);
        shop.setOpenStatus(open ? RoleConstants.SHOP_OPEN : RoleConstants.SHOP_REST);
        shopService.saveShop(shop);
        evictCache(merchantId);
    }

    /** 商家维护可自助修改的店铺资料（名称/类目/经纬度/营业状态等敏感项走审核流程） */
    public void updateShopProfile(Long merchantId, Shop form) {
        Shop shop = requireShop(merchantId);
        if (form == null) {
            throw new BusinessException("缺少店铺参数");
        }
        if (StringUtils.hasText(form.getAddress())) {
            shop.setAddress(form.getAddress());
        }
        if (form.getLon() != null) {
            shop.setLon(form.getLon());
        }
        if (form.getLat() != null) {
            shop.setLat(form.getLat());
        }
        if (form.getImages() != null) {
            shop.setImages(form.getImages());
        }
        if (form.getPhone() != null) {
            shop.setPhone(form.getPhone());
        }
        shopService.saveShop(shop);
        evictCache(merchantId);
    }

    // ==================== 平台治理 ====================

    /** 平台停业/恢复某店铺（违规治理） */
    public void changeShopAuditStatus(Long adminId, Long shopId, Integer auditStatus, String remark) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            throw new BusinessException("店铺不存在");
        }
        if (auditStatus == null || (auditStatus != RoleConstants.SHOP_AUDIT_APPROVED
                && auditStatus != RoleConstants.SHOP_AUDIT_CLOSED)) {
            throw new BusinessException("不支持的店铺状态变更");
        }
        shop.setAuditStatus(auditStatus);
        shop.setAuditRemark(remark);
        shop.setAuditTime(LocalDateTime.now());
        shopService.saveShop(shop);
        evictCache(shop.getMerchantId());
        writeAuditLog(adminId, RoleConstants.ROLE_ADMIN,
                auditStatus == RoleConstants.SHOP_AUDIT_CLOSED ? "SHOP_CLOSE" : "SHOP_REOPEN",
                "SHOP", shopId, shop.getName() + "「" + (remark == null ? "" : remark) + "」");
    }

    private long count(Long value) {
        return value == null ? 0 : value;
    }

    // ==================== 审计日志 ====================

    public void writeAuditLog(Long actorId, Integer actorRole, String action,
                              String targetType, Long targetId, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setActorId(actorId);
            log.setActorRole(actorRole);
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            log.setDetail(detail);
            auditLogMapper.insert(log);
        } catch (Exception e) {
            // 审计失败不影响主流程
            log.warn("写审计日志失败: {}", e.getMessage());
        }
    }

    /** 平台端：审计日志分页 */
    public Map<String, Object> auditLogPage(Integer page, Integer size) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 100);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<AuditLog>()
                .orderByDesc(AuditLog::getCreateTime);
        long total = count(auditLogMapper.selectCount(wrapper));
        List<AuditLog> records = auditLogMapper.selectList(wrapper.last("LIMIT " + (p - 1) * s + "," + s));
        Map<String, Object> map = new HashMap<>();
        map.put("total", total);
        map.put("records", records);
        return map;
    }

    private static final class CacheEntry {
        private final Shop shop;
        private final long at;

        private CacheEntry(Shop shop, long at) {
            this.shop = shop;
            this.at = at;
        }
    }
}
