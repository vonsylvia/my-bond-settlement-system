package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.entity.*;
import com.settlement.exception.NonRetryableSettlementException;
import com.settlement.jms.SwiftMessageSender;
import com.settlement.strategy.CanonicalMapper;
import com.settlement.strategy.SwiftMessageStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Handles transactional (XA) operations for settlement processing.
 * Separated from AsyncSettlementProcessor to avoid Spring @Transactional
 * self-invocation issues (AOP proxy bypass).
 */
@Service
public class SettlementXaExecutor {

    private static final Logger log = LoggerFactory.getLogger(SettlementXaExecutor.class);

    private final SettlementInstructionDao instructionDao;
    private final AuditLogDao auditLogDao;
    private final SwiftMessageDao swiftMessageDao;
    private final SwiftMessageSender messageSender;
    private final SwiftMessageStrategyFactory strategyFactory;
    private final CanonicalMapper canonicalMapper;

    public SettlementXaExecutor(SettlementInstructionDao instructionDao,
                                AuditLogDao auditLogDao,
                                SwiftMessageDao swiftMessageDao,
                                SwiftMessageSender messageSender,
                                SwiftMessageStrategyFactory strategyFactory,
                                CanonicalMapper canonicalMapper) {
        this.instructionDao = instructionDao;
        this.auditLogDao = auditLogDao;
        this.swiftMessageDao = swiftMessageDao;
        this.messageSender = messageSender;
        this.strategyFactory = strategyFactory;
        this.canonicalMapper = canonicalMapper;
    }

    /**
     * Single attempt: atomically transition PENDING→SUBMITTING, send JMS, set SENT.
     * Uses CAS-style status check to prevent duplicate sends from concurrent threads.
     * Runs in its own XA transaction (DB + MQ).
     * Throws on failure so the caller can decide to retry.
     */
    @Transactional
    public void executeSettlement(String tradeRef) {
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef)
                .orElse(null);
        if (instruction == null) {
            log.warn("Instruction not found for async processing: tradeRef={}", tradeRef);
            return;
        }

        if (instruction.getStatus() == InstructionStatus.SENT
                || instruction.getStatus() == InstructionStatus.MATCHED) {
            log.info("Instruction already processed, skipping: tradeRef={}, status={}",
                    tradeRef, instruction.getStatus());
            return;
        }

        if (instruction.getStatus() == InstructionStatus.SUBMITTING) {
            log.info("Instruction already in-flight, skipping: tradeRef={}", tradeRef);
            return;
        }

        if (instruction.getStatus() != InstructionStatus.PENDING
                && instruction.getStatus() != InstructionStatus.RETRYING) {
            log.info("Instruction not in processable state, skipping: tradeRef={}, status={}",
                    tradeRef, instruction.getStatus());
            return;
        }

        int updated = instructionDao.compareAndSetStatus(
                tradeRef, instruction.getStatus(), InstructionStatus.SUBMITTING);
        if (updated == 0) {
            log.info("CAS failed (concurrent processing detected), skipping: tradeRef={}", tradeRef);
            return;
        }

        instruction.setStatus(InstructionStatus.SUBMITTING);

        SwiftMessageStrategy strategy = strategyFactory.getStrategy(instruction.getPreferredStandard());
        CanonicalSettlement canonical = canonicalMapper.toCanonical(instruction);
        String outboundType = strategy.getOutboundMessageType(canonical);

        SwiftMessage outbound = swiftMessageDao
                .findLatestOutbound(instruction.getId(), outboundType)
                .orElseThrow(() -> new NonRetryableSettlementException(
                        "No outbound " + outboundType + " message found for tradeRef=" + tradeRef));

        messageSender.sendSwiftMessage(tradeRef, outbound.getRawPayload(),
                outboundType, instruction.getPreferredStandard());

        instruction.setStatus(InstructionStatus.SENT);
        instruction.setFailureReason(null);
        instruction.setRetryCount(0);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, AuditEventType.INSTRUCTION_SENT,
                outboundType + " sent via async XA"));

        log.info("Async settlement completed: tradeRef={}, messageType={}", tradeRef, outboundType);
    }

    @Transactional
    public void recordFailure(String tradeRef, int attempt, String errorMessage,
                              boolean exhausted, int maxRetryCount) {
        String reason = (errorMessage != null && errorMessage.length() > 1000)
                ? errorMessage.substring(0, 1000)
                : errorMessage;

        int updated = instructionDao.updateFailure(tradeRef, attempt, reason, exhausted);
        if (updated == 0) {
            log.warn("recordFailure: instruction not found, skipping: tradeRef={}", tradeRef);
            return;
        }

        AuditEventType eventType = exhausted ? AuditEventType.RETRIES_EXHAUSTED : AuditEventType.INSTRUCTION_FAILED;
        String detail = String.format("Async XA failed (attempt %d/%d): %s",
                attempt, maxRetryCount, reason);
        auditLogDao.save(new AuditLog(tradeRef, eventType, detail));
    }

    private static final long ORPHAN_THRESHOLD_MINUTES = 5;

    /**
     * Crash-recovery: reset SUBMITTING instructions that have been stuck longer
     * than {@link #ORPHAN_THRESHOLD_MINUTES} back to PENDING, then return all
     * PENDING and RETRYING trade refs for re-processing. The time threshold
     * prevents resetting instructions that are still actively being sent.
     */
    @Transactional
    public List<String> recoverOrphanedInstructions() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(ORPHAN_THRESHOLD_MINUTES);
        int resetCount = instructionDao.bulkUpdateStatusOlderThan(
                InstructionStatus.SUBMITTING, InstructionStatus.PENDING, threshold);
        if (resetCount > 0) {
            log.warn("Recovered {} orphaned SUBMITTING instruction(s) (stuck > {}min) to PENDING",
                    resetCount, ORPHAN_THRESHOLD_MINUTES);
        }

        List<SettlementInstruction> pending =
                instructionDao.findByStatus(InstructionStatus.PENDING);
        List<SettlementInstruction> retrying =
                instructionDao.findByStatus(InstructionStatus.RETRYING);

        List<String> tradeRefs = new java.util.ArrayList<>(
                pending.stream().map(SettlementInstruction::getTradeRef).toList());
        tradeRefs.addAll(retrying.stream().map(SettlementInstruction::getTradeRef).toList());

        if (!tradeRefs.isEmpty()) {
            log.info("Re-queuing {} orphaned instruction(s): {}", tradeRefs.size(), tradeRefs);
        }

        return tradeRefs;
    }

    @Transactional(readOnly = true)
    public SettlementInstruction findByTradeRef(String tradeRef) {
        return instructionDao.findByTradeRef(tradeRef).orElse(null);
    }
}
