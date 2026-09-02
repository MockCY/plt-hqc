package com.qinglian.fitness.config;

import com.qinglian.fitness.auth.LoginInterceptor;
import com.qinglian.fitness.admin.AdminLoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final AdminLoginInterceptor adminLoginInterceptor;
    private final Path mediaRoot;

    public WebConfig(LoginInterceptor loginInterceptor, AdminLoginInterceptor adminLoginInterceptor,
                     @org.springframework.beans.factory.annotation.Value("${app.media.root}") String mediaRoot) {
        this.loginInterceptor = loginInterceptor;
        this.adminLoginInterceptor = adminLoginInterceptor;
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
            .addResourceLocations(mediaRoot.toUri().toString() + "/")
            .setCachePeriod(86400);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns(
                "/api/health",
                "/api/auth/login",
                "/api/auth/wechat-phone",
                "/api/media/files/**",
                "/api/admin/**",
                "/api/courses",
                "/api/courses/**",
                "/api/exercises",
                "/api/exercises/**",
                "/api/devices",
                "/api/plans/catalog",
                "/api/plans/detail/*"
            );
        registry.addInterceptor(adminLoginInterceptor)
            .addPathPatterns("/api/admin/**")
            .excludePathPatterns("/api/admin/auth/login");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("http://127.0.0.1:*", "http://localhost:*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .maxAge(3600);
    }
}
