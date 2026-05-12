package com.settlement.strategy;

import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.*;
import com.prowidesoftware.swift.model.mt.mt5xx.MT541;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.canonical.CanonicalStatusAdvice.StatusOutcome;
import com.settlement.entity.MessageStandard;
import com.settlement.swift.SwiftConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SWIFT FIN (MT) strategy for building MT541 settlement instructions
 * and parsing MT548 status replies using Prowide Core.
 */
@Component
public class MtStrategy implements SwiftMessageStrategy {

    private static final Logger log = LoggerFactory.getLogger(MtStrategy.class);
    private static final DateTimeFormatter SWIFT_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Pattern TRAILING_WS = Pattern.compile("[ \\t]+(\\r?\\n)");
    private static final Pattern STATUS_PATTERN = Pattern.compile(
            ":" + SwiftConst.TAG_25D + "::" + SwiftConst.MTCH + "//([A-Z]{4})");
    private static final Pattern TRADE_REF_PATTERN = Pattern.compile(
            ":" + SwiftConst.TAG_20C + "::" + SwiftConst.SEME + "//([A-Za-z0-9\\-]+)");

    @Override
    public MessageStandard getStandard() {
        return MessageStandard.MT;
    }

    @Override
    public String buildSettlementInstruction(CanonicalSettlement settlement) {
        MT541 mt = new MT541();

        mt.setSender(padBic(settlement.instructingParty().bic()));
        mt.setReceiver(padBic(settlement.counterparty().bic()));

        mt.addField(new Field20C()
                .setQualifier(SwiftConst.SEME)
                .setReference(settlement.transactionId()));
        mt.addField(new Field23G(SwiftConst.NEWM));

        String settlementDateStr = settlement.settlementDate().format(SWIFT_DATE_FORMAT);
        mt.addField(new Field98A()
                .setQualifier(SwiftConst.SETT)
                .setDate(settlementDateStr));
        mt.addField(new Field35B("ISIN " + settlement.isin()));
        mt.addField(new Field36B()
                .setQualifier(SwiftConst.SETT)
                .setQuantityTypeCode(SwiftConst.UNIT)
                .setQuantity(settlement.quantity().toPlainString()));

        mt.addField(new Field95P()
                .setQualifier(SwiftConst.DEAG)
                .setIdentifierCode(padBic(settlement.counterparty().bic())));
        mt.addField(new Field97A()
                .setQualifier(SwiftConst.SAFE)
                .setAccountNumber(settlement.safekeepingAccount()));

        String message = mt.message();
        log.debug("Built MT541 for tradeRef={}: length={}", settlement.transactionId(), message.length());
        return message;
    }

    @Override
    public String getOutboundMessageType(CanonicalSettlement settlement) {
        return "MT541";
    }

    @Override
    public String getInboundStatusType() {
        return "MT548";
    }

    @Override
    public CanonicalStatusAdvice parseStatusReply(String rawPayload) {
        String sanitised = sanitiseSwiftMessage(rawPayload);
        String tradeRef = extractTradeRefInternal(sanitised);
        String statusCode = extractRawStatusCode(sanitised);
        StatusOutcome outcome = extractOutcome(sanitised);

        return switch (outcome) {
            case MATCHED -> CanonicalStatusAdvice.matched(tradeRef, statusCode);
            case FAILED -> CanonicalStatusAdvice.failed(tradeRef, statusCode, null);
            case PENDING -> CanonicalStatusAdvice.pending(tradeRef, statusCode);
            case UNKNOWN -> CanonicalStatusAdvice.unknown(tradeRef, "Unable to parse MT548 status");
        };
    }

    @Override
    public String extractTradeRef(String rawPayload) {
        String sanitised = sanitiseSwiftMessage(rawPayload);
        return extractTradeRefInternal(sanitised);
    }

    private String extractTradeRefInternal(String sanitised) {
        try {
            SwiftMessage swiftMsg = SwiftMessage.parse(sanitised);
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
        return extractTradeRefFromRawText(sanitised);
    }

    private String extractTradeRefFromRawText(String rawMessage) {
        if (rawMessage == null) return null;
        Matcher m = TRADE_REF_PATTERN.matcher(rawMessage);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private StatusOutcome extractOutcome(String sanitised) {
        try {
            SwiftMessage swiftMsg = SwiftMessage.parse(sanitised);
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
        return extractOutcomeFromRawText(sanitised);
    }

    private StatusOutcome extractOutcomeFromRawText(String rawMessage) {
        if (rawMessage == null) {
            log.warn("Cannot extract status from null MT548 message");
            return StatusOutcome.UNKNOWN;
        }
        Matcher m = STATUS_PATTERN.matcher(rawMessage.toUpperCase());
        if (m.find()) {
            return mapStatusCode(m.group(1));
        }
        log.warn("Unable to extract settlement status from raw MT548 — no :25D::MTCH// pattern found");
        return StatusOutcome.UNKNOWN;
    }

    private String extractRawStatusCode(String sanitised) {
        if (sanitised == null) return null;
        Matcher m = STATUS_PATTERN.matcher(sanitised.toUpperCase());
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private StatusOutcome mapStatusCode(String statusCode) {
        return switch (statusCode.toUpperCase()) {
            case SwiftConst.MACH, SwiftConst.MATC -> StatusOutcome.MATCHED;
            case SwiftConst.NMAT, SwiftConst.REJT, SwiftConst.CANC -> StatusOutcome.FAILED;
            default -> StatusOutcome.PENDING;
        };
    }

    /**
     * Normalises raw SWIFT FIN for reliable parsing. The original
     * (un-sanitised) message is preserved in SWIFT_MESSAGE.RAW_PAYLOAD.
     */
    static String sanitiseSwiftMessage(String raw) {
        if (raw == null || raw.isEmpty()) return raw;

        String s = raw;

        if (s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }

        s = s.replace("\0", "");

        s = s.replace("\r\n", "\n")
             .replace("\r", "\n")
             .replace("\n", "\r\n");

        s = TRAILING_WS.matcher(s).replaceAll("$1");

        return s.strip();
    }

    private String padBic(String bic) {
        if (bic.length() == 8) {
            return bic + "XXX";
        }
        return bic;
    }
}
