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

    @BeforeEach
    void setup() {
        redissonClient.getAtomicLong("test:atomic").delete();
        redissonClient.getSet("test:set").delete();
        redissonClient.getAtomicLong("test:counter").delete();
    }

    @Test
    @Order(1)
    @DisplayName("Redisson基本操作测试")
    void basicOperations() {
        RAtomicLong atomicLong = redissonClient.getAtomicLong("test:atomic");
        atomicLong.set(100);
        assertEquals(100, atomicLong.get());
        atomicLong.decrementAndGet();
        assertEquals(99, atomicLong.get());
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
    }

    @Test
    @Order(3)
    @DisplayName("Redisson分布式锁测试")
    void distributedLock() throws InterruptedException {
        RLock lock = redissonClient.getLock("test:lock");

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
        String lockKey = "test:concurrent:lock";
        int threadCount = 100;
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        RAtomicLong counter = redissonClient.getAtomicLong("test:counter");
        counter.set(0);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                RLock lock = redissonClient.getLock(lockKey);
                try {
                    if (lock.tryLock(5, 10, TimeUnit.SECONDS)) {
                        try {
                            long value = counter.incrementAndGet();
                            if (value <= 10) {
                                successCount.incrementAndGet();
                            } else {
                                failCount.incrementAndGet();
                            }
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        System.out.println("成功数: " + successCount.get());
        System.out.println("失败数: " + failCount.get());
        System.out.println("计数器值: " + counter.get());

        assertEquals(10, successCount.get());
        assertEquals(90, failCount.get());
        assertEquals(10, counter.get());
    }

    @Test
    @Order(5)
    @DisplayName("Redisson锁等待超时测试")
    void lockTimeoutTest() throws InterruptedException {
        String lockKey = "test:timeout:lock";
        RLock lock1 = redissonClient.getLock(lockKey);

        lock1.lock(10, TimeUnit.SECONDS);

        RLock lock2 = redissonClient.getLock(lockKey);
        boolean locked = lock2.tryLock(1, 1, TimeUnit.SECONDS);
        assertFalse(locked);

        lock1.unlock();
    }
}
