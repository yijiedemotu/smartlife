package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.UserContext;
import com.smartlife.service.MateService;
import com.smartlife.vo.MateVO;
import com.smartlife.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 饭搭子匹配（社交匹配，参考伙伴匹配系统）
 */
@RestController
@RequestMapping("/mate")
public class MateController {

    private final MateService mateService;

    public MateController(MateService mateService) {
        this.mateService = mateService;
    }

    /** 推荐饭搭子（编辑距离 + TopN） */
    @GetMapping("/recommend")
    public Result<List<MateVO>> recommend(@RequestParam(required = false) Integer size) {
        return Result.ok(mateService.recommend(UserContext.getUserId(), size));
    }

    /** 按标签找人 */
    @GetMapping("/search")
    public Result<List<UserVO>> search(@RequestParam String tag,
                                       @RequestParam(required = false) Integer size) {
        return Result.ok(mateService.searchByTag(tag, UserContext.getUserId(), size));
    }

    /** 热门标签 */
    @GetMapping("/tags/hot")
    public Result<List<Map<String, Object>>> hotTags() {
        return Result.ok(mateService.hotTags());
    }
}
