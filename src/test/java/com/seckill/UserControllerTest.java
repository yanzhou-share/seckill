package com.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.LoginRequest;
import com.seckill.common.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;

    @Test
    @Order(1)
    @DisplayName("注册成功")
    void registerSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser1");
        request.setPassword("123456");
        request.setNickname("测试用户1");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(2)
    @DisplayName("注册-用户名为空")
    void registerUsernameEmpty() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setPassword("123456");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(3)
    @DisplayName("注册-密码过短")
    void registerPasswordShort() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser2");
        request.setPassword("123");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @Order(4)
    @DisplayName("注册-重复用户名")
    void registerDuplicateUsername() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser1");
        request.setPassword("123456");

        mockMvc.perform(post("/api/user/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("用户名已存在"));
    }

    @Test
    @Order(5)
    @DisplayName("登录成功")
    void loginSuccess() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser1");
        request.setPassword("123456");

        MvcResult result = mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        java.util.Map<String, Object> responseMap = objectMapper.readValue(response, java.util.Map.class);
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) responseMap.get("data");
        userToken = (String) data.get("token");
    }

    @Test
    @Order(6)
    @DisplayName("登录-密码错误")
    void loginWrongPassword() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("testuser1");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
    }

    @Test
    @Order(7)
    @DisplayName("登录-用户不存在")
    void login_userNotFound() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("123456");

        mockMvc.perform(post("/api/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("用户名或密码错误"));
    }

    @Test
    @Order(8)
    @DisplayName("获取用户信息")
    void getUserInfo() throws Exception {
        mockMvc.perform(get("/api/user/info")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("testuser1"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @Order(9)
    @DisplayName("获取用户信息-未登录")
    void getUserInfoUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/info"))
                .andExpect(status().isUnauthorized());
    }
}
