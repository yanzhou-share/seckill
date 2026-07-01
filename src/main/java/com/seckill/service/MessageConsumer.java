package com.seckill.service;

import com.seckill.common.Constants;
import com.seckill.common.SeckillMessage;
import com.seckill.entity.Order;
import com.seckill.entity.SeckillActivity;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.SeckillActivityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class MessageConsumer {

    private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @PostConstruct
    public void startConsumer() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        for (int i = 0; i < 4; i++) {
            executor.submit(this::processQueue);
        }
        logger.info("消息队列消费者启动成功，线程数: 4");
    }

    public void processQueue() {
        while (true) {
            try {
                Object obj = redisTemplate.opsForList().leftPop(Constants.SECKILL_QUEUE_KEY, 100, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (obj == null) {
                    continue;
                }

                SeckillMessage message = (SeckillMessage) obj;
                try {
                    createOrder(message);
                    logger.info("消息消费成功 - userId: {}, activityId: {}", message.getUserId(), message.getActivityId());
                } catch (Exception e) {
                    logger.error("消息消费失败 - userId: {}, activityId: {}, error: {}", 
                        message.getUserId(), message.getActivityId(), e.getMessage());
                    rollbackRedis(message);
                }
            } catch (Exception e) {
                logger.error("队列处理异常: {}", e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void createOrder(SeckillMessage message) {
        SeckillActivity activity = seckillActivityMapper.selectById(message.getActivityId());
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }

        int updated = seckillActivityMapper.decrementStock(message.getActivityId());
        if (updated == 0) {
            throw new RuntimeException("库存扣减失败");
        }

        Order order = new Order();
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setActivityId(message.getActivityId());
        order.setProductId(message.getProductId());
        order.setPrice(activity.getSeckillPrice());
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);
    }

    private void rollbackRedis(SeckillMessage message) {
        try {
            String stockKey = Constants.SECKILL_STOCK_KEY + message.getActivityId();
            redisTemplate.opsForValue().increment(stockKey);

            String boughtKey = Constants.SECKILL_BOUGHT_KEY + message.getActivityId();
            redisTemplate.opsForSet().remove(boughtKey, message.getUserId());
            logger.info("Redis回滚成功 - userId: {}, activityId: {}", message.getUserId(), message.getActivityId());
        } catch (Exception e) {
            logger.error("Redis回滚失败 - userId: {}, activityId: {}, error: {}", 
                message.getUserId(), message.getActivityId(), e.getMessage());
        }
    }
}