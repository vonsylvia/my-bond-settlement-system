package com.settlement.strategy;

import com.prowidesoftware.swift.model.SwiftMessage;
import com.prowidesoftware.swift.model.SwiftTagListBlock;
import com.prowidesoftware.swift.model.Tag;
import com.prowidesoftware.swift.model.field.*;
import com.prowidesoftware.swift.model.mt.AbstractMT;
import com.prowidesoftware.swift.model.mt.mt5xx.MT540;
import com.prowidesoftware.swift.model.mt.mt5xx.MT541;
import com.prowidesoftware.swift.model.mt.mt5xx.MT542;
import com.prowidesoftware.swift.model.mt.mt5xx.MT543;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.canonical.CanonicalStatusAdvice.StatusOutcome;
import com.settlement.canonical.PartyInfo;
import com.settlement.canonical.PaymentType;
import com.settlement.canonical.SettlementDirection;
import com.settlement.entity.MessageStandard;
import com.settlement.swift.SwiftConst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
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
        AbstractMT mt = createSettlementMessage(settlement);

        // Basic header sender BIC: instructing party / account owner.
        mt.setSender(padBic(settlement.instructingParty().bic()));
        // Basic/application header receiver BIC: settlement counterparty.
        mt.setReceiver(padBic(settlement.counterparty().bic()));

        // :20C::SEME// - sender's settlement message reference / transaction id.
        mt.addField(new Field20C()
                .setQualifier(SwiftConst.SEME)
                .setReference(settlement.transactionId()));
        // :23G:NEWM - message function, a new settlement instruction.
        mt.addField(new Field23G(SwiftConst.NEWM));

        String settlementDateStr = settlement.settlementDate().format(SWIFT_DATE_FORMAT);
        // :98A::SETT// - requested settlement date in SWIFT yyyyMMdd format.
        mt.addField(new Field98A()
                .setQualifier(SwiftConst.SETT)
                .setDate(settlementDateStr));
        // :35B:ISIN - financial instrument identification.
        mt.addField(new Field35B("ISIN " + settlement.isin()));
        // :36B::SETT//FAMT/ - settlement quantity, expressed as face amount.
        mt.addField(new Field36B()
                .setQualifier(SwiftConst.SETT)
                .setQuantityTypeCode(SwiftConst.FAMT)
                .setQuantity(settlement.quantity().toPlainString()));

        // :95P::PSET// - place of settlement.
        mt.addField(new Field95P()
                .setQualifier(SwiftConst.PSET)
                .setIdentifierCode("HKMAHKHCXXX"));
        // :95P::DEAG/REAG// - counterparty settlement agent by direction.
        mt.addField(new Field95P()
                .setQualifier(settlement.direction() == SettlementDirection.RECEIVE
                        ? SwiftConst.DEAG : SwiftConst.REAG)
                .setIdentifierCode(padBic(settlement.counterparty().bic())));
        // :97A::SAFE// - safekeeping securities account.
        mt.addField(new Field97A()
                .setQualifier(SwiftConst.SAFE)
                .setAccountNumber(settlement.safekeepingAccount()));

        String message = mt.message();
        log.debug("Built MT541 for tradeRef={}: length={}", settlement.transactionId(), message.length());
        return message;
    }

    @Override
    public String getOutboundMessageType(CanonicalSettlement settlement) {
        if (settlement.direction() == SettlementDirection.RECEIVE) {
            return (settlement.paymentType() == PaymentType.FREE_OF_PAYMENT) ? "MT540" : "MT541";
        } else {
            return (settlement.paymentType() == PaymentType.FREE_OF_PAYMENT) ? "MT542" : "MT543";
        }
    }

    private AbstractMT createSettlementMessage(CanonicalSettlement settlement) {
        return switch (getOutboundMessageType(settlement)) {
            case "MT540" -> new MT540();
            case "MT541" -> new MT541();
            case "MT542" -> new MT542();
            case "MT543" -> new MT543();
            default -> throw new IllegalArgumentException("Unsupported MT settlement type");
        };
    }

    @Override
    public String getInboundStatusType() {
        return "MT548";
    }

    @Override
    public String buildStatusReply(CanonicalStatusAdvice statusAdvice) {
        String statusCode = resolveStatusCode(statusAdvice);
        String tradeRef = statusAdvice.transactionId() != null
                ? statusAdvice.transactionId() : "UNKNOWN";

        return "{1:F01TESTBIC0AXXX0000000000}" +
                "{2:O5481200000000TESTBIC0AXXX00000000000000000000N}" +
                "{4:\r\n" +
                ":16R:GENL\r\n" +
                ":20C::SEME//" + tradeRef + "\r\n" +
                ":23G:INST\r\n" +
                ":16S:GENL\r\n" +
                ":16R:STAT\r\n" +
                ":25D::MTCH//" + statusCode + "\r\n" +
                ":16S:STAT\r\n" +
                "-}";
    }

    private String resolveStatusCode(CanonicalStatusAdvice advice) {
        if (advice.statusCode() != null && !advice.statusCode().isBlank()) {
            return switch (advice.statusCode()) {
                case "MTCHD", "ACKD" -> SwiftConst.MATC;
                case "UMTCHD" -> SwiftConst.NMAT;
                case "RJCTD" -> SwiftConst.REJT;
                case "CANC" -> SwiftConst.CANC;
                case "PDNG" -> "PEND";
                default -> advice.statusCode();
            };
        }
        return switch (advice.outcome()) {
            case MATCHED -> SwiftConst.MATC;
            case FAILED -> SwiftConst.NMAT;
            case PENDING -> "PEND";
            case UNKNOWN -> "UNKN";
        };
    }

    @Override
    public CanonicalSettlement parseSettlementInstruction(String rawPayload) {
        String sanitised = sanitiseSwiftMessage(rawPayload);

        try {
            SwiftMessage swiftMsg = SwiftMessage.parse(sanitised);
            if (swiftMsg == null || swiftMsg.getBlock4() == null) {
                throw new IllegalArgumentException("Cannot parse MT message — null or missing Block4");
            }
            SwiftTagListBlock b4 = swiftMsg.getBlock4();

            String tradeRef = extractField20CReference(b4);
            if (tradeRef == null) {
                throw new IllegalArgumentException("Missing :20C::SEME trade reference");
            }

            LocalDate settlementDate = extractField98ADate(b4);
            String isin = extractIsin(b4);
            BigDecimal quantity = extractField36BQuantity(b4);

            String counterpartyBic = extractField95PBic(b4);
            if (counterpartyBic != null) {
                counterpartyBic = stripBicPadding(counterpartyBic);
            }

            String safekeepingAccount = extractField97AAccount(b4);

            String senderBic = (swiftMsg.getBlock1() != null)
                    ? stripBicPadding(swiftMsg.getBlock1().getLogicalTerminal())
                    : "UNKNOWN";

            SettlementDirection direction = SettlementDirection.RECEIVE;
            PaymentType paymentType = PaymentType.AGAINST_PAYMENT;

            String msgType = swiftMsg.getType();
            if (msgType != null) {
                switch (msgType) {
                    case "540" -> { direction = SettlementDirection.RECEIVE; paymentType = PaymentType.FREE_OF_PAYMENT; }
                    case "541" -> { direction = SettlementDirection.RECEIVE; paymentType = PaymentType.AGAINST_PAYMENT; }
                    case "542" -> { direction = SettlementDirection.DELIVER; paymentType = PaymentType.FREE_OF_PAYMENT; }
                    case "543" -> { direction = SettlementDirection.DELIVER; paymentType = PaymentType.AGAINST_PAYMENT; }
                    default -> {} // keep defaults
                }
            }

            return new CanonicalSettlement(
                    tradeRef, isin, settlementDate, quantity,
                    direction,
                    paymentType,
                    PartyInfo.ofBic(senderBic),
                    PartyInfo.ofBic(counterpartyBic != null ? counterpartyBic : "UNKNOWN"),
                    safekeepingAccount,
                    null, null, null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MT settlement instruction: " + e.getMessage(), e);
        }
    }

    private String extractField20CReference(SwiftTagListBlock b4) {
        Tag tag = b4.getTagByName(SwiftConst.TAG_20C);
        if (tag == null) return null;
        Field20C field = new Field20C(tag.getValue());
        String ref = field.getComponent(2);
        return (ref != null && !ref.isBlank()) ? ref : null;
    }

    private LocalDate extractField98ADate(SwiftTagListBlock b4) {
        Tag tag = b4.getTagByName(SwiftConst.TAG_98A);
        if (tag == null) return null;
        Field98A field = new Field98A(tag.getValue());
        String dateStr = field.getComponent(2);
        if (dateStr != null && !dateStr.isBlank()) {
            return LocalDate.parse(dateStr, SWIFT_DATE_FORMAT);
        }
        return null;
    }

    private BigDecimal extractField36BQuantity(SwiftTagListBlock b4) {
        Tag tag = b4.getTagByName(SwiftConst.TAG_36B);
        if (tag == null) return null;
        Field36B field = new Field36B(tag.getValue());
        String qty = field.getComponent(3);
        if (qty != null && !qty.isBlank()) {
            return new BigDecimal(qty.replace(",", "."));
        }
        return null;
    }

    private String extractField95PBic(SwiftTagListBlock b4) {
        Tag tag = b4.getTagByName(SwiftConst.TAG_95P);
        if (tag == null) return null;
        Field95P field = new Field95P(tag.getValue());
        String bic = field.getComponent(2);
        return (bic != null && !bic.isBlank()) ? bic : null;
    }

    private String extractField97AAccount(SwiftTagListBlock b4) {
        Tag tag = b4.getTagByName(SwiftConst.TAG_97A);
        if (tag == null) return null;
        Field97A field = new Field97A(tag.getValue());
        String account = field.getComponent(2);
        return (account != null && !account.isBlank()) ? account : null;
    }

    private String extractIsin(SwiftTagListBlock b4) {
        Tag tag35B = b4.getTagByName(SwiftConst.TAG_35B);
        if (tag35B == null) return null;
        String val = tag35B.getValue();
        if (val != null && val.toUpperCase().startsWith("ISIN ")) {
            String isin = val.substring(5).strip();
            int nlPos = isin.indexOf('\n');
            if (nlPos > 0) isin = isin.substring(0, nlPos).strip();
            int crPos = isin.indexOf('\r');
            if (crPos > 0) isin = isin.substring(0, crPos).strip();
            return isin;
        }
        return val;
    }

    private String stripBicPadding(String bic) {
        if (bic == null) return null;
        if (bic.length() == 11 && bic.endsWith("XXX")) {
            return bic.substring(0, 8);
        }
        return bic;
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
