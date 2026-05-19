package com.settlement.service;

import com.settlement.canonical.PaymentType;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Starts book-entry DVP processing after a settlement status reply confirms
 * that an instruction is matched and ready to settle.
 */
@Service
public class DvpOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DvpOrchestrator.class);

    private final SettlementInstructionDao instructionDao;
    private final DvpSettlementService dvpSettlementService;
    private final Executor settlementExecutor;

    public DvpOrchestrator(SettlementInstructionDao instructionDao,
                           DvpSettlementService dvpSettlementService,
                           @Qualifier("settlementExecutor") Executor settlementExecutor) {
        this.instructionDao = instructionDao;
        this.dvpSettlementService = dvpSettlementService;
        this.settlementExecutor = settlementExecutor;
    }

    public void processMatchedInstructionAsync(String tradeRef) {
        try {
            settlementExecutor.execute(() -> processMatchedInstruction(tradeRef));
        } catch (RejectedExecutionException e) {
            log.error("DVP orchestration rejected by executor: tradeRef={}", tradeRef, e);
            dvpSettlementService.recordAutomaticDvpFailure(
                    tradeRef, "DVP orchestration executor rejected task");
        }
    }

    private void processMatchedInstruction(String tradeRef) {
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef).orElse(null);
        if (instruction == null) {
            log.warn("DVP orchestration skipped: instruction not found, tradeRef={}", tradeRef);
            return;
        }

        if (instruction.isFinal()) {
            log.info("DVP orchestration skipped: instruction already final, tradeRef={}", tradeRef);
            return;
        }

        if (!PaymentType.AGAINST_PAYMENT.name().equals(instruction.getPaymentType())) {
            log.info("DVP orchestration skipped for FOP instruction: tradeRef={}", tradeRef);
            return;
        }

        if (instruction.getStatus() != InstructionStatus.MATCHED
                && instruction.getStatus() != InstructionStatus.SENT) {
            log.info("DVP orchestration skipped: tradeRef={}, status={}",
                    tradeRef, instruction.getStatus());
            return;
        }

        try {
            dvpSettlementService.lockForDvp(instruction);
            SettlementInstruction locked = instructionDao.findByTradeRef(tradeRef).orElse(instruction);
            dvpSettlementService.completeDvp(locked);
            log.info("Automatic DVP orchestration completed: tradeRef={}", tradeRef);
        } catch (Exception e) {
            log.error("Automatic DVP orchestration failed: tradeRef={}", tradeRef, e);
            rollbackIfLocked(tradeRef);
            dvpSettlementService.recordAutomaticDvpFailure(tradeRef, e.getMessage());
        }
    }

    private void rollbackIfLocked(String tradeRef) {
        try {
            instructionDao.findByTradeRef(tradeRef)
                    .filter(i -> i.getStatus() == InstructionStatus.DVP_LOCKED)
                    .ifPresent(dvpSettlementService::rollbackDvp);
        } catch (Exception rollbackEx) {
            log.error("Automatic DVP rollback failed: tradeRef={}", tradeRef, rollbackEx);
        }
    }

}
