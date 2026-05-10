package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.AuditLog;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.jms.SwiftMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final SwiftMessageSender messageSender;

    public SettlementXaExecutor(SettlementInstructionDao instructionDao,
                                AuditLogDao auditLogDao,
                                SwiftMessageSender messageSender) {
        this.instructionDao = instructionDao;
        this.auditLogDao = auditLogDao;
        this.messageSender = messageSender;
    }

    /**
     * Single attempt: set SUBMITTING, send JMS, set SENT.
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

        instruction.setStatus(InstructionStatus.SUBMITTING);
        instructionDao.save(instruction);

        messageSender.sendSwiftMessage(tradeRef, instruction.getMt541Raw());

        instruction.setStatus(InstructionStatus.SENT);
        instruction.setFailureReason(null);
        instruction.setRetryCount(0);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, "INSTRUCTION_SENT",
                "MT541 sent via async XA"));

        log.info("Async settlement completed: tradeRef={}", tradeRef);
    }

    @Transactional
    public void recordFailure(String tradeRef, int attempt, Exception e,
                              boolean exhausted, int maxRetryCount) {
        String reason = e.getMessage();
        if (reason != null && reason.length() > 1000) {
            reason = reason.substring(0, 1000);
        }

        int updated = instructionDao.updateFailure(tradeRef, attempt, reason);
        if (updated == 0) {
            log.warn("recordFailure: instruction not found, skipping: tradeRef={}", tradeRef);
            return;
        }

        String eventType = exhausted ? "RETRIES_EXHAUSTED" : "INSTRUCTION_FAILED";
        String detail = String.format("Async XA failed (attempt %d/%d): %s",
                attempt, maxRetryCount, reason);
        auditLogDao.save(new AuditLog(tradeRef, eventType, detail));
    }

    /**
     * Crash-recovery: reset orphaned SUBMITTING instructions back to PENDING,
     * then return all PENDING trade refs (including just-reset ones) for re-processing.
     */
    @Transactional
    public List<String> recoverOrphanedInstructions() {
        int resetCount = instructionDao.bulkUpdateStatus(InstructionStatus.SUBMITTING, InstructionStatus.PENDING);
        if (resetCount > 0) {
            log.warn("Recovered {} orphaned SUBMITTING instruction(s) to PENDING", resetCount);
        }

        List<SettlementInstruction> pending =
                instructionDao.findByStatus(InstructionStatus.PENDING);

        List<String> tradeRefs = pending.stream().map(SettlementInstruction::getTradeRef).toList();
        if (!tradeRefs.isEmpty()) {
            log.warn("Re-queuing {} orphaned PENDING instruction(s): {}", tradeRefs.size(), tradeRefs);
        }

        return tradeRefs;
    }

    @Transactional(readOnly = true)
    public SettlementInstruction findByTradeRef(String tradeRef) {
        return instructionDao.findByTradeRef(tradeRef).orElse(null);
    }
}
