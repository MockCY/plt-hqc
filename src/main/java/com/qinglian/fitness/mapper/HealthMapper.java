package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HealthMapper {

    int checkDatabase();
}
