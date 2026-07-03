package com.seckill.service;

import com.seckill.common.Constants;
import com.seckill.common.SeckillMessage;
import com.seckill.entity.SeckillActivity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SeckillQueueConsumer {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private SeckillService seckillService;

    private static final int MAX_RETRY = 3;
    private static final String RETRY_KEY_PREFIX = "seckill:retry:";
    private static final String DEAD_LETTER_KEY = "seckill:dead_letter";

    private static final String LUA_RPOPLPUSH =
        "local message = redis.call('rpop', KEYS[1]); " +
        "if message then " +
        "  redis.call('lpush', KEYS[2], message); " +
        "  return message; " +
        "end; " +
        "return nil;";

    @Scheduled(fixedDelay = 100)
    public void consume() {
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(LUA_RPOPLPUSH, String.class);
            String message = redisTemplate.execute(script,
                Arrays.asList(Constants.SECKILL_QUEUE_KEY, RETRY_KEY_PREFIX + "processing"));

            if (message == null) {
                return;
            }

            Object obj = redisTemplate.getValueSerializer().deserialize(message.getBytes());
            SeckillMessage msg = null;
            if (obj instanceof SeckillMessage) {
                msg = (SeckillMessage) obj;
            } else if (obj instanceof String) {
                msg = new SeckillMessage();
            }

            if (msg == null) {
                log.error("消息反序列化失败");
                redisTemplate.opsForList().leftPop(RETRY_KEY_PREFIX + "processing");
                return;
            }

            processMessage(msg, 0);

        } catch (Exception e) {
            log.error("消费秒杀消息异常", e);
        }
    }

    private void processMessage(SeckillMessage msg, int retryCount) {
        try {
            SeckillActivity activity = seckillService.getById(msg.getActivityId());
            if (activity == null) {
                log.error("活动不存在: {}", msg.getActivityId());
                moveToDeadLetter(msg, "活动不存在");
                return;
            }

            seckillService.createOrder(msg.getUserId(), msg.getActivityId(), activity);
            redisTemplate.opsForList().leftPop(RETRY_KEY_PREFIX + "processing");
            log.info("订单创建成功: user={}, activity={}", msg.getUserId(), msg.getActivityId());

        } catch (Exception e) {
            log.error("处理消息失败, retryCount={}, error={}", retryCount, e.getMessage());

            redisTemplate.opsForList().leftPop(RETRY_KEY_PREFIX + "processing");

            if (retryCount >= MAX_RETRY) {
                log.error("消息处理失败，移入死信队列: userId={}, activityId={}", msg.getUserId(), msg.getActivityId());
                moveToDeadLetter(msg, e.getMessage());
                return;
            }

            String retryKey = RETRY_KEY_PREFIX + msg.getUserId() + ":" + msg.getActivityId();
            Long retries = redisTemplate.opsForValue().increment(retryKey);
            redisTemplate.expire(retryKey, 1, TimeUnit.HOURS);

            if (retries != null && retries >= MAX_RETRY) {
                log.error("重试次数超限，移入死信队列: userId={}, activityId={}", msg.getUserId(), msg.getActivityId());
                moveToDeadLetter(msg, "重试次数超限");
                redisTemplate.delete(retryKey);
                return;
            }

            redisTemplate.opsForList().rightPush(Constants.SECKILL_QUEUE_KEY, msg);
            log.info("消息重新入队: userId={}, activityId={}, retryCount={}", msg.getUserId(), msg.getActivityId(), retryCount + 1);
        }
    }

    private void moveToDeadLetter(SeckillMessage msg, String reason) {
        try {
            redisTemplate.opsForList().rightPush(DEAD_LETTER_KEY, msg);
            log.warn("消息移入死信队列: userId={}, activityId={}, reason={}", msg.getUserId(), msg.getActivityId(), reason);
        } catch (Exception e) {
            log.error("移入死信队列失败", e);
        }
    }

    public Long getDeadLetterSize() {
        return redisTemplate.opsForList().size(DEAD_LETTER_KEY);
    }

    public SeckillMessage peekDeadLetter() {
        Object message = redisTemplate.opsForList().index(DEAD_LETTER_KEY, 0);
        if (message == null) {
            return null;
        }
        return (SeckillMessage) message;
    }
}
