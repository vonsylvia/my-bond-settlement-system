package com.settlement.reconcile;

import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.Field20C;
import com.prowidesoftware.swift.model.field.Field25D;
import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Processes SWIFT MT548 (Settlement Status and Processing Advice) replies,
 * reconciles against original instructions, and updates bond holdings.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final SettlementInstructionDao instructionDao;
    private final BondHoldingDao holdingDao;
    private final AuditLogDao auditLogDao;

    public ReconciliationService(SettlementInstructionDao instructionDao,
                                 BondHoldingDao holdingDao,
                                 AuditLogDao auditLogDao) {
        this.instructionDao = instructionDao;
        this.holdingDao = holdingDao;
        this.auditLogDao = auditLogDao;
    }

    @Transactional
    public void processSwiftReply(String correlationId, String mt548RawMessage) {
        log.info("Processing SWIFT reply for correlationId={}", correlationId);

        // Parse the trade reference from the MT548 or fall back to correlationId
        String tradeRef = extractTradeRef(mt548RawMessage, correlationId);

        Optional<SettlementInstruction> optInstruction = instructionDao.findByTradeRef(tradeRef);
        if (optInstruction.isEmpty()) {
            log.warn("No matching instruction found for tradeRef={}", tradeRef);
            auditLogDao.save(new AuditLog(tradeRef, "RECONCILE_UNMATCHED",
                    "No instruction found for incoming MT548"));
            return;
        }

        SettlementInstruction instruction = optInstruction.get();
        instruction.setMt548Raw(mt548RawMessage);

        // Extract settlement status from MT548
        SettlementStatus status = extractSettlementStatus(mt548RawMessage);

        switch (status) {
            case MATCHED -> handleMatched(instruction);
            case FAILED -> handleFailed(instruction);
            case PENDING -> handlePending(instruction);
        }

        instructionDao.save(instruction);
    }

    private void handleMatched(SettlementInstruction instruction) {
        instruction.setStatus(InstructionStatus.MATCHED);
        updateHoldings(instruction);
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), "SETTLEMENT_MATCHED",
                "Settlement confirmed: ISIN=" + instruction.getIsin() +
                " QTY=" + instruction.getQuantity() +
                " DIR=" + instruction.getDirection()));
        log.info("Settlement MATCHED: tradeRef={}", instruction.getTradeRef());
    }

    private void handleFailed(SettlementInstruction instruction) {
        instruction.setStatus(InstructionStatus.FAILED);
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), "SETTLEMENT_FAILED",
                "Settlement failed for ISIN=" + instruction.getIsin()));
        log.warn("Settlement FAILED: tradeRef={}", instruction.getTradeRef());
    }

    private void handlePending(SettlementInstruction instruction) {
        auditLogDao.save(new AuditLog(instruction.getTradeRef(), "SETTLEMENT_PENDING",
                "Settlement still pending for ISIN=" + instruction.getIsin()));
        log.info("Settlement still PENDING: tradeRef={}", instruction.getTradeRef());
    }

    private void updateHoldings(SettlementInstruction instruction) {
        String accountId = instruction.getAccountId();
        String isin = instruction.getIsin();
        BigDecimal quantity = instruction.getQuantity();

        Optional<BondHolding> optHolding = holdingDao.findByAccountAndIsin(accountId, isin);

        BondHolding holding;
        if (optHolding.isPresent()) {
            holding = optHolding.get();
        } else {
            holding = new BondHolding();
            holding.setAccountId(accountId);
            holding.setIsin(isin);
            holding.setQuantity(BigDecimal.ZERO);
        }

        if (instruction.getDirection() == Direction.BUY) {
            holding.setQuantity(holding.getQuantity().add(quantity));
        } else {
            BigDecimal newQty = holding.getQuantity().subtract(quantity);
            if (newQty.compareTo(BigDecimal.ZERO) < 0) {
                log.error("Insufficient holdings for SELL: account={}, isin={}, current={}, requested={}",
                        accountId, isin, holding.getQuantity(), quantity);
                throw new IllegalStateException("Insufficient bond holdings for settlement");
            }
            holding.setQuantity(newQty);
        }

        holdingDao.save(holding);
        log.info("Holdings updated: account={}, isin={}, newQty={}", accountId, isin, holding.getQuantity());
    }

    private String extractTradeRef(String mt548Raw, String fallbackCorrelationId) {
        try {
            SwiftMessage swiftMsg = SwiftMessage.parse(mt548Raw);
            if (swiftMsg != null && swiftMsg.getBlock4() != null) {
                SwiftTagListBlock block4 = swiftMsg.getBlock4();
                Tag tag20C = block4.getTagByName("20C");
                if (tag20C != null) {
                    Field20C field = new Field20C(tag20C.getValue());
                    String value = field.getComponent(2);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse trade ref from MT548, using correlationId", e);
        }
        return fallbackCorrelationId;
    }

    private SettlementStatus extractSettlementStatus(String mt548Raw) {
        try {
            SwiftMessage swiftMsg = SwiftMessage.parse(mt548Raw);
            if (swiftMsg != null && swiftMsg.getBlock4() != null) {
                SwiftTagListBlock block4 = swiftMsg.getBlock4();
                Tag tag25D = block4.getTagByName("25D");
                if (tag25D != null) {
                    Field25D field = new Field25D(tag25D.getValue());
                    String statusCode = field.getComponent(2);
                    if (statusCode != null) {
                        return mapStatusCode(statusCode);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Prowide parsing failed, trying text-based extraction", e);
        }
        // Fallback: raw text pattern matching for :25D:: field
        return extractStatusFromRawText(mt548Raw);
    }

    private SettlementStatus extractStatusFromRawText(String rawMessage) {
        if (rawMessage == null) {
            return SettlementStatus.PENDING;
        }
        String upper = rawMessage.toUpperCase();
        if (upper.contains("//MATC") || upper.contains("//MACH")) {
            return SettlementStatus.MATCHED;
        } else if (upper.contains("//REJT") || upper.contains("//NMAT") || upper.contains("//CANC")) {
            return SettlementStatus.FAILED;
        }
        return SettlementStatus.PENDING;
    }

    private SettlementStatus mapStatusCode(String statusCode) {
        return switch (statusCode.toUpperCase()) {
            case "MACH", "MATC" -> SettlementStatus.MATCHED;
            case "NMAT", "REJT", "CANC" -> SettlementStatus.FAILED;
            default -> SettlementStatus.PENDING;
        };
    }

    private enum SettlementStatus {
        MATCHED,
        FAILED,
        PENDING
    }
}
