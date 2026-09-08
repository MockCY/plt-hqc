package com.qinglian.fitness.sensor;

import java.time.Duration;
import java.time.Instant;

/** Session closure uses the last motion time, never the timeout detection time. */
public final class TrainingWindow {
    public static final Duration PAUSE = Duration.ofMinutes(3);
    private TrainingWindow() {}
    public static boolean expired(Instant lastMotion, Instant now) {
        return !now.isBefore(lastMotion.plus(PAUSE));
    }
    public static long elapsedMs(Instant start, Instant lastMotion) {
        return Math.max(0, Duration.between(start, lastMotion).toMillis());
    }
}
