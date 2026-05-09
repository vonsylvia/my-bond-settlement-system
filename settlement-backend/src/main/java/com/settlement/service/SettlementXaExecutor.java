package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.AuditLog;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.jms.SwiftMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
    private final ObjectProvider<SwiftMessageSender> messageSenderProvider;

    public SettlementXaExecutor(SettlementInstructionDao instructionDao,
                                AuditLogDao auditLogDao,
                                ObjectProvider<SwiftMessageSender> messageSenderProvider) {
        this.instructionDao = instructionDao;
        this.auditLogDao = auditLogDao;
        this.messageSenderProvider = messageSenderProvider;
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

        messageSenderProvider.getObject()
                .sendSwiftMessage(tradeRef, instruction.getMt541Raw());

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
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef)
                .orElse(null);
        if (instruction == null) return;

        String reason = e.getMessage();
        if (reason != null && reason.length() > 1000) {
            reason = reason.substring(0, 1000);
        }

        instruction.setRetryCount(attempt);
        instruction.setFailureReason(reason);
        instruction.setStatus(InstructionStatus.FAILED);
        instructionDao.save(instruction);

        String eventType = exhausted ? "RETRIES_EXHAUSTED" : "INSTRUCTION_FAILED";
        String detail = String.format("Async XA failed (attempt %d/%d): %s",
                attempt, maxRetryCount, reason);
        auditLogDao.save(new AuditLog(tradeRef, eventType, detail));
    }

    /**
     * Crash-recovery: reset orphaned SUBMITTING instructions back to PENDING.
     */
    @Transactional
    public List<String> recoverOrphanedInstructions() {
        List<SettlementInstruction> submitting =
                instructionDao.findByStatus(InstructionStatus.SUBMITTING);
        for (SettlementInstruction instr : submitting) {
            log.warn("Recovering orphaned SUBMITTING instruction: tradeRef={}",
                    instr.getTradeRef());
            instr.setStatus(InstructionStatus.PENDING);
            instructionDao.save(instr);
        }

        List<SettlementInstruction> pending =
                instructionDao.findByStatus(InstructionStatus.PENDING);

        List<String> tradeRefs = new java.util.ArrayList<>();
        for (SettlementInstruction instr : submitting) {
            tradeRefs.add(instr.getTradeRef());
        }
        for (SettlementInstruction instr : pending) {
            log.warn("Recovering orphaned PENDING instruction: tradeRef={}",
                    instr.getTradeRef());
            tradeRefs.add(instr.getTradeRef());
        }
        return tradeRefs;
    }

    @Transactional(readOnly = true)
    public SettlementInstruction findByTradeRef(String tradeRef) {
        return instructionDao.findByTradeRef(tradeRef).orElse(null);
    }
}
