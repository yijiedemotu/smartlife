package com.smartlife.controller;

import com.smartlife.common.Result;
import com.smartlife.common.UserContext;
import com.smartlife.dto.CartItemDTO;
import com.smartlife.service.CartService;
import com.smartlife.vo.CartItemVO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 购物车（Redis Hash）
 */
@Validated
@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Result<List<CartItemVO>> list() {
        return Result.ok(cartService.list(UserContext.getUserId()));
    }

    @GetMapping("/count")
    public Result<Map<String, Object>> count() {
        Map<String, Object> map = new HashMap<>();
        map.put("count", cartService.totalCount(UserContext.getUserId()));
        return Result.ok(map);
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody CartItemDTO dto) {
        cartService.add(UserContext.getUserId(), dto.getProductId(), dto.getCount());
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody CartItemDTO dto) {
        cartService.update(UserContext.getUserId(), dto.getProductId(), dto.getCount());
        return Result.ok();
    }

    @DeleteMapping("/{productId}")
    public Result<Void> remove(@PathVariable Long productId) {
        cartService.remove(UserContext.getUserId(), productId);
        return Result.ok();
    }

    @DeleteMapping
    public Result<Void> clear() {
        cartService.clear(UserContext.getUserId());
        return Result.ok();
    }
}
