package com.seckill;

import com.seckill.common.Constants;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class RedisTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("Redis库存扣减测试")
    void stockDecrement() {
        String key = "test:stock:1";
        redisTemplate.opsForValue().set(key, 10);

        Long stock = redisTemplate.opsForValue().decrement(key);
        assertEquals(9, stock);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis库存扣减原子性测试")
    void stockDecrementAtomic() throws InterruptedException {
        String key = "test:stock:atomic";
        int initStock = 100;
        redisTemplate.opsForValue().set(key, initStock);

        int threadCount = 200;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    redisTemplate.opsForValue().decrement(key);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Long stock = (Long) redisTemplate.opsForValue().get(key);
        assertEquals(-100, stock);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis重复购买检查")
    void duplicatePurchaseCheck() {
        String key = "test:bought:1";
        Long userId = 1001L;

        redisTemplate.opsForSet().remove(key, userId);

        Long added = redisTemplate.opsForSet().add(key, userId);
        assertEquals(1, added);

        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId);
        assertTrue(isMember);

        Long addedAgain = redisTemplate.opsForSet().add(key, userId);
        assertEquals(0, addedAgain);

        Long size = redisTemplate.opsForSet().size(key);
        assertEquals(1, size);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis限流测试")
    void rateLimit() {
        String key = "test:rate:1";
        int maxCount = 5;
        long window = 10;

        redisTemplate.delete(key);

        for (int i = 0; i < maxCount; i++) {
            Long count = redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, Duration.ofSeconds(window));
            assertTrue(count <= maxCount);
        }

        Long count = redisTemplate.opsForValue().increment(key);
        assertTrue(count > maxCount);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis并发限流测试")
    void concurrentRateLimit() throws InterruptedException {
        String key = "test:rate:concurrent";
        int maxCount = 10;
        long window = 10;
        int threadCount = 100;

        redisTemplate.delete(key);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failCount = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Long count = redisTemplate.opsForValue().increment(key);
                    if (count <= maxCount) {
                        successCount.incrementAndGet();
                        redisTemplate.expire(key, Duration.ofSeconds(window));
                    } else {
                        failCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue(successCount.get() <= maxCount);
        assertEquals(threadCount - successCount.get(), failCount.get());

        redisTemplate.delete(key);
    }
}
