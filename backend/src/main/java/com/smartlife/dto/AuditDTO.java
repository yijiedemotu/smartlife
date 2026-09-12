package com.smartlife.dto;

import lombok.Data;

/**
 * 平台审核入参（入驻审核 / 店铺治理 / 店铺类型等共用一条审批语义）
 */
@Data
public class AuditDTO {

    /** 是否通过：true 通过，false 驳回 */
    private Boolean approved;

    /** 审核意见 / 驳回原因 */
    private String remark;
}
