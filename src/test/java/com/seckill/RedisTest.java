package com.seckill;

import com.seckill.common.Constants;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
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
    @DisplayName("Redis基本操作测试")
    void basicOperations() {
        String key = "test:basic";
        redisTemplate.opsForValue().set(key, "hello");
        assertEquals("hello", redisTemplate.opsForValue().get(key));
        redisTemplate.delete(key);
    }

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
    @DisplayName("Redis Lua脚本原子扣减测试")
    void luaScriptDecrement() {
        String key = "test:lua:stock";
        redisTemplate.opsForValue().set(key, 5);

        String script = "local stock = redis.call('decr', KEYS[1]); " +
                "if stock < 0 then redis.call('incr', KEYS[1]); return -1; end; " +
                "return stock;";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

        Long result = redisTemplate.execute(redisScript, Collections.singletonList(key));
        assertEquals(4, result);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis重复购买检查测试")
    void duplicatePurchaseCheck() {
        String key = "test:bought:1";
        Long userId = 1001L;

        redisTemplate.opsForSet().remove(key, userId);

        Boolean added = redisTemplate.opsForSet().add(key, userId);
        assertTrue(added);

        Boolean isMember = redisTemplate.opsForSet().isMember(key, userId);
        assertTrue(isMember);

        Boolean addedAgain = redisTemplate.opsForSet().add(key, userId);
        assertFalse(addedAgain);

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
            redisTemplate.expire(key, window);
            assertTrue(count <= maxCount);
        }

        Long count = redisTemplate.opsForValue().increment(key);
        assertTrue(count > maxCount);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis Lua脚本限流测试")
    void luaScriptRateLimit() {
        String key = "test:lua:rate";
        int maxCount = 3;
        int window = 10;

        redisTemplate.delete(key);

        String script = "local count = redis.call('incr', KEYS[1]); " +
                "if count == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; " +
                "return count;";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

        for (int i = 0; i < maxCount; i++) {
            Long count = redisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(window));
            assertTrue(count <= maxCount);
        }

        Long count = redisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(window));
        assertTrue(count > maxCount);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis并发限流测试")
    void concurrentRateLimit() throws InterruptedException {
        String key = "test:rate:concurrent";
        int maxCount = 10;
        int window = 10;
        int threadCount = 100;

        redisTemplate.delete(key);

        String script = "local count = redis.call('incr', KEYS[1]); " +
                "if count == 1 then redis.call('expire', KEYS[1], ARGV[1]); end; " +
                "return count;";

        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>(script, Long.class);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong successCount = new AtomicLong(0);
        AtomicLong failCount = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Long count = redisTemplate.execute(redisScript, Collections.singletonList(key), String.valueOf(window));
                    if (count != null && count <= maxCount) {
                        successCount.incrementAndGet();
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
