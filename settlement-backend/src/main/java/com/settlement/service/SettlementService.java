package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.dto.HoldingResponse;
import com.settlement.dto.SettlementRequest;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.entity.*;
import com.settlement.exception.BusinessException;
import com.settlement.exception.ResourceNotFoundException;
import com.settlement.strategy.CanonicalMapper;
import com.settlement.strategy.SwiftMessageStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementInstructionDao instructionDao;
    private final BondHoldingDao holdingDao;
    private final AuditLogDao auditLogDao;
    private final SwiftMessageDao swiftMessageDao;
    private final SwiftMessageStrategyFactory strategyFactory;
    private final CanonicalMapper canonicalMapper;
    private final AsyncSettlementProcessor asyncProcessor;

    public SettlementService(SettlementInstructionDao instructionDao,
                             BondHoldingDao holdingDao,
                             AuditLogDao auditLogDao,
                             SwiftMessageDao swiftMessageDao,
                             SwiftMessageStrategyFactory strategyFactory,
                             CanonicalMapper canonicalMapper,
                             AsyncSettlementProcessor asyncProcessor) {
        this.instructionDao = instructionDao;
        this.holdingDao = holdingDao;
        this.auditLogDao = auditLogDao;
        this.swiftMessageDao = swiftMessageDao;
        this.strategyFactory = strategyFactory;
        this.canonicalMapper = canonicalMapper;
        this.asyncProcessor = asyncProcessor;
    }

    /**
     * Phase 1 (synchronous): validate, persist instruction with PENDING status,
     * build outbound SWIFT message (MT or MX), store in SWIFT_MESSAGE table,
     * then trigger async XA processing.
     */
    @Transactional
    public SettlementInstruction submitInstruction(SettlementRequest request) {
        String tradeRef = generateTradeRef();

        MessageStandard standard = request.getPreferredStandard() != null
                ? MessageStandard.valueOf(request.getPreferredStandard())
                : MessageStandard.MT;

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
        instruction.setPreferredStandard(standard);

        instructionDao.save(instruction);

        SwiftMessageStrategy strategy = strategyFactory.getStrategy(standard);
        CanonicalSettlement canonical = canonicalMapper.toCanonical(instruction);
        String rawMessage = strategy.buildSettlementInstruction(canonical);
        String messageType = strategy.getOutboundMessageType(canonical);

        SwiftMessage swiftMessage = new SwiftMessage(
                instruction.getId(), tradeRef, standard, messageType,
                MessageDirection.OUTBOUND, rawMessage);
        swiftMessageDao.save(swiftMessage);

        auditLogDao.save(new AuditLog(tradeRef, AuditEventType.INSTRUCTION_CREATED,
                "Instruction saved (" + standard + "/" + messageType + "), async XA processing queued. ISIN="
                        + request.getIsin() + " QTY=" + request.getQuantity()));

        log.info("Settlement instruction created: tradeRef={}, ISIN={}, direction={}, standard={} — async send queued",
                tradeRef, request.getIsin(), request.getDirection(), standard);

        scheduleAfterCommit(tradeRef);

        return instruction;
    }

    @Transactional
    public SettlementInstruction findByTradeRef(String tradeRef) {
        return instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement instruction not found: " + tradeRef));
    }

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

        auditLogDao.save(new AuditLog(tradeRef, AuditEventType.MANUAL_RETRY,
                "Manual retry triggered, status reset to PENDING"));

        log.info("Manual retry triggered: tradeRef={}", tradeRef);

        scheduleAfterCommit(tradeRef);

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

    private void scheduleAfterCommit(String tradeRef) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncProcessor.processSettlementAsync(tradeRef);
                }
            });
        } else {
            asyncProcessor.processSettlementAsync(tradeRef);
        }
    }

    private String generateTradeRef() {
        return "TR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
