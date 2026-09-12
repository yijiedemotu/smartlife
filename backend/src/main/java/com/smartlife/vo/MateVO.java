package com.smartlife.vo;

import lombok.Data;

/**
 * 饭搭子匹配结果：用户 + 匹配度
 */
@Data
public class MateVO {

    private UserVO user;
    /** 0~100 匹配度 */
    private Integer matchRate;
    /** 标签总距离（越小越像） */
    private Integer distance;
    /** 共同标签数 */
    private Integer matchedTags;
}
