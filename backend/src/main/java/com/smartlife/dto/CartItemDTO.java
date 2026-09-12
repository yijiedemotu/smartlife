package com.smartlife.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class CartItemDTO {

    @NotNull(message = "商品不能为空")
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 99, message = "单次最多 99 件")
    private Integer count;
}
