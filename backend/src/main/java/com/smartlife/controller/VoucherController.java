package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.UserContext;
import com.smartlife.service.VoucherOrderService;
import com.smartlife.service.VoucherService;
import com.smartlife.vo.VoucherOrderVO;
import com.smartlife.vo.VoucherVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户端优惠券：领取/秒杀/我的券/核销
 */
@RestController
@RequestMapping("/voucher")
public class VoucherController {

    private final VoucherService voucherService;
    private final VoucherOrderService voucherOrderService;

    public VoucherController(VoucherService voucherService, VoucherOrderService voucherOrderService) {
        this.voucherService = voucherService;
        this.voucherOrderService = voucherOrderService;
    }

    /** 店铺优惠券（普通券+进行中秒杀券） */
    @GetMapping("/shop/{shopId}")
    public Result<List<VoucherVO>> shopVouchers(@PathVariable Long shopId) {
        return Result.ok(voucherService.shopVouchers(shopId));
    }

    /** 全平台秒杀场次 */
    @GetMapping("/seckill/list")
    public Result<List<VoucherVO>> seckillList() {
        return Result.ok(voucherService.seckillList());
    }

    /** 秒杀：Redis Lua 原子预扣 + MQ 异步下单 */
    @PostMapping("/{voucherId}/seckill")
    public Result<Void> seckill(@PathVariable Long voucherId) {
        voucherService.grabSeckill(UserContext.getUserId(), voucherId);
        return Result.ok();
    }

    /** 普通代金券领取：Redisson 分布式锁 */
    @PostMapping("/{voucherId}/grab")
    public Result<Void> grab(@PathVariable Long voucherId) {
        voucherService.grabNormal(UserContext.getUserId(), voucherId);
        return Result.ok();
    }

    /** 我的券 */
    @GetMapping("/order/my")
    public Result<List<VoucherOrderVO>> my(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "20") Integer size) {
        return Result.ok(voucherOrderService.myPage(UserContext.getUserId(), page, size));
    }

    /** 核销（演示） */
    @PostMapping("/order/{orderId}/use")
    public Result<Void> use(@PathVariable Long orderId) {
        voucherOrderService.useVoucher(orderId, UserContext.getUserId());
        return Result.ok();
    }
}
