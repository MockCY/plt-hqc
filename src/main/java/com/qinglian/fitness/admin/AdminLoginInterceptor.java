package com.qinglian.fitness.admin;

import com.qinglian.fitness.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

@Component
public class AdminLoginInterceptor implements HandlerInterceptor {

    private final AdminAuthService authService;
    private final ObjectMapper objectMapper;

    public AdminLoginInterceptor(AdminAuthService authService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) return unauthorized(response);
        String rawToken = authorization.substring(7).trim();
        var admin = authService.verify(rawToken);
        if (admin.isEmpty()) return unauthorized(response);
        request.setAttribute(AdminCurrent.ADMIN_ID_ATTRIBUTE, admin.get().id());
        request.setAttribute(AdminCurrent.ADMIN_USERNAME_ATTRIBUTE, admin.get().username());
        request.setAttribute(AdminCurrent.RAW_TOKEN_ATTRIBUTE, rawToken);
        return true;
    }

    private boolean unauthorized(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiError.of("ADMIN_UNAUTHORIZED", "后台登录状态无效或已过期"));
        return false;
    }
}
