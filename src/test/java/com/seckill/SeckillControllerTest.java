package com.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.entity.SeckillActivity;
import com.seckill.common.LoginRequest;
import com.seckill.common.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SeckillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static String userToken;
    private static Long activityId;

    @BeforeAll
    static void setup(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper,
                      @Autowired RedisTemplate<String, Object> redisTemplate) {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();

        RegisterRequest req = new RegisterRequest();
        req.setUsername("seckill_test_user");
        req.setPassword("123456");
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("seckill_test_user");
        loginReq.setPassword("123456");

        try {
            MvcResult result = mockMvc.perform(post("/api/user/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginReq)))
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            java.util.Map<String, Object> map = objectMapper.readValue(response, java.util.Map.class);
            java.util.Map<String, Object> data = (java.util.Map<String, Object>) map.get("data");
            userToken = (String) data.get("token");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @Order(1)
    @DisplayName("创建秒杀活动成功")
    void createActivitySuccess() throws Exception {
        SeckillActivity activity = new SeckillActivity();
        activity.setProductId(1L);
        activity.setSeckillPrice(new BigDecimal("9.99"));
        activity.setStock(10);
        activity.setStartTime(java.time.LocalDateTime.now().minusMinutes(1));
        activity.setEndTime(java.time.LocalDateTime.now().plusHours(1));

        MvcResult result = mockMvc.perform(post("/api/seckill")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        java.util.Map<String, Object> map = objectMapper.readValue(response, java.util.Map.class);
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) map.get("data");
        activityId = ((Number) data.get("id")).longValue();
    }

    @Test
    @Order(2)
    @DisplayName("创建活动-商品ID为空")
    void createActivity_productIdNull() throws Exception {
        SeckillActivity activity = new SeckillActivity();
        activity.setSeckillPrice(new BigDecimal("9.99"));
        activity.setStock(10);
        activity.setStartTime(java.time.LocalDateTime.now());
        activity.setEndTime(java.time.LocalDateTime.now().plusHours(1));

        mockMvc.perform(post("/api/seckill")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(activity)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("获取所有活动列表")
    void listAllActivities() throws Exception {
        mockMvc.perform(get("/api/seckill/listAll")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(4)
    @DisplayName("获取活动详情")
    void getActivityById() throws Exception {
        mockMvc.perform(get("/api/seckill/" + activityId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(activityId));
    }

    @Test
    @Order(5)
    @DisplayName("开启秒杀活动")
    void openActivity() throws Exception {
        mockMvc.perform(put("/api/seckill/" + activityId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .param("status", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(6)
    @DisplayName("执行秒杀成功")
    void doSeckillSuccess() throws Exception {
        mockMvc.perform(post("/api/seckill/do/" + activityId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(7)
    @DisplayName("重复秒杀")
    void duplicateSeckill() throws Exception {
        mockMvc.perform(post("/api/seckill/do/" + activityId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("您已参与过该活动"));
    }

    @Test
    @Order(8)
    @DisplayName("检查已购买状态")
    void checkBoughtTrue() throws Exception {
        mockMvc.perform(get("/api/seckill/check/" + activityId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    @Order(9)
    @DisplayName("关闭秒杀活动")
    void closeActivity() throws Exception {
        mockMvc.perform(put("/api/seckill/" + activityId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .param("status", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(10)
    @DisplayName("活动已结束后秒杀")
    void doSeckillAfterEnd() throws Exception {
        mockMvc.perform(post("/api/seckill/do/" + activityId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("活动未开始或已结束"));
    }

    @Test
    @Order(11)
    @DisplayName("未登录访问秒杀接口")
    void unauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/seckill/list"))
                .andExpect(status().isUnauthorized());
    }
}
