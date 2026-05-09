package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dto.HoldingResponse;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.*;
import com.settlement.exception.BusinessException;
import com.settlement.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementInstructionDao instructionDao;
    private final BondHoldingDao holdingDao;
    private final AuditLogDao auditLogDao;
    private final SwiftMessageBuilder messageBuilder;
    private final ObjectProvider<AsyncSettlementProcessor> asyncProcessorProvider;

    public SettlementService(SettlementInstructionDao instructionDao,
                             BondHoldingDao holdingDao,
                             AuditLogDao auditLogDao,
                             SwiftMessageBuilder messageBuilder,
                             ObjectProvider<AsyncSettlementProcessor> asyncProcessorProvider) {
        this.instructionDao = instructionDao;
        this.holdingDao = holdingDao;
        this.auditLogDao = auditLogDao;
        this.messageBuilder = messageBuilder;
        this.asyncProcessorProvider = asyncProcessorProvider;
    }

    /**
     * Phase 1 (synchronous): validate, persist instruction with PENDING status,
     * build MT541 message, then trigger async XA processing.
     * Returns immediately after DB save — no JMS involved.
     */
    @Transactional
    public SettlementInstruction submitInstruction(SettlementRequest request) {
        String tradeRef = generateTradeRef();

        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef(tradeRef);
        instruction.setIsin(request.getIsin());
        instruction.setSettlementDate(request.getSettlementDate());
        instruction.setQuantity(request.getQuantity());
        instruction.setCounterparty(request.getCounterparty());
        instruction.setBicCode(request.getBicCode());
        instruction.setDirection(Direction.valueOf(request.getDirection()));
        instruction.setAccountId(request.getAccountId());
        instruction.setStatus(InstructionStatus.PENDING);

        String mt541Message = messageBuilder.buildMT541(instruction);
        instruction.setMt541Raw(mt541Message);

        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, "INSTRUCTION_CREATED",
                "Instruction saved, async XA processing queued. ISIN=" + request.getIsin()
                        + " QTY=" + request.getQuantity()));

        log.info("Settlement instruction created: tradeRef={}, ISIN={}, direction={} — async send queued",
                tradeRef, request.getIsin(), request.getDirection());

        asyncProcessorProvider.getObject().processSettlementAsync(tradeRef);

        return instruction;
    }

    @Transactional
    public SettlementInstruction findByTradeRef(String tradeRef) {
        return instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement instruction not found: " + tradeRef));
    }

    /**
     * Reset a FAILED instruction back to PENDING and trigger async retry.
     */
    @Transactional
    public SettlementInstruction manualRetry(String tradeRef) {
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement instruction not found: " + tradeRef));

        if (instruction.getStatus() != InstructionStatus.FAILED) {
            throw new BusinessException(
                    "Cannot retry instruction in status " + instruction.getStatus()
                            + ". Only FAILED instructions can be retried.");
        }

        instruction.setStatus(InstructionStatus.PENDING);
        instruction.setRetryCount(0);
        instruction.setFailureReason(null);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, "MANUAL_RETRY",
                "Manual retry triggered, status reset to PENDING"));

        log.info("Manual retry triggered: tradeRef={}", tradeRef);

        asyncProcessorProvider.getObject().processSettlementAsync(tradeRef);

        return instruction;
    }

    @Transactional
    public List<SettlementInstruction> findAll(int page, int size) {
        return instructionDao.findAll(page, size);
    }

    @Transactional
    public long count() {
        return instructionDao.count();
    }

    @Transactional
    public List<HoldingResponse> getHoldings(String accountId) {
        List<BondHolding> holdings;
        if (accountId != null && !accountId.isBlank()) {
            holdings = holdingDao.findByAccount(accountId);
        } else {
            holdings = holdingDao.findAll();
        }
        return holdings.stream().map(this::toHoldingResponse).toList();
    }

    private HoldingResponse toHoldingResponse(BondHolding holding) {
        HoldingResponse resp = new HoldingResponse();
        resp.setAccountId(holding.getAccountId());
        resp.setIsin(holding.getIsin());
        resp.setQuantity(holding.getQuantity());
        resp.setUpdatedAt(holding.getUpdatedAt());
        return resp;
    }

    private String generateTradeRef() {
        return "TR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
