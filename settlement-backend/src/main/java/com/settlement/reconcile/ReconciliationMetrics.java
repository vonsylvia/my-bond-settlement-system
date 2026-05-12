package com.settlement.reconcile;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory counters for MT548 reconciliation outcomes.
 * Follows the same AtomicLong pattern as {@link com.settlement.bridge.MdbMetricsHolder}.
 *
 * <p>Thread-safe; counters reset on application restart.
 */
@Component
public class ReconciliationMetrics {

    private final AtomicLong totalProcessed = new AtomicLong();
    private final AtomicLong totalMatched = new AtomicLong();
    private final AtomicLong totalFailed = new AtomicLong();
    private final AtomicLong totalPending = new AtomicLong();
    private final AtomicLong totalUnknown = new AtomicLong();
    private final AtomicLong totalUnmatched = new AtomicLong();

    private final AtomicLong lastProcessedEpochMs = new AtomicLong();
    private final AtomicLong lastUnknownEpochMs = new AtomicLong();

    public void recordMatched() {
        totalProcessed.incrementAndGet();
        totalMatched.incrementAndGet();
        lastProcessedEpochMs.set(System.currentTimeMillis());
    }

    public void recordFailed() {
        totalProcessed.incrementAndGet();
        totalFailed.incrementAndGet();
        lastProcessedEpochMs.set(System.currentTimeMillis());
    }

    public void recordPending() {
        totalProcessed.incrementAndGet();
        totalPending.incrementAndGet();
        lastProcessedEpochMs.set(System.currentTimeMillis());
    }

    public void recordUnknown() {
        totalProcessed.incrementAndGet();
        totalUnknown.incrementAndGet();
        lastProcessedEpochMs.set(System.currentTimeMillis());
        lastUnknownEpochMs.set(System.currentTimeMillis());
    }

    public void recordUnmatched() {
        totalProcessed.incrementAndGet();
        totalUnmatched.incrementAndGet();
        lastProcessedEpochMs.set(System.currentTimeMillis());
    }

    public long getUnknownCount() {
        return totalUnknown.get();
    }

    public long getUnmatchedCount() {
        return totalUnmatched.get();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalProcessed", totalProcessed.get());
        map.put("totalMatched", totalMatched.get());
        map.put("totalFailed", totalFailed.get());
        map.put("totalPending", totalPending.get());
        map.put("totalUnknown", totalUnknown.get());
        map.put("totalUnmatched", totalUnmatched.get());
        map.put("lastProcessedAt", epochToIso(lastProcessedEpochMs.get()));
        map.put("lastUnknownAt", epochToIso(lastUnknownEpochMs.get()));
        return map;
    }

    private static String epochToIso(long epochMs) {
        return epochMs == 0 ? null : Instant.ofEpochMilli(epochMs).toString();
    }
}
