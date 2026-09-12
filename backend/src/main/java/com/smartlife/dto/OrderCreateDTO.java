package com.smartlife.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

@Data
public class OrderCreateDTO {

    @NotNull(message = "店铺不能为空")
    private Long shopId;

    private String address;

    private String remark;

    @NotEmpty(message = "订单不能为空")
    @Valid
    private List<CartItemDTO> items;
}
