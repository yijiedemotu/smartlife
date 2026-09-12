package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.UserContext;
import com.smartlife.service.SignService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.Map;

/**
 * 每日签到（BitMap）
 */
@RestController
@RequestMapping("/sign")
public class SignController {

    private final SignService signService;

    public SignController(SignService signService) {
        this.signService = signService;
    }

    @PostMapping("/today")
    public Result<Map<String, Object>> sign() {
        return Result.ok(signService.sign(UserContext.getUserId()));
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(signService.todayStatus(UserContext.getUserId()));
    }

    @GetMapping("/month/{yearMonth}")
    public Result<Map<String, Object>> month(@PathVariable String yearMonth) {
        YearMonth month = "current".equals(yearMonth) ? YearMonth.now()
                : YearMonth.parse(yearMonth, java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        return Result.ok(signService.monthStatus(UserContext.getUserId(), month));
    }
}
