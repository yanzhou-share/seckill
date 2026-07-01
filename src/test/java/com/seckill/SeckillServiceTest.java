package com.seckill;

import com.seckill.entity.SeckillActivity;
import com.seckill.service.SeckillService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeckillServiceTest {

    @Autowired
    private SeckillService seckillService;

    private static Long activityId;
    private static Long testUserId = 10001L;

    @Test
    @Order(1)
    @DisplayName("创建秒杀活动")
    void createActivity() {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(1L);
        activity.setSeckillPrice(new BigDecimal("9.99"));
        activity.setStock(10);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));

        SeckillActivity created = seckillService.create(activity);
        activityId = created.getId();

        assertNotNull(activityId);
        assertEquals(0, created.getStatus());
    }

    @Test
    @Order(2)
    @DisplayName("开启秒杀活动")
    void openActivity() {
        seckillService.updateStatus(activityId, 1);
        SeckillActivity activity = seckillService.getById(activityId);
        assertEquals(1, activity.getStatus());
    }

    @Test
    @Order(3)
    @DisplayName("正常秒杀")
    void seckillSuccess() {
        boolean result = seckillService.seckill(testUserId, activityId);
        assertTrue(result);
    }

    @Test
    @Order(4)
    @DisplayName("重复秒杀")
    void duplicateSeckill() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seckillService.seckill(testUserId, activityId));
        assertEquals("您已参与过该活动", exception.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("库存不足秒杀")
    void outOfStock() {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(2L);
        activity.setSeckillPrice(new BigDecimal("1.00"));
        activity.setStock(1);
        activity.setStartTime(LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        SeckillActivity created = seckillService.create(activity);
        seckillService.updateStatus(created.getId(), 1);

        seckillService.seckill(20001L, created.getId());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seckillService.seckill(20002L, created.getId()));
        assertEquals("库存不足", exception.getMessage());
    }

    @Test
    @Order(6)
    @DisplayName("活动未开始秒杀")
    void activityNotStarted() {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(3L);
        activity.setSeckillPrice(new BigDecimal("1.00"));
        activity.setStock(10);
        activity.setStartTime(LocalDateTime.now().plusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(2));
        SeckillActivity created = seckillService.create(activity);
        seckillService.updateStatus(created.getId(), 1);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seckillService.seckill(30001L, created.getId()));
        assertEquals("活动未在有效期内", exception.getMessage());
    }

    @Test
    @Order(7)
    @DisplayName("活动已结束秒杀")
    void activityEnded() {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(4L);
        activity.setSeckillPrice(new BigDecimal("1.00"));
        activity.setStock(10);
        activity.setStartTime(LocalDateTime.now().minusHours(2));
        activity.setEndTime(LocalDateTime.now().minusHours(1));
        SeckillActivity created = seckillService.create(activity);
        seckillService.updateStatus(created.getId(), 1);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seckillService.seckill(40001L, created.getId()));
        assertEquals("活动未在有效期内", exception.getMessage());
    }

    @Test
    @Order(8)
    @DisplayName("检查是否已购买-已购买")
    void checkBoughtTrue() {
        assertTrue(seckillService.checkBought(testUserId, activityId));
    }

    @Test
    @Order(9)
    @DisplayName("检查是否已购买-未购买")
    void checkBoughtFalse() {
        assertFalse(seckillService.checkBought(99999L, activityId));
    }

    @Test
    @Order(10)
    @DisplayName("关闭秒杀活动")
    void closeActivity() {
        seckillService.updateStatus(activityId, 2);
        SeckillActivity activity = seckillService.getById(activityId);
        assertEquals(2, activity.getStatus());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seckillService.seckill(50001L, activityId));
        assertEquals("活动未开始或已结束", exception.getMessage());
    }

    @Test
    @Order(11)
    @DisplayName("活动不存在")
    void activityNotFound() {
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> seckillService.seckill(60001L, 99999L));
        assertEquals("活动不存在", exception.getMessage());
    }
}
