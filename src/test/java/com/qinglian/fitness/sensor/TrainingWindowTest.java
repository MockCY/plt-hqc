package com.qinglian.fitness.sensor;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class TrainingWindowTest {
    @Test void exactThreeMinuteBoundaryClosesSession() {
        Instant last = Instant.parse("2026-09-07T12:00:00Z");
        assertFalse(TrainingWindow.expired(last,last.plusMillis(179999)));
        assertTrue(TrainingWindow.expired(last,last.plusSeconds(180)));
        assertEquals(520000,TrainingWindow.elapsedMs(last.minusSeconds(520),last));
    }
    @Test void elapsedNeverBecomesNegative() {
        Instant now = Instant.now(); assertEquals(0,TrainingWindow.elapsedMs(now,now.minusSeconds(1)));
    }
}
