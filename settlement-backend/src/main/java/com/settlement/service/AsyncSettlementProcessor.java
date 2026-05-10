package com.settlement.service;

import com.settlement.entity.SettlementInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Orchestrates async settlement processing with exponential backoff retry.
 * Delegates all transactional work to {@link SettlementXaExecutor} to
 * avoid Spring @Transactional self-invocation (AOP proxy bypass).
 */
@Service
public class AsyncSettlementProcessor {

    private static final Logger log = LoggerFactory.getLogger(AsyncSettlementProcessor.class);

    static final int MAX_RETRY_COUNT = 3;
    private static final long INITIAL_BACKOFF_MS = 2_000;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final SettlementXaExecutor xaExecutor;
    private final AlertWebhookService alertWebhookService;
    private final Executor settlementExecutor;

    public AsyncSettlementProcessor(SettlementXaExecutor xaExecutor,
                                    AlertWebhookService alertWebhookService,
                                    @Qualifier("settlementExecutor") Executor settlementExecutor) {
        this.xaExecutor = xaExecutor;
        this.alertWebhookService = alertWebhookService;
        this.settlementExecutor = settlementExecutor;
    }

    /**
     * Phase 2 (async): submit settlement processing to the executor.
     * Backoff: 2s → 4s → 8s (capped at 30s).
     * Stays FAILED after all retries; traders can manually retry via API.
     */
    public void processSettlementAsync(String tradeRef) {
        settlementExecutor.execute(() -> doProcess(tradeRef));
    }

    /**
     * Crash-recovery: scan for orphaned instructions and re-trigger async processing.
     */
    public void recoverOrphanedInstructions() {
        List<String> tradeRefs = xaExecutor.recoverOrphanedInstructions();
        for (String tradeRef : tradeRefs) {
            processSettlementAsync(tradeRef);
        }
    }

    private void doProcess(String tradeRef) {
        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                xaExecutor.executeSettlement(tradeRef);
                return;
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

                long backoffMs = Math.min(
                        INITIAL_BACKOFF_MS * (1L << (attempt - 1)),
                        MAX_BACKOFF_MS);
                log.info("Retrying in {}ms: tradeRef={}, nextAttempt={}",
                        backoffMs, tradeRef, attempt + 1);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Retry sleep interrupted: tradeRef={}", tradeRef);
                    return;
                }
            }
        }
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
