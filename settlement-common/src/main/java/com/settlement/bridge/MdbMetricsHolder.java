package com.settlement.bridge;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory MDB processing metrics shared between the EJB module (producer)
 * and the WAR module (consumer/REST exposure). Resides in the shared EAR lib
 * so both classloaders see the same static counters.
 *
 * <p>All operations are thread-safe via {@link AtomicLong}.
 * Counters reset on application restart (they are not persisted).
 */
public final class MdbMetricsHolder {

    private static final AtomicLong totalReceived = new AtomicLong();
    private static final AtomicLong totalSuccess = new AtomicLong();
    private static final AtomicLong totalFailed = new AtomicLong();
    private static final AtomicLong lastReceivedEpochMs = new AtomicLong();
    private static final AtomicLong lastSuccessEpochMs = new AtomicLong();
    private static final AtomicLong lastFailedEpochMs = new AtomicLong();

    private MdbMetricsHolder() {}

    public static void recordReceived() {
        totalReceived.incrementAndGet();
        lastReceivedEpochMs.set(System.currentTimeMillis());
    }

    public static void recordSuccess() {
        totalSuccess.incrementAndGet();
        lastSuccessEpochMs.set(System.currentTimeMillis());
    }

    public static void recordFailed() {
        totalFailed.incrementAndGet();
        lastFailedEpochMs.set(System.currentTimeMillis());
    }

    public static Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalReceived", totalReceived.get());
        map.put("totalSuccess", totalSuccess.get());
        map.put("totalFailed", totalFailed.get());
        map.put("lastReceivedAt", epochToIso(lastReceivedEpochMs.get()));
        map.put("lastSuccessAt", epochToIso(lastSuccessEpochMs.get()));
        map.put("lastFailedAt", epochToIso(lastFailedEpochMs.get()));
        return map;
    }

    private static String epochToIso(long epochMs) {
        if (epochMs == 0) {
            return null;
        }
        return Instant.ofEpochMilli(epochMs).toString();
    }
}
