package com.qinglian.fitness.common;

import com.qinglian.fitness.mapper.HealthMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthMapper healthMapper;

    public HealthController(HealthMapper healthMapper) {
        this.healthMapper = healthMapper;
    }

    @GetMapping
    public Map<String, Object> health() {
        healthMapper.checkDatabase();
        return Map.of("status", "UP", "time", Instant.now());
    }
}
