package com.seckill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.seckill.common.Constants;
import com.seckill.entity.SeckillActivity;
import com.seckill.mapper.OrderMapper;
import com.seckill.service.SeckillService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConcurrencyTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        redisTemplate.delete(Constants.SECKILL_QUEUE_KEY);
    }

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("并发秒杀测试 - 消息队列异步消费")
    void concurrentSeckillWithQueue() throws InterruptedException {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(999L);
        activity.setSeckillPrice(new BigDecimal("1.00"));
        activity.setStock(10);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        SeckillActivity created = seckillService.create(activity);
        Long testActivityId = created.getId();
        seckillService.updateStatus(testActivityId, 1);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final Long userId = 300000L + i;
            executor.submit(() -> {
                try {
                    boolean result = seckillService.seckill(userId, testActivityId);
                    if (result) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long enqueueEndTime = System.currentTimeMillis();

        waitForQueueEmpty(5000);

        long consumeEndTime = System.currentTimeMillis();

        SeckillActivity finalActivity = seckillService.getById(testActivityId);
        long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<com.seckill.entity.Order>()
                .eq(com.seckill.entity.Order::getActivityId, testActivityId));
        Long remainingQueueSize = redisTemplate.opsForList().size(Constants.SECKILL_QUEUE_KEY);

        System.out.println("=== 并发秒杀测试结果（消息队列）===");
        System.out.println("总请求数: " + threadCount);
        System.out.println("Redis预扣减成功数: " + successCount.get());
        System.out.println("失败数: " + failCount.get());
        System.out.println("DB订单数: " + orderCount);
        System.out.println("剩余库存: " + finalActivity.getStock());
        System.out.println("队列剩余消息数: " + (remainingQueueSize != null ? remainingQueueSize : 0));
        System.out.println("入队耗时: " + (enqueueEndTime - startTime) + "ms");
        System.out.println("消费耗时: " + (consumeEndTime - enqueueEndTime) + "ms");
        System.out.println("总耗时: " + (consumeEndTime - startTime) + "ms");

        assertEquals(10, successCount.get(), "Redis预扣减成功数应为10");
        assertEquals(90, failCount.get(), "失败数应为90");
        assertEquals(10, orderCount, "DB订单数应为10");
        assertEquals(0, finalActivity.getStock(), "库存应为0");
        assertEquals(0, remainingQueueSize, "队列应为空");
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    @DisplayName("库存扣减原子性测试")
    void stockDecrementAtomicity() throws InterruptedException {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(998L);
        activity.setSeckillPrice(new BigDecimal("1.00"));
        activity.setStock(1);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        SeckillActivity created = seckillService.create(activity);
        Long testActivityId = created.getId();
        seckillService.updateStatus(testActivityId, 1);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final Long userId = 400000L + i;
            executor.submit(() -> {
                try {
                    boolean result = seckillService.seckill(userId, testActivityId);
                    if (result) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // expected
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        waitForQueueEmpty(3000);

        SeckillActivity finalActivity = seckillService.getById(testActivityId);
        long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<com.seckill.entity.Order>()
                .eq(com.seckill.entity.Order::getActivityId, testActivityId));

        System.out.println("=== 库存扣减原子性测试结果 ===");
        System.out.println("总请求数: " + threadCount);
        System.out.println("Redis预扣减成功数: " + successCount.get());
        System.out.println("DB订单数: " + orderCount);
        System.out.println("剩余库存: " + finalActivity.getStock());

        assertEquals(1, successCount.get(), "Redis预扣减只能有1人成功");
        assertEquals(1, orderCount, "DB订单数应为1");
        assertEquals(0, finalActivity.getStock(), "库存应为0");
    }

    @Test
    @org.junit.jupiter.api.Order(3)
    @DisplayName("高并发秒杀测试 - 500人抢20个库存")
    void highConcurrencySeckill() throws InterruptedException {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(888L);
        activity.setSeckillPrice(new BigDecimal("0.01"));
        activity.setStock(20);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        SeckillActivity created = seckillService.create(activity);
        Long testActivityId = created.getId();
        seckillService.updateStatus(testActivityId, 1);

        int threadCount = 500;
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();
        ArrayList<Long> userIdsSuccess = new ArrayList<>();
        ArrayList<Long> userIdsFaild = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final Long userId = 500000L + i;
            executor.submit(() -> {
                try {
                    boolean result = seckillService.seckill(userId, testActivityId);
                    if (result) {
                        userIdsSuccess.add(userId);
                        successCount.incrementAndGet();
                    } else {
                        userIdsFaild.add(userId);
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    userIdsFaild.add(userId);
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        waitForQueueEmpty(15000);

        long endTime = System.currentTimeMillis();

        SeckillActivity finalActivity = seckillService.getById(testActivityId);
        long orderCount = orderMapper.selectCount(new LambdaQueryWrapper<com.seckill.entity.Order>()
                .eq(com.seckill.entity.Order::getActivityId, testActivityId));

        System.out.println("=== 高并发秒杀测试结果 ===");
        System.out.println("总请求数: " + threadCount);
        System.out.println("Redis预扣减成功数: " + successCount.get());
        System.out.println("失败数: " + failCount.get());
        System.out.println("DB订单数: " + orderCount);
        System.out.println("剩余库存: " + finalActivity.getStock());
        System.out.println("总耗时: " + (endTime - startTime) + "ms");
        System.out.println("成功UIDS: " + userIdsSuccess.toString());
        System.out.println("失败UIDS: " + userIdsFaild.toString());
        System.out.println("TPS: " + (threadCount * 1000 / (endTime - startTime)) + "/s");

        assertEquals(20, successCount.get(), "Redis预扣减成功数应为20");
        assertEquals(480, failCount.get(), "失败数应为480");
        assertEquals(20, orderCount, "DB订单数应为20");
        assertEquals(0, finalActivity.getStock(), "库存应为0");
    }

    private void waitForQueueEmpty(long timeoutMs) throws InterruptedException {
        long startTime = System.currentTimeMillis();
        long interval = 200;
        int consecutiveEmptyCount = 0;
        int requiredEmptyCount = 3;

        while (true) {
            Long size = redisTemplate.opsForList().size(Constants.SECKILL_QUEUE_KEY);
            if (size == null || size == 0) {
                consecutiveEmptyCount++;
                if (consecutiveEmptyCount >= requiredEmptyCount) {
                    break;
                }
            } else {
                consecutiveEmptyCount = 0;
            }
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                System.out.println("等待队列消费超时，剩余消息数: " + size);
                break;
            }
            Thread.sleep(interval);
        }
    }
}