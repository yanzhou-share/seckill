package com.seckill.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.Constants;
import com.seckill.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collections;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${seckill.rate-limit.window:10}")
    private int window;

    @Value("${seckill.rate-limit.max-count:5}")
    private int maxCount;

    private static final String LUA_SCRIPT =
        "local count = redis.call('incr', KEYS[1]); " +
        "if count == 1 then " +
        "  redis.call('expire', KEYS[1], ARGV[1]); " +
        "end; " +
        "return count;";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Long userId = (Long) request.getAttribute(Constants.USER_ID);
        if (userId == null) {
            return true;
        }

        String key = Constants.SECKILL_RATE_KEY + userId;

        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
            Long count = redisTemplate.execute(script, Collections.singletonList(key), String.valueOf(window));

            if (count != null && count > maxCount) {
                writeError(response, 429, "请求过于频繁，请稍后再试");
                return false;
            }
        } catch (Exception e) {
            log.warn("限流Lua脚本执行异常，跳过限流: key={}, error={}", key, e.getMessage());
        }

        return true;
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(Result.error(code, msg)));
    }
}
