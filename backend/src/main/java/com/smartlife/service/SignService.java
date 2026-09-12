package com.smartlife.service;

import com.smartlife.common.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 每日签到：Redis BitMap 存储 sign:{userId}:{yyyyMM}
 * 一个用户一年仅占 46 字节，极省内存；支持月度签到统计与连续天数计算
 */
@Service
public class SignService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final StringRedisTemplate redis;

    public SignService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** 今日签到（幂等） */
    public Map<String, Object> sign(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        String key = RedisConstants.signKey(userId, today);
        int offset = today.getDayOfMonth() - 1;
        boolean already = isSigned(userId, today);
        if (!already) {
            redis.opsForValue().setBit(key, offset, true);
        }
        Map<String, Object> map = new HashMap<>();
        map.put("already", already);
        map.put("signedToday", true);
        map.put("date", today.toString());
        return map;
    }

    public boolean isSigned(Long userId, LocalDate date) {
        String key = RedisConstants.signKey(userId, date);
        Boolean bit = redis.opsForValue().getBit(key, date.getDayOfMonth() - 1);
        return Boolean.TRUE.equals(bit);
    }

    /** 当月签到统计 */
    public Map<String, Object> monthStatus(Long userId, YearMonth month) {
        String key = RedisConstants.signKey(userId, month.atDay(1));
        int days = month.lengthOfMonth();
        List<Integer> signedDays = new ArrayList<>();
        int count = 0;
        for (int d = 1; d <= days; d++) {
            Boolean bit = redis.opsForValue().getBit(key, d - 1);
            if (Boolean.TRUE.equals(bit)) {
                signedDays.add(d);
                count++;
            }
        }
        Map<String, Object> map = new HashMap<>();
        map.put("yearMonth", month.format(DateTimeFormatter.ofPattern("yyyyMM")));
        map.put("monthDays", days);
        map.put("signCount", count);
        map.put("signedDays", signedDays);
        return map;
    }

    /** 当前状态（首页展示）：是否签到/本月次数/连续天数 */
    public Map<String, Object> todayStatus(Long userId) {
        LocalDate today = LocalDate.now(ZONE);
        YearMonth month = YearMonth.from(today);
        Map<String, Object> monthStatus = monthStatus(userId, month);
        Map<String, Object> map = new HashMap<>();
        map.put("signedToday", isSigned(userId, today));
        map.put("monthSignCount", monthStatus.get("signCount"));
        map.put("streak", streak(userId, today));
        map.put("today", today.getDayOfMonth());
        return map;
    }

    /** 连续签到天数（从今天往前数） */
    private int streak(Long userId, LocalDate date) {
        int streak = 0;
        LocalDate cursor = date;
        while (isSigned(userId, cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
            // 最多回溯一个月
            if (streak >= date.lengthOfMonth()) {
                break;
            }
        }
        return streak;
    }
}
