package com.smartlife.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.smartlife.common.BusinessException;
import com.smartlife.common.JsonUtils;
import com.smartlife.common.RedisConstants;
import com.smartlife.common.LoginUser;
import com.smartlife.entity.User;
import com.smartlife.mapper.UserMapper;
import com.smartlife.util.Levenshtein;
import com.smartlife.vo.MateVO;
import com.smartlife.vo.UserVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 饭搭子匹配服务（参考伙伴匹配系统）
 *
 * 算法：编辑距离度量标签相似度
 * 优化：TopN 采用容量为 N 的小根堆语义（大顶堆淘汰最差），内存占用 O(N) 而非 O(全量)
 * 缓存：推荐结果按用户缓存 10min，定时预热热门用户，缓解热用户重复计算
 */
@Service
public class MateService {

    private static final String HOT_TAGS_KEY = "mate:hotTags";

    private final UserMapper userMapper;
    private final UserService userService;
    private final StringRedisTemplate redis;

    public MateService(UserMapper userMapper, UserService userService, StringRedisTemplate redis) {
        this.userMapper = userMapper;
        this.userService = userService;
        this.redis = redis;
    }

    @Data
    @AllArgsConstructor
    private static class Candidate {
        private Long userId;
        private int distance;
        private int matchedTags;
    }

    /**
     * 推荐饭搭子：编辑距离 + 优先队列 TopN
     */
    public List<MateVO> recommend(Long userId, Integer size) {
        int n = size == null ? 6 : Math.max(1, Math.min(size, 20));
        // 结果缓存（同一用户 10 分钟内重复推荐直接命中 Redis）
        String cacheKey = RedisConstants.mateRecommendKey(userId);
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            List<MateVO> list = JsonUtils.fromJson(cached, new TypeReference<List<MateVO>>() {});
            if (list != null && !list.isEmpty()) {
                return list;
            }
        }
        User me = userMapper.selectById(userId);
        List<String> myTags = parseTags(me);
        if (myTags.isEmpty()) {
            throw new BusinessException("请先在个人中心完善兴趣标签，才能匹配到合适的饭搭子");
        }

        List<User> pool = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, LoginUser.ROLE_USER)
                .ne(User::getId, userId)
                .isNotNull(User::getTags));
        if (pool.isEmpty()) {
            return new ArrayList<>();
        }

        // 大顶堆维护「当前最优 N 个」：堆顶是待淘汰的最差候选
        PriorityQueue<Candidate> heap = new PriorityQueue<>(
                Comparator.comparingInt(Candidate::getDistance).reversed());
        Map<Long, User> userMap = new HashMap<>();
        for (User other : pool) {
            List<String> otherTags = parseTags(other);
            if (otherTags.isEmpty()) {
                continue;
            }
            Candidate c = scoreCandidate(userId, myTags, other, otherTags);
            if (heap.size() < n) {
                heap.offer(c);
            } else if (c.getDistance() < heap.peek().getDistance()) {
                heap.poll();
                heap.offer(c);
            }
            userMap.put(other.getId(), other);
        }
        List<Candidate> top = new ArrayList<>(heap);
        top.sort(Comparator.comparingInt(Candidate::getDistance));

        List<MateVO> result = top.stream().map(c -> {
            MateVO vo = new MateVO();
            UserVO userVO = userService.toVO(userMap.get(c.getUserId()), false);
            vo.setUser(userVO);
            vo.setDistance(c.getDistance());
            vo.setMatchedTags(c.getMatchedTags());
            // 标签数加权换算：距离 0 => 100 分
            int rate = Math.max(0, 100 - c.getDistance() * 10);
            vo.setMatchRate(rate);
            return vo;
        }).collect(Collectors.toList());

        redis.opsForValue().set(cacheKey, JsonUtils.toJson(result),
                RedisConstants.MATE_RECOMMEND_TTL, TimeUnit.SECONDS);
        return result;
    }

    /** 相似度打分：每个“我的标签”取其与对方标签集合的最小编辑距离并累加 */
    private Candidate scoreCandidate(Long myId, List<String> myTags, User other, List<String> otherTags) {
        int total = 0;
        int matched = 0;
        for (String mine : myTags) {
            int best = Integer.MAX_VALUE;
            for (String theirs : otherTags) {
                int d = Levenshtein.distance(mine, theirs);
                if (d < best) {
                    best = d;
                }
            }
            if (best == 0) {
                matched++;
            }
            total += best;
        }
        return new Candidate(other.getId(), total, matched);
    }

    /** 按标签精确搜索饭搭子（JSON_CONTAINS） */
    public List<UserVO> searchByTag(String tag, Long userId, Integer size) {
        if (tag == null || tag.isBlank()) {
            return new ArrayList<>();
        }
        int limit = size == null ? 10 : Math.max(1, Math.min(size, 50));
        return userMapper.selectByTag(tag.trim()).stream()
                .filter(u -> !u.getId().equals(userId))
                .limit(limit)
                .map(u -> userService.toVO(u, false))
                .collect(Collectors.toList());
    }

    /** 热门标签（去重计数，10min 缓存） */
    public List<Map<String, Object>> hotTags() {
        String cached = redis.opsForValue().get(HOT_TAGS_KEY);
        if (cached != null) {
            List<Map<String, Object>> list = JsonUtils.fromJson(cached,
                    new TypeReference<List<Map<String, Object>>>() {});
            if (list != null) {
                return list;
            }
        }
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, LoginUser.ROLE_USER)
                .isNotNull(User::getTags));
        Map<String, Integer> counts = new HashMap<>();
        for (User u : users) {
            for (String tag : parseTags(u)) {
                counts.merge(tag, 1, Integer::sum);
            }
        }
        List<Map<String, Object>> result = counts.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(24)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("tag", e.getKey());
                    m.put("count", e.getValue());
                    return m;
                }).collect(Collectors.toList());
        redis.opsForValue().set(HOT_TAGS_KEY, JsonUtils.toJson(result), 10, TimeUnit.MINUTES);
        return result;
    }

    private List<String> parseTags(User user) {
        if (user == null || user.getTags() == null || user.getTags().isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<String> tags = JsonUtils.fromJson(user.getTags(), new TypeReference<List<String>>() {});
            return tags == null ? new ArrayList<>() : tags;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
