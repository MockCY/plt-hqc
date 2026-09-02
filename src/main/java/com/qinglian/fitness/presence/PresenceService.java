package com.qinglian.fitness.presence;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PresenceService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration LEGACY_HEARTBEAT_TIMEOUT = Duration.ofSeconds(75);

    private final PresenceMapper mapper;
    private final ConcurrentHashMap<Long, Set<String>> sessionsByUser = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Instant>> legacyHeartbeatsByUser =
        new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, LocalDate> recordedDatesByUser = new ConcurrentHashMap<>();

    public PresenceService(PresenceMapper mapper) {
        this.mapper = mapper;
    }

    public void connected(long userId, String sessionId) {
        Instant now = Instant.now();
        recordDailyOnline(userId, now);
        sessionsByUser.compute(userId, (ignored, sessions) -> {
            Set<String> activeSessions = sessions == null ? ConcurrentHashMap.newKeySet() : sessions;
            activeSessions.add(sessionId);
            return activeSessions;
        });
    }

    public void heartbeat(long userId, String clientId) {
        Instant now = Instant.now();
        recordDailyOnline(userId, now);
        legacyHeartbeatsByUser.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
            .put(clientId, now);
    }

    public void offline(long userId, String clientId) {
        legacyHeartbeatsByUser.computeIfPresent(userId, (ignored, clients) -> {
            clients.remove(clientId);
            return clients.isEmpty() ? null : clients;
        });
    }

    private void recordDailyOnline(long userId, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, BUSINESS_ZONE);
        recordedDatesByUser.compute(userId, (ignored, recordedDate) -> {
            if (!today.equals(recordedDate)) {
                mapper.recordDailyOnline(userId, today, now);
            }
            return today;
        });
    }

    public void disconnected(long userId, String sessionId) {
        sessionsByUser.computeIfPresent(userId, (ignored, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public long currentOnlineCount() {
        Instant cutoff = Instant.now().minus(LEGACY_HEARTBEAT_TIMEOUT);
        legacyHeartbeatsByUser.forEach((userId, clients) ->
            legacyHeartbeatsByUser.computeIfPresent(userId, (ignored, activeClients) -> {
                activeClients.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
                return activeClients.isEmpty() ? null : activeClients;
            })
        );
        Set<Long> onlineUsers = new HashSet<>(sessionsByUser.keySet());
        onlineUsers.addAll(legacyHeartbeatsByUser.keySet());
        return onlineUsers.size();
    }
}
