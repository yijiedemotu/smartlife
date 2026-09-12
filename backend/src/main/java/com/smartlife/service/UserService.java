package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlife.common.BusinessException;
import com.smartlife.common.JsonUtils;
import com.smartlife.common.LoginUser;
import com.smartlife.common.RoleConstants;
import com.smartlife.dto.LoginDTO;
import com.smartlife.dto.MerchantApplyDTO;
import com.smartlife.dto.MerchantRegisterDTO;
import com.smartlife.dto.RegisterDTO;
import com.smartlife.entity.User;
import com.smartlife.mapper.UserMapper;
import com.smartlife.util.JwtUtil;
import com.smartlife.util.PasswordUtil;
import com.smartlife.vo.UserVO;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 账号服务（三端统一）：注册、登录（JWT）、个人资料
 *
 * 注册入口区分角色：
 *   /auth/register           用户端注册 -> role=1
 *   /auth/register/merchant  商家入驻注册 -> role=2 + 自动生成一张待审核入驻申请单
 * 管理员不开放自助注册（避免越权提权），由已有管理员在管理端开通。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final MerchantService merchantService;

    public UserService(UserMapper userMapper, JwtUtil jwtUtil, MerchantService merchantService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.merchantService = merchantService;
    }

    // ==================== 注册 ====================

    /** 用户端注册 */
    public void register(RegisterDTO dto) {
        ensurePhoneAvailable(dto.getPhone());
        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname() : "用户" + dto.getPhone().substring(7));
        user.setGender(0);
        user.setRole(RoleConstants.ROLE_USER);
        user.setStatus(RoleConstants.USER_STATUS_NORMAL);
        userMapper.insert(user);
    }

    /**
     * 商家入驻注册：创建 role=2 账号 + 首张入驻申请单（待平台审核）
     * 账号可立即登录进入商家端，但审核通过前不能进行经营操作。
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerMerchant(MerchantRegisterDTO dto) {
        ensurePhoneAvailable(dto.getPhone());
        User user = new User();
        user.setPhone(dto.getPhone());
        user.setPassword(PasswordUtil.encode(dto.getPassword()));
        user.setNickname(StringUtils.hasText(dto.getNickname())
                ? dto.getNickname() : "商家" + dto.getPhone().substring(7));
        user.setGender(0);
        user.setRole(RoleConstants.ROLE_MERCHANT);
        user.setStatus(RoleConstants.USER_STATUS_NORMAL);
        userMapper.insert(user);

        MerchantApplyDTO apply = new MerchantApplyDTO();
        apply.setShopName(dto.getShopName());
        apply.setTypeId(dto.getTypeId());
        apply.setContactName(dto.getContactName());
        apply.setContactPhone(dto.getContactPhone());
        apply.setArea(dto.getArea());
        apply.setAddress(dto.getAddress());
        apply.setLon(dto.getLon());
        apply.setLat(dto.getLat());
        apply.setLicenseNo(dto.getLicenseNo());
        apply.setLicenseImg(dto.getLicenseImg());
        apply.setIdCardImg(dto.getIdCardImg());
        apply.setDescription(dto.getDescription());
        merchantService.submitApply(user.getId(), user.getNickname(), user.getPhone(), apply);
    }

    private void ensurePhoneAvailable(String phone) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
        if (exists != null && exists > 0) {
            throw new BusinessException("该手机号已注册");
        }
    }

    // ==================== 登录 ====================

    public Map<String, Object> login(LoginDTO dto) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, dto.getPhone()));
        if (user == null || !PasswordUtil.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException("手机号或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == RoleConstants.USER_STATUS_BANNED) {
            throw new BusinessException("账号已被平台封禁，请联系客服");
        }
        String token = jwtUtil.createToken(new LoginUser(user.getId(), user.getRole(), user.getNickname()));
        Map<String, Object> map = new HashMap<>();
        map.put("token", token);
        map.put("id", user.getId());
        map.put("role", user.getRole());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        // 三端首页路径，前端登录后直接跳转，避免前端重复判断角色逻辑
        map.put("homePath", homePath(user.getRole()));
        // 商家端附带店铺状态，便于前端引导"去入驻/等待审核/去经营"
        if (user.getRole() != null && user.getRole() == RoleConstants.ROLE_MERCHANT) {
            var shop = merchantService.shopOfMerchant(user.getId());
            map.put("shopId", shop == null ? null : shop.getId());
            map.put("shopAuditStatus", shop == null ? null : shop.getAuditStatus());
            map.put("shopName", shop == null ? null : shop.getName());
        }
        return map;
    }

    /** 角色 -> 端侧首页 */
    public static String homePath(Integer role) {
        if (role == null) {
            return "/user/home";
        }
        switch (role) {
            case RoleConstants.ROLE_MERCHANT:
                return "/merchant/dashboard";
            case RoleConstants.ROLE_ADMIN:
                return "/admin/dashboard";
            default:
                return "/user/home";
        }
    }

    // ==================== 资料 ====================

    public UserVO myProfile(Long userId) {
        return toVO(userMapper.selectById(userId), true);
    }

    public UserVO publicProfile(Long userId) {
        return toVO(userMapper.selectById(userId), false);
    }

    /**
     * 更新个人资料；tags 传 JSON 数组字符串，wechat 为微信号(联系方式)
     */
    public void updateProfile(Long userId, String nickname, String avatar, Integer gender,
                              String address, String wechat, String tagsJson) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname);
        }
        if (avatar != null) {
            user.setAvatar(avatar);
        }
        if (gender != null) {
            user.setGender(gender);
        }
        if (address != null) {
            user.setAddress(address);
        }
        if (wechat != null) {
            user.setWechat(wechat);
        }
        if (tagsJson != null) {
            // 校验为合法 JSON 数组后落库
            List<String> tags = JsonUtils.fromJson(tagsJson, new TypeReference<List<String>>() {});
            user.setTags(JsonUtils.toJson(tags == null ? new ArrayList<>() : tags));
        }
        userMapper.updateById(user);
    }

    /** 修改密码（用户/商家自助） */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 6) {
            throw new BusinessException("新密码长度至少 6 位");
        }
        user.setPassword(PasswordUtil.encode(newPassword));
        userMapper.updateById(user);
    }

    // ==================== 平台端用户治理 ====================

    /** 平台端用户分页（可按角色/状态/关键字过滤），一律不返回密码 */
    public Map<String, Object> adminPage(Integer page, Integer size, Integer role, Integer status, String keyword) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 10 : Math.min(size, 100);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(role != null, User::getRole, role)
                .eq(status != null, User::getStatus, status)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(User::getPhone, keyword).or().like(User::getNickname, keyword))
                .orderByDesc(User::getCreateTime);
        Long total = userMapper.selectCount(wrapper);
        List<UserVO> records = userMapper.selectList(wrapper.last("LIMIT " + (p - 1) * s + "," + s))
                .stream().map(u -> toVO(u, false)).toList();
        Map<String, Object> map = new HashMap<>();
        map.put("total", total == null ? 0 : total);
        map.put("records", records);
        return map;
    }

    /** 平台封禁/解封账号（管理员账号不可被操作，防止误锁死平台） */
    public void changeStatus(Long userId, Integer status) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (user.getRole() != null && user.getRole() == RoleConstants.ROLE_ADMIN) {
            throw new BusinessException("平台管理员账号不允许被封禁");
        }
        if (status == null || (status != RoleConstants.USER_STATUS_NORMAL
                && status != RoleConstants.USER_STATUS_BANNED)) {
            throw new BusinessException("不支持的账号状态");
        }
        user.setStatus(status);
        userMapper.updateById(user);
    }

    public UserVO toVO(User user, boolean withPhone) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setGender(user.getGender());
        vo.setRole(user.getRole());
        vo.setStatus(user.getStatus());
        vo.setAddress(user.getAddress());
        vo.setWechat(user.getWechat());
        vo.setCreateTime(user.getCreateTime());
        if (withPhone) {
            String phone = user.getPhone();
            vo.setPhone(phone == null ? null : phone.substring(0, 3) + "****" + phone.substring(7));
        }
        if (StringUtils.hasText(user.getTags())) {
            vo.setTags(JsonUtils.fromJson(user.getTags(), new TypeReference<List<String>>() {}));
        }
        return vo;
    }
}
