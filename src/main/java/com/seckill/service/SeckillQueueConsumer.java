package com.seckill.service;

import com.seckill.common.Constants;
import com.seckill.common.SeckillMessage;
import com.seckill.entity.SeckillActivity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SeckillQueueConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SeckillService seckillService;

    @Scheduled(fixedDelay = 100)
    public void consume() {
        try {
            Object message = redisTemplate.opsForList().leftPop(Constants.SECKILL_QUEUE_KEY);
            if (message == null) {
                return;
            }

            SeckillMessage msg = (SeckillMessage) message;
            SeckillActivity activity = seckillService.getById(msg.getActivityId());

            if (activity == null) {
                log.error("活动不存在: {}", msg.getActivityId());
                return;
            }

            seckillService.createOrder(msg.getUserId(), msg.getActivityId(), activity);
            log.info("订单创建成功: user={}, activity={}", msg.getUserId(), msg.getActivityId());

        } catch (Exception e) {
            log.error("消费秒杀消息失败", e);
        }
    }
}
