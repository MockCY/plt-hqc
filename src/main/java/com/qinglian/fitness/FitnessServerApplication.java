package com.qinglian.fitness;

import com.qinglian.fitness.config.AppProperties;
import com.qinglian.fitness.config.WechatProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AppProperties.class, WechatProperties.class})
public class FitnessServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitnessServerApplication.class, args);
    }
}
