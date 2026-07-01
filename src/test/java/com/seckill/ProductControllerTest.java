package com.seckill;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.entity.Product;
import com.seckill.common.LoginRequest;
import com.seckill.common.RegisterRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;
    private static Long productId;

    @BeforeAll
    static void setup(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("product_test_user");
        req.setPassword("123456");
        mockMvc.perform(post("/api/user/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)));

        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("product_test_user");
        loginReq.setPassword("123456");

        MvcResult result = mockMvc.perform(post("/api/user/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        java.util.Map<String, Object> map = objectMapper.readValue(response, java.util.Map.class);
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) map.get("data");
        userToken = (String) data.get("token");
    }

    @Test
    @Order(1)
    @DisplayName("创建商品成功")
    void createProductSuccess() throws Exception {
        Product product = new Product();
        product.setName("测试商品1");
        product.setDescription("商品描述");
        product.setPrice(new BigDecimal("99.99"));
        product.setStock(100);

        MvcResult result = mockMvc.perform(post("/api/product")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        java.util.Map<String, Object> map = objectMapper.readValue(response, java.util.Map.class);
        java.util.Map<String, Object> data = (java.util.Map<String, Object>) map.get("data");
        productId = ((Number) data.get("id")).longValue();
    }

    @Test
    @Order(2)
    @DisplayName("创建商品-名称为空")
    void createProduct_nameEmpty() throws Exception {
        Product product = new Product();
        product.setName("");
        product.setPrice(new BigDecimal("99.99"));
        product.setStock(100);

        mockMvc.perform(post("/api/product")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(3)
    @DisplayName("创建商品-价格为空")
    void createProduct_priceNull() throws Exception {
        Product product = new Product();
        product.setName("测试商品");
        product.setStock(100);

        mockMvc.perform(post("/api/product")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(4)
    @DisplayName("创建商品-库存为负")
    void createProduct_stockNegative() throws Exception {
        Product product = new Product();
        product.setName("测试商品");
        product.setPrice(new BigDecimal("99.99"));
        product.setStock(-1);

        mockMvc.perform(post("/api/product")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(5)
    @DisplayName("获取商品列表")
    void listProducts() throws Exception {
        mockMvc.perform(get("/api/product/list")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @Order(6)
    @DisplayName("获取商品详情")
    void getProductById() throws Exception {
        mockMvc.perform(get("/api/product/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("测试商品1"));
    }

    @Test
    @Order(7)
    @DisplayName("更新商品")
    void updateProduct() throws Exception {
        Product product = new Product();
        product.setId(productId);
        product.setName("更新后的商品");
        product.setPrice(new BigDecimal("199.99"));
        product.setStock(200);

        mockMvc.perform(put("/api/product")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(product)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(8)
    @DisplayName("删除商品")
    void deleteProduct() throws Exception {
        mockMvc.perform(delete("/api/product/" + productId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(9)
    @DisplayName("未登录访问商品接口")
    void unauthorizedAccess() throws Exception {
        mockMvc.perform(get("/api/product/list"))
                .andExpect(status().isUnauthorized());
    }
}
