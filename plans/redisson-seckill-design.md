# Redis集群防超卖方案 - Redisson实现

## 问题分析

### 当前实现的问题
```java
// Lua脚本操作两个KEY，可能在Redis集群的不同节点
String stockKey = "seckill:stock:" + activityId;
String boughtKey = "seckill:bought:" + activityId;
// 两个KEY的hash不同，可能分布在不同节点
// Redis集群的Lua脚本要求所有KEY必须在同一个hash slot
```

### Redis集群限制
1. Lua脚本只能操作同一个hash slot的key
2. 跨节点操作需要使用 `{hash_tag}` 强制路由到同一节点
3. 但hash_tag会影响集群的数据分布均衡性

## 方案设计

### 方案一：Redisson分布式锁（推荐）

**架构**
```
用户请求
    ↓
获取Redisson分布式锁 (RLock)
    ↓
检查活动状态
    ↓
检查是否已购买 (RSet)
    ↓
扣减库存 (RAtomicLong)
    ↓
释放锁
    ↓
入队创建订单
```

**锁粒度**
- 按活动ID加锁：`seckill:lock:{activityId}`
- 不同活动互不影响
- 同一活动串行执行

**超时配置**
- lockWaitTime: 3秒（等待获取锁）
- lockLeaseTime: 5秒（锁持有时间）
- 自动续期：防止业务未完成锁已释放

**代码实现**
```java
@Slf4j
@Service
public class SeckillService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private SeckillActivityMapper seckillActivityMapper;

    public boolean seckill(Long userId, Long activityId) {
        // 1. 活动校验
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new RuntimeException("活动不存在");
        }
        if (activity.getStatus() != 1) {
            throw new RuntimeException("活动未开始或已结束");
        }

        // 2. 获取分布式锁
        RLock lock = redissonClient.getLock("seckill:lock:" + activityId);
        try {
            if (!lock.tryLock(3, 5, TimeUnit.SECONDS)) {
                throw new RuntimeException("系统繁忙，请稍后再试");
            }

            // 3. 检查是否已购买（Redisson RSet）
            String boughtKey = Constants.SECKILL_BOUGHT_KEY + activityId;
            RSet<Long> boughtSet = redissonClient.getSet(boughtKey);
            if (boughtSet.contains(userId)) {
                throw new RuntimeException("您已参与过该活动");
            }

            // 4. 扣减库存（Redisson RAtomicLong）
            String stockKey = Constants.SECKILL_STOCK_KEY + activityId;
            RAtomicLong stock = redissonClient.getAtomicLong(stockKey);
            long currentStock = stock.decrementAndGet();
            if (currentStock < 0) {
                stock.incrementAndGet(); // 回滚
                throw new RuntimeException("库存不足");
            }

            // 5. 标记已购买
            boughtSet.add(userId);

            // 6. 入队创建订单
            SeckillMessage message = new SeckillMessage();
            message.setUserId(userId);
            message.setActivityId(activityId);
            message.setProductId(activity.getProductId());
            message.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
            redisTemplate.opsForList().rightPush(Constants.SECKILL_QUEUE_KEY, message);

            log.info("秒杀成功: userId={}, activityId={}", userId, activityId);
            return true;

        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

### 方案二：Hash Tag（备选）

使用 `{hash_tag}` 强制所有key在同一节点：
```java
// 使用 {activityId} 作为hash tag
String stockKey = "seckill:{%d}:stock".formatted(activityId);
String boughtKey = "seckill:{%d}:bought".formatted(activityId);
// 两个key会路由到同一节点
```

**缺点：**
- 大量请求集中在同一个slot，热点问题
- 不如分布式锁方案灵活

## 需要修改的文件

| 文件 | 修改内容 |
|------|---------|
| pom.xml | 添加Redisson依赖 |
| application.yml | 添加Redisson配置 |
| 新增 RedissonConfig.java | Redisson配置类 |
| SeckillService.java | 使用Redisson分布式锁 |
| docker-compose.yml | Redis改为单节点或集群 |

## Redisson配置

```yaml
# application.yml
spring:
  redis:
    host: ${REDIS_HOST:localhost}
    port: ${REDIS_PORT:6379}

# 单机模式（开发环境）
redisson:
  single-server-config:
    address: "redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}"
    database: 0

# 集群模式（生产环境）
# redisson:
#   cluster-servers-config:
#     node-addresses:
#       - "redis://node1:6379"
#       - "redis://node2:6379"
#       - "redis://node3:6379"
```

## 性能对比

| 方案 | QPS | 复杂度 | 适用场景 |
|------|-----|--------|---------|
| Lua脚本（单机） | 高 | 低 | 单机Redis |
| Redisson锁 | 中 | 中 | Redis集群 |
| Hash Tag | 高 | 低 | 热点不严重 |

## 测试方案

1. **单机测试**：验证基本功能
2. **并发测试**：100线程秒杀10库存
3. **集群测试**：3节点Redis集群
4. **故障测试**：节点故障时的表现

## 部署建议

**开发环境**：单机Redis
**测试环境**：Redis Sentinel
**生产环境**：Redis Cluster + Redisson
