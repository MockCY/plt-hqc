package com.qinglian.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface CampaignMapper {

    int createCheckin(
        @Param("userId") long userId,
        @Param("code") String code,
        @Param("date") LocalDate date
    );

    int countForDate(
        @Param("userId") long userId,
        @Param("code") String code,
        @Param("date") LocalDate date
    );

    int countAll(@Param("userId") long userId, @Param("code") String code);
}
