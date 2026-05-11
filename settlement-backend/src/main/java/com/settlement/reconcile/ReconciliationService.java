package com.settlement.reconcile;

import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.Field20C;
import com.prowidesoftware.swift.model.field.Field25D;
import com.settlement.bridge.ReconciliationHandler;
import com.settlement.swift.SwiftConst;
import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.*;
import com.settlement.service.AlertWebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Processes SWIFT MT548 (Settlement Status and Processing Advice) replies,
 * reconciles against original instructions, and updates bond holdings.
 */
@Service
public class ReconciliationService implements ReconciliationHandler {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final SettlementInstructionDao instructionDao;
    private final BondHoldingDao holdingDao;
    private final AuditLogDao auditLogDao;
    private final ReconciliationMetrics metrics;
    private final AlertWebhookService alertService;

    public ReconciliationService(SettlementInstructionDao instructionDao,
                                 BondHoldingDao holdingDao,
                                 AuditLogDao auditLogDao,
                                 ReconciliationMetrics metrics,
                                 AlertWebhookService alertService) {
        this.instructionDao = instructionDao;
        this.holdingDao = holdingDao;
        this.auditLogDao = auditLogDao;
        this.metrics = metrics;
        this.alertService = alertService;
    }

    @Transactional
    public void processSwiftReply(String correlationId, String mt548RawMessage) {
        log.info("Processing SWIFT reply for correlationId={}", correlationId);

        String sanitised = sanitiseSwiftMessage(mt548RawMessage);

        String tradeRef = extractTradeRef(sanitised, correlationId);

        Optional<SettlementInstruction> optInstruction = instructionDao.findByTradeRef(tradeRef);
        if (optInstruction.isEmpty()) {
            log.warn("No matching instruction found for tradeRef={}", tradeRef);
            auditLogDao.save(new AuditLog(tradeRef, AuditEventType.RECONCILE_UNMATCHED,
                    "No instruction found for incoming MT548"));
            metrics.recordUnmatched();
            return;
        }

        SettlementInstruction instruction = optInstruction.get();
        instruction.setMt548Raw(mt548RawMessage);

        SettlementStatus status = extractSettlementStatus(sanitised);

        switch (status) {
            case MATCHED -> handleMatched(instruction);
            case FAILED -> handleFailed(instruction);
            case PENDING -> handlePending(instruction);
            case UNKNOWN -> handleUnknownStatus(instruction, sanitised);
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
                "Unable to parse settlement status from MT548 for ISIN=" + instruction.getIsin()
                        + ", requires manual review"));
        log.error("Settlement status UNKNOWN (unparseable MT548): tradeRef={}, rawLength={}",
                instruction.getTradeRef(),
                rawMessage != null ? rawMessage.length() : 0);
        metrics.recordUnknown();
        alertService.sendUnknownStatusAlert(instruction.getTradeRef(), instruction.getIsin());
    }

    private void updateHoldings(SettlementInstruction instruction) {
        String accountId = instruction.getAccountId();
        String isin = instruction.getIsin();
        BigDecimal quantity = instruction.getQuantity();

        Optional<BondHolding> optHolding = holdingDao.findByAccountAndIsin(accountId, isin);

        BondHolding holding;
        if (instruction.getDirection() == Direction.BUY) {
            holding = optHolding.orElseGet(() -> newHolding(accountId, isin));
            holding.setQuantity(holding.getQuantity().add(quantity));
        } else {
            holding = optHolding.orElseThrow(() -> {
                log.error("No holding record for SELL: account={}, isin={}", accountId, isin);
                return new IllegalStateException(
                        "Cannot sell: no existing holding for account=" + accountId + ", isin=" + isin);
            });
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

    private static BondHolding newHolding(String accountId, String isin) {
        BondHolding h = new BondHolding();
        h.setAccountId(accountId);
        h.setIsin(isin);
        h.setQuantity(BigDecimal.ZERO);
        return h;
    }

    /**
     * Normalises a raw SWIFT FIN message so that minor transport-level
     * corruptions (wrong line endings, stray NUL bytes, BOM, etc.) do not
     * cause Prowide or regex-based parsing to fail.
     *
     * <p>The original (un-sanitised) message is still persisted on the
     * instruction for audit purposes; only the sanitised copy is used for
     * field extraction.</p>
     */
    static String sanitiseSwiftMessage(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        String s = raw;

        if (s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }

        // Remove NUL bytes (common in fixed-length MQ messages)
        s = s.replace("\0", "");

        // Normalise line endings to SWIFT FIN standard (\r\n)
        s = s.replace("\r\n", "\n")   // collapse existing \r\n first
             .replace("\r", "\n")     // handle bare \r
             .replace("\n", "\r\n");  // then convert all to \r\n

        // Trim trailing whitespace from each line (some gateways pad with spaces)
        s = TRAILING_WS.matcher(s).replaceAll("$1");

        return s.strip();
    }

    private static final Pattern TRAILING_WS = Pattern.compile("[ \\t]+(\\r?\\n)");

    private String extractTradeRef(String mt548Raw, String fallbackCorrelationId) {
        try {
            SwiftMessage swiftMsg = SwiftMessage.parse(mt548Raw);
            if (swiftMsg != null && swiftMsg.getBlock4() != null) {
                SwiftTagListBlock block4 = swiftMsg.getBlock4();
                Tag tag20C = block4.getTagByName(SwiftConst.TAG_20C);
                if (tag20C != null) {
                    Field20C field = new Field20C(tag20C.getValue());
                    String value = field.getComponent(2);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Prowide parsing failed, trying raw text extraction", e);
        }
        String rawRef = extractTradeRefFromRawText(mt548Raw);
        if (rawRef != null) {
            return rawRef;
        }
        return fallbackCorrelationId;
    }

    private String extractTradeRefFromRawText(String rawMessage) {
        if (rawMessage == null) return null;
        Matcher m = Pattern
                .compile(":" + SwiftConst.TAG_20C + "::" + SwiftConst.SEME + "//([A-Za-z0-9\\-]+)")
                .matcher(rawMessage);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private SettlementStatus extractSettlementStatus(String mt548Raw) {
        try {
            SwiftMessage swiftMsg = SwiftMessage.parse(mt548Raw);
            if (swiftMsg != null && swiftMsg.getBlock4() != null) {
                SwiftTagListBlock block4 = swiftMsg.getBlock4();
                Tag tag25D = block4.getTagByName(SwiftConst.TAG_25D);
                if (tag25D != null) {
                    Field25D field = new Field25D(tag25D.getValue());
                    String statusCode = field.getComponent(2);
                    if (statusCode != null) {
                        return mapStatusCode(statusCode);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Prowide parsing failed for MT548 status extraction, falling back to raw text", e);
        }
        return extractStatusFromRawText(mt548Raw);
    }

    private static final Pattern STATUS_PATTERN = Pattern.compile(
            ":" + SwiftConst.TAG_25D + "::" + SwiftConst.MTCH + "//([A-Z]{4})");

    private SettlementStatus extractStatusFromRawText(String rawMessage) {
        if (rawMessage == null) {
            log.warn("Cannot extract status from null MT548 message");
            return SettlementStatus.UNKNOWN;
        }
        Matcher m = STATUS_PATTERN.matcher(rawMessage.toUpperCase());
        if (m.find()) {
            return mapStatusCode(m.group(1));
        }
        log.warn("Unable to extract settlement status from raw MT548 — no :25D::MTCH// pattern found");
        return SettlementStatus.UNKNOWN;
    }

    private SettlementStatus mapStatusCode(String statusCode) {
        return switch (statusCode.toUpperCase()) {
            case SwiftConst.MACH, SwiftConst.MATC -> SettlementStatus.MATCHED;
            case SwiftConst.NMAT, SwiftConst.REJT, SwiftConst.CANC -> SettlementStatus.FAILED;
            default -> SettlementStatus.PENDING;
        };
    }

    private enum SettlementStatus {
        MATCHED,
        FAILED,
        PENDING,
        UNKNOWN
    }
}
