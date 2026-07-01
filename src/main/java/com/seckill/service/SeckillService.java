package com.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.Constants;
import com.seckill.common.SeckillMessage;
import com.seckill.entity.Order;
import com.seckill.entity.Product;
import com.seckill.entity.SeckillActivity;
import com.seckill.mapper.OrderMapper;
import com.seckill.mapper.SeckillActivityMapper;
import com.seckill.mapper.ProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillService {

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public List<SeckillActivity> listActive() {
        LambdaQueryWrapper<SeckillActivity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SeckillActivity::getStatus, 1);
        return seckillActivityMapper.selectList(wrapper);
    }

    public List<SeckillActivity> listAll() {
        return seckillActivityMapper.selectList(null);
    }

    public SeckillActivity getById(Long id) {
        return seckillActivityMapper.selectById(id);
    }

    public SeckillActivity create(SeckillActivity activity) {
        activity.setStatus(0);
        activity.setCreateTime(LocalDateTime.now());
        seckillActivityMapper.insert(activity);
        initRedisStock(activity.getId(), activity.getStock());
        log.info("创建秒杀活动: id={}, productId={}, stock={}", activity.getId(), activity.getProductId(), activity.getStock());
        return activity;
    }

    public void initRedisStock(Long activityId, Integer stock) {
        String key = Constants.SECKILL_STOCK_KEY + activityId;
        redisTemplate.opsForValue().set(key, stock);
    }

    public void updateStatus(Long id, Integer status) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(id);
        activity.setStatus(status);
        seckillActivityMapper.updateById(activity);
        log.info("更新活动状态: id={}, status={}", id, status);
    }

    public boolean seckill(Long userId, Long activityId) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }

        if (activity.getStatus() != 1) {
            throw new RuntimeException("活动未开始或已结束");
        }

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime()) || now.isAfter(activity.getEndTime())) {
            throw new RuntimeException("活动未在有效期内");
        }

        String stockKey = Constants.SECKILL_STOCK_KEY + activityId;
        String boughtKey = Constants.SECKILL_BOUGHT_KEY + activityId;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
            "local stock = redis.call('decr', KEYS[1]); " +
            "if stock < 0 then redis.call('incr', KEYS[1]); return 0; end; " +
            "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then redis.call('incr', KEYS[1]); return 2; end; " +
            "redis.call('sadd', KEYS[2], ARGV[1]); return 1;"
        );
        script.setResultType(Long.class);

        Long result = redisTemplate.execute(script,
            java.util.Arrays.asList(stockKey, boughtKey),
            userId.toString());

        if (result == 0) {
            log.warn("秒杀失败-库存不足: userId={}, activityId={}", userId, activityId);
            throw new RuntimeException("库存不足");
        }
        if (result == 2) {
            log.warn("秒杀失败-重复购买: userId={}, activityId={}", userId, activityId);
            throw new RuntimeException("您已参与过该活动");
        }

        SeckillMessage message = new SeckillMessage();
        message.setUserId(userId);
        message.setActivityId(activityId);
        message.setProductId(activity.getProductId());
        message.setOrderNo(UUID.randomUUID().toString().replace("-", ""));

        redisTemplate.opsForList().rightPush(Constants.SECKILL_QUEUE_KEY, message);
        log.info("秒杀成功-入队: userId={}, activityId={}", userId, activityId);

        return true;
    }

    @Transactional
    public void createOrder(Long userId, Long activityId, SeckillActivity activity) {
        Order order = new Order();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setUserId(userId);
        order.setActivityId(activityId);
        order.setProductId(activity.getProductId());
        order.setPrice(activity.getSeckillPrice());
        order.setStatus(0);
        order.setCreateTime(LocalDateTime.now());
        orderMapper.insert(order);

        int updated = seckillActivityMapper.decrementStock(activityId);
        if (updated == 0) {
            throw new RuntimeException("库存扣减失败");
        }
        log.info("订单创建成功: orderNo={}, userId={}, activityId={}", order.getOrderNo(), userId, activityId);
    }

    public boolean checkBought(Long userId, Long activityId) {
        String boughtKey = Constants.SECKILL_BOUGHT_KEY + activityId;
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(boughtKey, userId));
    }
}
