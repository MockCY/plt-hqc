package com.qinglian.fitness.presence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;

@Mapper
public interface PresenceMapper {

    int recordDailyOnline(
        @Param("userId") long userId,
        @Param("onlineDate") LocalDate onlineDate,
        @Param("firstSeenAt") Instant firstSeenAt
    );
}
