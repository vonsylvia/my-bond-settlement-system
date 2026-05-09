package com.settlement.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Crash-recovery safety net: periodically scans for instructions
 * stuck in SUBMITTING or orphaned in PENDING after a system restart.
 * Normal retries are handled inline with exponential backoff.
 */
@Component
public class SettlementRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(SettlementRetryScheduler.class);

    private final AsyncSettlementProcessor asyncProcessor;

    public SettlementRetryScheduler(AsyncSettlementProcessor asyncProcessor) {
        this.asyncProcessor = asyncProcessor;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void recoverOrphanedInstructions() {
        log.debug("Running orphaned instruction recovery scan...");
        asyncProcessor.recoverOrphanedInstructions();
    }
}
