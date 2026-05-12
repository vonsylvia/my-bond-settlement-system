package com.settlement.service;

import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.NonRetryableSettlementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.*;

/**
 * Orchestrates async settlement processing with non-blocking exponential backoff retry.
 * Uses a ScheduledExecutorService for delay scheduling (no Thread.sleep) and delegates
 * all transactional work to {@link SettlementXaExecutor}.
 */
@Service
public class AsyncSettlementProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncSettlementProcessor.class);

    static final int MAX_RETRY_COUNT = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 30_000;
    private static final int ORPHAN_RECOVERY_BATCH_SIZE = 20;

    private final SettlementXaExecutor xaExecutor;
    private final AlertWebhookService alertWebhookService;
    private final Executor settlementExecutor;
    private final ScheduledExecutorService retryScheduler;

    @Autowired
    public AsyncSettlementProcessor(SettlementXaExecutor xaExecutor,
                                    AlertWebhookService alertWebhookService,
                                    @Qualifier("settlementExecutor") Executor settlementExecutor) {
        this(xaExecutor, alertWebhookService, settlementExecutor,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "settlement-retry-scheduler");
                    t.setDaemon(true);
                    return t;
                }));
    }

    AsyncSettlementProcessor(SettlementXaExecutor xaExecutor,
                             AlertWebhookService alertWebhookService,
                             Executor settlementExecutor,
                             ScheduledExecutorService retryScheduler) {
        this.xaExecutor = xaExecutor;
        this.alertWebhookService = alertWebhookService;
        this.settlementExecutor = settlementExecutor;
        this.retryScheduler = retryScheduler;
    }

    /**
     * Phase 2 (async): submit settlement processing to the executor.
     * Non-blocking backoff via ScheduledExecutorService.
     */
    public void processSettlementAsync(String tradeRef) {
        submitWithRejectionHandling(tradeRef, 1);
    }

    /**
     * Crash-recovery: scan for orphaned instructions and re-trigger async processing.
     * Submissions are batched with staggered delays to avoid overwhelming the pool.
     */
    public void recoverOrphanedInstructions() {
        List<String> tradeRefs = xaExecutor.recoverOrphanedInstructions();
        for (int i = 0; i < tradeRefs.size(); i++) {
            String tradeRef = tradeRefs.get(i);
            long delayMs = (long) (i / ORPHAN_RECOVERY_BATCH_SIZE) * 1_000L;
            if (delayMs == 0) {
                submitWithRejectionHandling(tradeRef, 1);
            } else {
                retryScheduler.schedule(
                        () -> submitWithRejectionHandling(tradeRef, 1),
                        delayMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    private void submitWithRejectionHandling(String tradeRef, int attempt) {
        try {
            settlementExecutor.execute(() -> doAttempt(tradeRef, attempt));
        } catch (RejectedExecutionException e) {
            log.error("Executor rejected settlement task (pool saturated): tradeRef={}, attempt={}. " +
                    "Will be recovered by scheduler.", tradeRef, attempt);
            try {
                xaExecutor.recordFailure(tradeRef, attempt,
                        "Executor pool saturated — task rejected", false, MAX_RETRY_COUNT);
            } catch (Exception dbEx) {
                log.error("Failed to record rejection in DB: tradeRef={}", tradeRef, dbEx);
            }
        }
    }

    private void doAttempt(String tradeRef, int attempt) {
        try {
            xaExecutor.executeSettlement(tradeRef);
        } catch (NonRetryableSettlementException e) {
            log.error("Non-retryable failure (no further retries): tradeRef={}", tradeRef, e);
            try {
                xaExecutor.recordFailure(tradeRef, attempt, e.getMessage(), true, MAX_RETRY_COUNT);
            } catch (Exception dbEx) {
                log.error("Failed to record non-retryable failure in DB: tradeRef={}", tradeRef, dbEx);
            }
            sendExhaustedAlert(tradeRef);
        } catch (Exception e) {
            log.error("Settlement attempt {}/{} failed: tradeRef={}",
                    attempt, MAX_RETRY_COUNT, tradeRef, e);

            boolean exhausted = (attempt >= MAX_RETRY_COUNT);
            try {
                xaExecutor.recordFailure(tradeRef, attempt, e.getMessage(), exhausted, MAX_RETRY_COUNT);
            } catch (Exception dbEx) {
                log.error("Failed to record failure in DB: tradeRef={}", tradeRef, dbEx);
            }

            if (exhausted) {
                sendExhaustedAlert(tradeRef);
                return;
            }

            long backoffMs = computeBackoffWithJitter(attempt);
            log.info("Scheduling retry in {}ms: tradeRef={}, nextAttempt={}",
                    backoffMs, tradeRef, attempt + 1);

            retryScheduler.schedule(
                    () -> submitWithRejectionHandling(tradeRef, attempt + 1),
                    backoffMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Full jitter exponential backoff: uniform random in [0, min(cap, base * 2^attempt)].
     * Prevents thundering herd when many settlements fail simultaneously.
     */
    private long computeBackoffWithJitter(int attempt) {
        long exponentialMs = Math.min(INITIAL_BACKOFF_MS * (1L << (attempt - 1)), MAX_BACKOFF_MS);
        return ThreadLocalRandom.current().nextLong(exponentialMs / 2, exponentialMs + 1);
    }

    private void sendExhaustedAlert(String tradeRef) {
        try {
            SettlementInstruction instruction = xaExecutor.findByTradeRef(tradeRef);
            if (instruction != null) {
                log.warn("ALERT: All {} retries exhausted for tradeRef={}, awaiting manual retry. Reason: {}",
                        MAX_RETRY_COUNT, tradeRef, instruction.getFailureReason());
                alertWebhookService.sendExhaustedAlert(instruction);
            }
        } catch (Exception e) {
            log.error("Failed to send exhausted alert: tradeRef={}", tradeRef, e);
        }
    }
}
