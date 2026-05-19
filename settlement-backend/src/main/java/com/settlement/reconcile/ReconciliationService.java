package com.settlement.reconcile;

import com.settlement.bridge.ReconciliationHandler;
import com.settlement.dao.AuditLogDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.entity.*;
import com.settlement.service.AlertWebhookService;
import com.settlement.service.DvpOrchestrator;
import com.settlement.strategy.SwiftMessageStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

/**
 * Processes inbound SWIFT settlement status replies (MT548 or sese.024),
 * reconciles against original instructions, and updates bond holdings.
 * Message format detection is delegated to {@link SwiftMessageStrategyFactory}.
 */
@Service
public class ReconciliationService implements ReconciliationHandler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final SettlementInstructionDao instructionDao;
    private final AuditLogDao auditLogDao;
    private final SwiftMessageDao swiftMessageDao;
    private final ReconciliationMetrics metrics;
    private final AlertWebhookService alertService;
    private final SwiftMessageStrategyFactory strategyFactory;
    private final DvpOrchestrator dvpOrchestrator;

    public ReconciliationService(SettlementInstructionDao instructionDao,
                                 AuditLogDao auditLogDao,
                                 SwiftMessageDao swiftMessageDao,
                                 ReconciliationMetrics metrics,
                                 AlertWebhookService alertService,
                                 SwiftMessageStrategyFactory strategyFactory,
                                 DvpOrchestrator dvpOrchestrator) {
        this.instructionDao = instructionDao;
        this.auditLogDao = auditLogDao;
        this.swiftMessageDao = swiftMessageDao;
        this.metrics = metrics;
        this.alertService = alertService;
        this.strategyFactory = strategyFactory;
        this.dvpOrchestrator = dvpOrchestrator;
    }

    @Transactional
    public void processSwiftReply(String correlationId, String rawMessage) {
        log.info("Processing SWIFT reply for correlationId={}", correlationId);

        SwiftMessageStrategy strategy = strategyFactory.detectStrategy(rawMessage);

        CanonicalStatusAdvice statusAdvice = strategy.parseStatusReply(rawMessage);

        String tradeRef = statusAdvice.transactionId();
        if (tradeRef == null || tradeRef.isBlank()) {
            log.error("Cannot extract tradeRef from inbound {} reply, correlationId={}. " +
                    "Message requires manual review.", strategy.getInboundStatusType(), correlationId);
            auditLogDao.save(new AuditLog(correlationId, AuditEventType.RECONCILE_UNMATCHED,
                    "Failed to extract tradeRef from " + strategy.getInboundStatusType()
                    + " — message unprocessable, requires manual review"));
            metrics.recordUnmatched();
            alertService.sendUnknownStatusAlert(correlationId, null);
            return;
        }

        Optional<SettlementInstruction> optInstruction = instructionDao.findByTradeRef(tradeRef);
        if (optInstruction.isEmpty()) {
            log.warn("No matching instruction found for tradeRef={}", tradeRef);
            auditLogDao.save(new AuditLog(tradeRef, AuditEventType.RECONCILE_UNMATCHED,
                    "No instruction found for incoming " + strategy.getInboundStatusType()));
            metrics.recordUnmatched();
            return;
        }

        SettlementInstruction instruction = optInstruction.get();

        if (!isEligibleForStatusUpdate(instruction)) {
            log.warn("Ignoring {} reply for instruction in non-receivable state: tradeRef={}, status={}",
                    strategy.getInboundStatusType(), tradeRef, instruction.getStatus());
            auditLogDao.save(new AuditLog(tradeRef, AuditEventType.RECONCILE_UNMATCHED,
                    "Ignored " + strategy.getInboundStatusType() + " reply: instruction status is "
                    + instruction.getStatus() + " (not eligible for status updates)"));
            return;
        }

        String inboundType = strategy.getInboundStatusType();
        int seqNo = swiftMessageDao.nextSequenceNo(
                instruction.getId(), inboundType, MessageDirection.INBOUND);
        SwiftMessage inboundMsg = new SwiftMessage(
                instruction.getId(), tradeRef,
                strategy.getStandard(), inboundType,
                MessageDirection.INBOUND, rawMessage);
        inboundMsg.setSequenceNo(seqNo);
        inboundMsg.setParsedStatus(statusAdvice.statusCode());
        inboundMsg.setParsedReason(statusAdvice.reasonText());
        swiftMessageDao.save(inboundMsg);

        switch (statusAdvice.outcome()) {
            case MATCHED -> handleMatched(instruction);
            case FAILED -> handleFailed(instruction);
            case PENDING -> handlePending(instruction);
            case UNKNOWN -> handleUnknownStatus(instruction, rawMessage);
        }

        instructionDao.save(instruction);
    }

    private void handleMatched(SettlementInstruction instruction) {
        if (instruction.getStatus() == InstructionStatus.MATCHED) {
            log.warn("Duplicate MATCHED reply ignored (idempotent): tradeRef={}", instruction.getTradeRef());
            metrics.recordMatched();
            return;
        }

        instruction.setStatus(InstructionStatus.MATCHED);
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.SETTLEMENT_MATCHED,
                "Settlement instruction matched/status confirmed: ISIN=" + instruction.getIsin() +
                " QTY=" + instruction.getQuantity() +
                " DIR=" + instruction.getDirection()));
        log.info("Settlement status MATCHED, automatic DVP orchestration queued after commit: tradeRef={}",
                instruction.getTradeRef());
        metrics.recordMatched();
        scheduleDvpAfterCommit(instruction.getTradeRef());
    }

    private void handleFailed(SettlementInstruction instruction) {
        if (instruction.getStatus() == InstructionStatus.FAILED) {
            log.warn("Duplicate FAILED reply ignored (idempotent): tradeRef={}", instruction.getTradeRef());
            metrics.recordFailed();
            return;
        }

        instruction.setStatus(InstructionStatus.FAILED);
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.SETTLEMENT_FAILED,
                "Settlement failed for ISIN=" + instruction.getIsin()));
        log.warn("Settlement FAILED: tradeRef={}", instruction.getTradeRef());
        metrics.recordFailed();
    }

    private void handlePending(SettlementInstruction instruction) {
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.SETTLEMENT_PENDING,
                "Settlement still pending for ISIN=" + instruction.getIsin()));
        log.info("Settlement still PENDING: tradeRef={}", instruction.getTradeRef());
        metrics.recordPending();
    }

    private void handleUnknownStatus(SettlementInstruction instruction, String rawMessage) {
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.SETTLEMENT_STATUS_UNKNOWN,
                "Unable to parse settlement status for ISIN=" + instruction.getIsin()
                        + ", requires manual review"));
        log.error("Settlement status UNKNOWN (unparseable): tradeRef={}, rawLength={}",
                instruction.getTradeRef(),
                rawMessage != null ? rawMessage.length() : 0);
        metrics.recordUnknown();
        alertService.sendUnknownStatusAlert(instruction.getTradeRef(), instruction.getIsin());
    }

    /**
     * Only instructions that have been sent (or already matched for idempotent re-processing)
     * are eligible to receive replies. Instructions in PENDING, SUBMITTING,
     * RETRYING, or CANCELLED states should not be processing inbound messages.
     */
    private static boolean isEligibleForStatusUpdate(SettlementInstruction instruction) {
        if (instruction.isFinal()) {
            return false;
        }
        return switch (instruction.getStatus()) {
            case SENT, MATCHED, FAILED, DVP_LOCKED -> true;
            case PENDING, SUBMITTING, RETRYING, CANCELLED -> false;
        };
    }

    private void scheduleDvpAfterCommit(String tradeRef) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dvpOrchestrator.processMatchedInstructionAsync(tradeRef);
                }
            });
        } else {
            dvpOrchestrator.processMatchedInstructionAsync(tradeRef);
        }
    }
}
