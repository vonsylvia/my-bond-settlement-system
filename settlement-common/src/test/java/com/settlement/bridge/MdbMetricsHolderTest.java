package com.settlement.bridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MdbMetricsHolderTest {

    @BeforeEach
    void resetCounters() throws Exception {
        resetField("totalReceived");
        resetField("totalSuccess");
        resetField("totalFailed");
        resetField("lastReceivedEpochMs");
        resetField("lastSuccessEpochMs");
        resetField("lastFailedEpochMs");
    }

    @Test
    void recordReceived_incrementsCounterAndSetsTimestamp() {
        MdbMetricsHolder.recordReceived();
        MdbMetricsHolder.recordReceived();

        Map<String, Object> snapshot = MdbMetricsHolder.snapshot();
        assertEquals(2L, snapshot.get("totalReceived"));
        assertNotNull(snapshot.get("lastReceivedAt"));
    }

    @Test
    void recordSuccess_incrementsCounter() {
        MdbMetricsHolder.recordSuccess();

        Map<String, Object> snapshot = MdbMetricsHolder.snapshot();
        assertEquals(1L, snapshot.get("totalSuccess"));
        assertNotNull(snapshot.get("lastSuccessAt"));
    }

    @Test
    void recordFailed_incrementsCounter() {
        MdbMetricsHolder.recordFailed();
        MdbMetricsHolder.recordFailed();
        MdbMetricsHolder.recordFailed();

        Map<String, Object> snapshot = MdbMetricsHolder.snapshot();
        assertEquals(3L, snapshot.get("totalFailed"));
        assertNotNull(snapshot.get("lastFailedAt"));
    }

    @Test
    void snapshot_returnsNullTimestamps_whenNoEventsRecorded() {
        Map<String, Object> snapshot = MdbMetricsHolder.snapshot();
        assertEquals(0L, snapshot.get("totalReceived"));
        assertEquals(0L, snapshot.get("totalSuccess"));
        assertEquals(0L, snapshot.get("totalFailed"));
        assertNull(snapshot.get("lastReceivedAt"));
        assertNull(snapshot.get("lastSuccessAt"));
        assertNull(snapshot.get("lastFailedAt"));
    }

    @Test
    void snapshot_returnsIsoTimestampFormat() {
        MdbMetricsHolder.recordReceived();

        Map<String, Object> snapshot = MdbMetricsHolder.snapshot();
        String ts = (String) snapshot.get("lastReceivedAt");
        assertTrue(ts.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z"));
    }

    private void resetField(String fieldName) throws Exception {
        Field field = MdbMetricsHolder.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        ((AtomicLong) field.get(null)).set(0);
    }
}
