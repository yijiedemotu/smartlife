package com.smartlife.vo;

import com.smartlife.entity.OrderItem;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图（含明细）
 */
@Data
public class OrderVO {

    private Long id;
    private String number;
    private Long userId;
    /** 管理端回显 */
    private String userName;
    private Long shopId;
    private String shopName;
    private String address;
    /** 金额(分) */
    private Integer amount;
    private Integer status;
    private String remark;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private List<OrderItem> items;
}
