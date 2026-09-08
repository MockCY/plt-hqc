package com.qinglian.fitness.sensor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SensorScheduling {
    private final SensorService service;
    public SensorScheduling(SensorService service) { this.service = service; }
    @Scheduled(fixedDelay=10000,initialDelay=30000)
    public void expire() { service.expire(); }
}
