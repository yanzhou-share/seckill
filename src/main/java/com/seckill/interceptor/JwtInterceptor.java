package com.seckill.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seckill.common.Constants;
import com.seckill.common.Result;
import com.seckill.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader(Constants.TOKEN_HEADER);
        if (!StringUtils.hasText(token) || !token.startsWith(Constants.TOKEN_PREFIX)) {
            writeError(response, 401, "未登录");
            return false;
        }

        token = token.substring(Constants.TOKEN_PREFIX.length());

        try {
            if (jwtUtil.isTokenExpired(token)) {
                writeError(response, 401, "登录已过期");
                return false;
            }
            Long userId = jwtUtil.getUserIdFromToken(token);
            request.setAttribute(Constants.USER_ID, userId);
            return true;
        } catch (Exception e) {
            writeError(response, 401, "Token无效");
            return false;
        }
    }

    private void writeError(HttpServletResponse response, int code, String msg) throws Exception {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(code);
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(Result.error(code, msg)));
    }
}
