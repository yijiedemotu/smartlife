package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.UserContext;
import com.smartlife.dto.OrderCreateDTO;
import com.smartlife.service.OrderService;
import com.smartlife.vo.OrderVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.Map;

/**
 * 用户端订单
 */
@Validated
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/create")
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.ok(orderService.create(UserContext.getUserId(), dto));
    }

    @PostMapping("/{orderId}/pay")
    public Result<OrderVO> pay(@PathVariable Long orderId) {
        return Result.ok(orderService.pay(UserContext.getUserId(), orderId));
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable Long orderId) {
        orderService.cancelByUser(UserContext.getUserId(), orderId);
        return Result.ok();
    }

    @GetMapping("/mine")
    public Result<Map<String, Object>> mine(@RequestParam(required = false) Integer status,
                                            @RequestParam(defaultValue = "1") Integer page,
                                            @RequestParam(defaultValue = "10") Integer size) {
        return Result.ok(orderService.myOrders(UserContext.getUserId(), status, page, size));
    }
}
