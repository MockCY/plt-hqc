package com.qinglian.fitness.presence;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Supplier;

@Service
public class PresenceService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    public static final int TIMEOUT_SECONDS = 75;
    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final PresenceMapper mapper;
    private final TransactionTemplate writes;

    public PresenceService(PresenceMapper mapper, PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        writes = new TransactionTemplate(transactionManager);
        writes.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        writes.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public void connected(long userId, String sessionId) { write(() -> { touch(userId, "ws:" + sessionId); return null; }); }

    public void heartbeat(long userId, String clientId) { write(() -> { touch(userId, "http:" + clientId); return null; }); }

    public void offline(long userId, String clientId) { close(userId, "http:" + clientId); }

    private void touch(long userId, String clientId) {
        Instant now = Instant.now();
        mapper.expireClient(userId, clientId, now.minusSeconds(TIMEOUT_SECONDS), TIMEOUT_SECONDS);
        mapper.touch(userId, clientId, now);
        mapper.recordDailyOnline(userId, LocalDate.ofInstant(now, BUSINESS_ZONE), now);
    }

    public void disconnected(long userId, String sessionId) { close(userId, "ws:" + sessionId); }

    private void close(long userId, String clientId) {
        write(() -> mapper.close(userId, clientId, Instant.now(), TIMEOUT_SECONDS));
    }

    @Scheduled(fixedDelay = 15000)
    public void expireIdleSessions() {
        Instant cutoff = Instant.now().minusSeconds(TIMEOUT_SECONDS);
        for (PresenceMapper.ExpiredClient client : mapper.findExpiredClients(cutoff, 200)) {
            try {
                // Match the heartbeat's unique-key lock path and recheck expiry after acquiring the lock.
                write(() -> mapper.expireClient(client.userId(), client.clientId(), cutoff, TIMEOUT_SECONDS));
            } catch (ConcurrencyFailureException exception) {
                log.warn("Presence expiry deferred after lock conflicts: userId={}", client.userId());
            }
        }
    }

    private <T> T write(Supplier<T> action) {
        for (int attempt = 1; ; attempt++) {
            try {
                return writes.execute(status -> action.get());
            } catch (ConcurrencyFailureException exception) {
                // TransactionTemplate has rolled back before retrying the entire operation.
                if (attempt == 3) throw exception;
            }
        }
    }

    public long currentOnlineCount() {
        return mapper.countOnline(Instant.now().minusSeconds(TIMEOUT_SECONDS));
    }

    public List<PresenceMapper.Summary> summaries(List<Long> userIds) {
        if (userIds.isEmpty()) return List.of();
        return mapper.summaries(userIds, Instant.now().minusSeconds(TIMEOUT_SECONDS), TIMEOUT_SECONDS);
    }

    public List<PresenceMapper.Visit> history(long userId, int limit, int offset) {
        return mapper.history(userId, limit, offset, Instant.now().minusSeconds(TIMEOUT_SECONDS), TIMEOUT_SECONDS);
    }

    public long historyCount(long userId) { return mapper.historyCount(userId); }
}
