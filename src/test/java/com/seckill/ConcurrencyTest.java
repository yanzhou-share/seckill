package com.seckill;

import com.seckill.entity.SeckillActivity;
import com.seckill.service.SeckillService;
import com.seckill.service.SeckillQueueConsumer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConcurrencyTest {

    @Autowired
    private SeckillService seckillService;

    @Autowired
    private SeckillQueueConsumer queueConsumer;

    private static Long activityId;

    @Test
    @Order(1)
    @DisplayName("并发秒杀测试 - 100人抢10个库存")
    void concurrentSeckill() throws InterruptedException {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(99L);
        activity.setSeckillPrice(new BigDecimal("1.00"));
        activity.setStock(10);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        SeckillActivity created = seckillService.create(activity);
        activityId = created.getId();
        seckillService.updateStatus(activityId, 1);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final Long userId = 100000L + i;
            executor.submit(() -> {
                try {
                    boolean result = seckillService.seckill(userId, activityId);
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

        Thread.sleep(2000);

        System.out.println("=== 并发秒杀测试结果 ===");
        System.out.println("总请求数: " + threadCount);
        System.out.println("成功数: " + successCount.get());
        System.out.println("耗时: " + (System.currentTimeMillis() - startTime) + "ms");

        assertTrue(successCount.get() <= 10, "成功秒杀数不能超过10");
        assertTrue(successCount.get() > 0, "至少有1人秒杀成功");
    }

    @Test
    @Order(2)
    @DisplayName("库存扣减原子性测试")
    void stockDecrementAtomicity() throws InterruptedException {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(98L);
        activity.setSeckillPrice(new BigDecimal("1.00"));
        activity.setStock(1);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        SeckillActivity created = seckillService.create(activity);
        seckillService.updateStatus(created.getId(), 1);

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final Long userId = 200000L + i;
            executor.submit(() -> {
                try {
                    boolean result = seckillService.seckill(userId, created.getId());
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

        Thread.sleep(2000);

        assertEquals(1, successCount.get(), "只能有1人秒杀成功");
    }

}
