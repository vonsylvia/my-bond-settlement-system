package com.settlement.service;

import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.AuditLogDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dto.HoldingResponse;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.*;
import com.settlement.exception.ResourceNotFoundException;
import com.settlement.jms.SwiftMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private final SwiftMessageSender messageSender;

    public SettlementService(SettlementInstructionDao instructionDao,
                             BondHoldingDao holdingDao,
                             AuditLogDao auditLogDao,
                             SwiftMessageBuilder messageBuilder,
                             SwiftMessageSender messageSender) {
        this.instructionDao = instructionDao;
        this.holdingDao = holdingDao;
        this.auditLogDao = auditLogDao;
        this.messageBuilder = messageBuilder;
        this.messageSender = messageSender;
    }

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

        instructionDao.save(instruction);

        String mt541Message = messageBuilder.buildMT541(instruction);
        instruction.setMt541Raw(mt541Message);
        instructionDao.save(instruction);

        messageSender.sendSwiftMessage(tradeRef, mt541Message);

        instruction.setStatus(InstructionStatus.SENT);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, "INSTRUCTION_SENT",
                "MT541 sent for ISIN=" + request.getIsin() + " QTY=" + request.getQuantity()));

        log.info("Settlement instruction submitted: tradeRef={}, ISIN={}, direction={}",
                tradeRef, request.getIsin(), request.getDirection());

        return instruction;
    }

    @Transactional(readOnly = true)
    public SettlementInstruction findByTradeRef(String tradeRef) {
        return instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement instruction not found: " + tradeRef));
    }

    @Transactional(readOnly = true)
    public List<SettlementInstruction> findAll(int page, int size) {
        return instructionDao.findAll(page, size);
    }

    @Transactional(readOnly = true)
    public long count() {
        return instructionDao.count();
    }

    @Transactional(readOnly = true)
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
