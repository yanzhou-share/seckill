package com.seckill;

import org.junit.jupiter.api.*;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RLock;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RedissonTest {

    @Autowired
    private RedissonClient redissonClient;

    @Test
    @Order(1)
    @DisplayName("Redisson基本操作测试")
    void basicOperations() {
        RAtomicLong atomicLong = redissonClient.getAtomicLong("test:atomic");
        atomicLong.set(100);
        assertEquals(100, atomicLong.get());
        atomicLong.decrementAndGet();
        assertEquals(99, atomicLong.get());
        atomicLong.delete();
    }

    @Test
    @Order(2)
    @DisplayName("Redisson Set操作测试")
    void setOperations() {
        RSet<Long> set = redissonClient.getSet("test:set");

        assertTrue(set.add(1001L));
        assertTrue(set.add(1002L));
        assertFalse(set.add(1001L));

        assertTrue(set.contains(1001L));
        assertFalse(set.contains(9999L));
        assertEquals(2, set.size());

        set.delete();
    }

    @Test
    @Order(3)
    @DisplayName("Redisson分布式锁测试")
    void distributedLock() throws InterruptedException {
        RLock lock = redissonClient.getLock("test:lock:" + System.nanoTime());

        boolean locked = lock.tryLock(1, 5, TimeUnit.SECONDS);
        assertTrue(locked);
        assertTrue(lock.isHeldByCurrentThread());

        lock.unlock();
        assertFalse(lock.isHeldByCurrentThread());
    }

    @Test
    @Order(4)
    @DisplayName("Redisson并发锁测试")
    void concurrentLockTest() throws InterruptedException {
        String lockKey = "test:concurrent:lock:" + System.nanoTime();
        int threadCount = 50;
        AtomicInteger successCount = new AtomicInteger(0);
        RAtomicLong counter = redissonClient.getAtomicLong("test:counter:" + System.nanoTime());
        counter.set(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    RLock lock = redissonClient.getLock(lockKey);
                    if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                        try {
                            counter.incrementAndGet();
                            successCount.incrementAndGet();
                        } finally {
                            lock.unlock();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(threadCount, successCount.get());
        assertEquals(threadCount, counter.get());

        counter.delete();
    }

}
