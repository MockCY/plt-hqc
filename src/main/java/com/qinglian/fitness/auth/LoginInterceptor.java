package com.qinglian.fitness.auth;

import com.qinglian.fitness.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.OptionalLong;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    public LoginInterceptor(SessionService sessionService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) throws Exception {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized(response);
            return false;
        }
        String rawToken = authorization.substring(7).trim();
        OptionalLong userId = sessionService.verify(rawToken);
        if (userId.isEmpty()) {
            unauthorized(response);
            return false;
        }
        request.setAttribute(CurrentUser.USER_ID_ATTRIBUTE, userId.getAsLong());
        request.setAttribute(CurrentUser.RAW_TOKEN_ATTRIBUTE, rawToken);
        return true;
    }

    private void unauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
            response.getWriter(),
            ApiError.of("UNAUTHORIZED", "登录状态无效或已过期")
        );
    }
}
