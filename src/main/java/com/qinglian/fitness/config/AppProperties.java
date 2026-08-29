package com.qinglian.fitness.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Duration sessionTtl, Duration sessionTouchInterval) {
}
