package com.seckill;

import com.seckill.entity.SeckillActivity;
import com.seckill.service.SeckillService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConcurrencyTest {

    @Autowired
    private SeckillService seckillService;

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
        AtomicInteger failCount = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final Long userId = 100000L + i;
            executor.submit(() -> {
                try {
                    boolean result = seckillService.seckill(userId, activityId);
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
        long endTime = System.currentTimeMillis();

        executor.shutdown();

        SeckillActivity finalActivity = seckillService.getById(activityId);

        System.out.println("=== 并发秒杀测试结果 ===");
        System.out.println("总请求数: " + threadCount);
        System.out.println("成功数: " + successCount.get());
        System.out.println("失败数: " + failCount.get());
        System.out.println("剩余库存: " + finalActivity.getStock());
        System.out.println("耗时: " + (endTime - startTime) + "ms");

        assertEquals(10, successCount.get(), "成功秒杀数应为10");
        assertEquals(0, finalActivity.getStock(), "库存应为0");
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

        SeckillActivity finalActivity = seckillService.getById(created.getId());

        assertEquals(1, successCount.get(), "只能有1人秒杀成功");
        assertEquals(0, finalActivity.getStock(), "库存应为0");
    }
}
