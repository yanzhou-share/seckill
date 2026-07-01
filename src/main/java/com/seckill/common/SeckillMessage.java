package com.seckill.common;

import lombok.Data;

import java.io.Serializable;

@Data
public class SeckillMessage implements Serializable {
    private Long userId;
    private Long activityId;
    private Long productId;
    private String orderNo;
}
