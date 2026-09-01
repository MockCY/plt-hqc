package com.qinglian.fitness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.admin")
public record AdminProperties(String username, String password, Duration sessionTtl) {
}
