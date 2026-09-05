package com.qinglian.fitness.presence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Mapper
public interface PresenceMapper {

    record Summary(long userId, boolean online, Instant lastOnlineAt, Instant lastOfflineAt) {}
    record Visit(long id, Instant onlineAt, Instant offlineAt, String endReason) {}
    record ExpiredClient(long userId, String clientId) {}

    int touch(@Param("userId") long userId, @Param("clientId") String clientId, @Param("now") Instant now);
    int close(@Param("userId") long userId, @Param("clientId") String clientId,
              @Param("now") Instant now, @Param("timeout") int timeout);
    int expireClient(@Param("userId") long userId, @Param("clientId") String clientId,
                     @Param("cutoff") Instant cutoff, @Param("timeout") int timeout);
    List<ExpiredClient> findExpiredClients(@Param("cutoff") Instant cutoff, @Param("limit") int limit);
    long countOnline(@Param("cutoff") Instant cutoff);
    List<Summary> summaries(@Param("userIds") List<Long> userIds,
                            @Param("cutoff") Instant cutoff, @Param("timeout") int timeout);
    List<Visit> history(@Param("userId") long userId, @Param("limit") int limit, @Param("offset") int offset,
                        @Param("cutoff") Instant cutoff, @Param("timeout") int timeout);
    long historyCount(@Param("userId") long userId);

    int recordDailyOnline(
        @Param("userId") long userId,
        @Param("onlineDate") LocalDate onlineDate,
        @Param("firstSeenAt") Instant firstSeenAt
    );
}
