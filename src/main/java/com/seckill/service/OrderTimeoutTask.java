package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.entity.Order;
import com.seckill.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class OrderTimeoutTask {

    @Autowired
    private OrderMapper orderMapper;

    @Scheduled(fixedRate = 60000)
    public void cancelExpiredOrders() {
        LocalDateTime timeout = LocalDateTime.now().minusMinutes(30);

        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getStatus, 0);
        wrapper.lt(Order::getCreateTime, timeout);

        List<Order> expiredOrders = orderMapper.selectList(wrapper);

        for (Order order : expiredOrders) {
            order.setStatus(2);
            orderMapper.updateById(order);
            log.info("订单超时取消: orderNo={}", order.getOrderNo());
        }

        if (!expiredOrders.isEmpty()) {
            log.info("取消超时订单数: {}", expiredOrders.size());
        }
    }
}
