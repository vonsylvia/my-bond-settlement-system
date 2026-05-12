package com.settlement.reconcile;

import com.settlement.bridge.ReconciliationHandler;
import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.entity.*;
import com.settlement.service.AlertWebhookService;
import com.settlement.strategy.SwiftMessageStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final BondHoldingDao holdingDao;
    private final SecurityMovementDao movementDao;
    private final AuditLogDao auditLogDao;
    private final SwiftMessageDao swiftMessageDao;
    private final ReconciliationMetrics metrics;
    private final AlertWebhookService alertService;
    private final SwiftMessageStrategyFactory strategyFactory;

    public ReconciliationService(SettlementInstructionDao instructionDao,
                                 BondHoldingDao holdingDao,
                                 SecurityMovementDao movementDao,
                                 AuditLogDao auditLogDao,
                                 SwiftMessageDao swiftMessageDao,
                                 ReconciliationMetrics metrics,
                                 AlertWebhookService alertService,
                                 SwiftMessageStrategyFactory strategyFactory) {
        this.instructionDao = instructionDao;
        this.holdingDao = holdingDao;
        this.movementDao = movementDao;
        this.auditLogDao = auditLogDao;
        this.swiftMessageDao = swiftMessageDao;
        this.metrics = metrics;
        this.alertService = alertService;
        this.strategyFactory = strategyFactory;
    }

    @Transactional
    public void processSwiftReply(String correlationId, String rawMessage) {
        log.info("Processing SWIFT reply for correlationId={}", correlationId);

        SwiftMessageStrategy strategy = strategyFactory.detectStrategy(rawMessage);

        String tradeRef = strategy.extractTradeRef(rawMessage, correlationId);

        Optional<SettlementInstruction> optInstruction = instructionDao.findByTradeRef(tradeRef);
        if (optInstruction.isEmpty()) {
            log.warn("No matching instruction found for tradeRef={}", tradeRef);
            auditLogDao.save(new AuditLog(tradeRef, AuditEventType.RECONCILE_UNMATCHED,
                    "No instruction found for incoming " + strategy.getInboundStatusType()));
            metrics.recordUnmatched();
            return;
        }

        SettlementInstruction instruction = optInstruction.get();

        CanonicalStatusAdvice statusAdvice = strategy.parseStatusReply(rawMessage);

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
        instruction.setStatus(InstructionStatus.MATCHED);
        updateHoldings(instruction);
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.SETTLEMENT_MATCHED,
                "Settlement confirmed: ISIN=" + instruction.getIsin() +
                " QTY=" + instruction.getQuantity() +
                " DIR=" + instruction.getDirection()));
        log.info("Settlement MATCHED: tradeRef={}", instruction.getTradeRef());
        metrics.recordMatched();
    }

    private void handleFailed(SettlementInstruction instruction) {
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

    private void updateHoldings(SettlementInstruction instruction) {
        String accountId = instruction.getAccountId();
        String isin = instruction.getIsin();
        BigDecimal quantity = instruction.getQuantity();
        String tradeRef = instruction.getTradeRef();

        MovementType movementType = (instruction.getDirection() == Direction.BUY)
                ? MovementType.CREDIT
                : MovementType.DEBIT;

        Optional<BondHolding> optHolding = holdingDao.findByAccountAndIsinForUpdate(accountId, isin);

        BondHolding holding;
        BigDecimal newBalance;

        if (movementType == MovementType.CREDIT) {
            holding = optHolding.orElseGet(() -> newHolding(accountId, isin));
            newBalance = holding.getQuantity().add(quantity);
        } else {
            holding = optHolding.orElseThrow(() -> {
                log.error("No holding record for SELL: account={}, isin={}", accountId, isin);
                return new IllegalStateException(
                        "Cannot sell: no existing holding for account=" + accountId + ", isin=" + isin);
            });
            newBalance = holding.getQuantity().subtract(quantity);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                log.error("Insufficient holdings for SELL: account={}, isin={}, current={}, requested={}",
                        accountId, isin, holding.getQuantity(), quantity);
                throw new IllegalStateException("Insufficient bond holdings for settlement");
            }
        }

        holding.setQuantity(newBalance);
        holdingDao.save(holding);

        movementDao.save(new SecurityMovement(
                accountId, isin, movementType, quantity, newBalance, tradeRef));

        log.info("Position updated: account={}, isin={}, type={}, qty={}, newBalance={}",
                accountId, isin, movementType, quantity, newBalance);
    }

    private static BondHolding newHolding(String accountId, String isin) {
        BondHolding h = new BondHolding();
        h.setAccountId(accountId);
        h.setIsin(isin);
        h.setQuantity(BigDecimal.ZERO);
        return h;
    }
}
