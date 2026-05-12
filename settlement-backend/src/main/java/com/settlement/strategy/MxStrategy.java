package com.settlement.strategy;

import com.prowidesoftware.swift.model.mx.BusinessAppHdrV02;
import com.prowidesoftware.swift.model.mx.MxSese02300109;
import com.prowidesoftware.swift.model.mx.MxSese02400110;
import com.prowidesoftware.swift.model.mx.dic.*;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.canonical.SettlementDirection;
import com.settlement.entity.MessageStandard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.LocalDate;
import java.util.GregorianCalendar;

/**
 * ISO 20022 (MX) strategy for building sese.023.001.09 settlement instructions
 * and parsing sese.024.001.10 status replies using Prowide ISO 20022.
 *
 * <p>Works entirely with Canonical models — no dependency on JPA entities.
 */
@Component
public class MxStrategy implements SwiftMessageStrategy {

    private static final Logger log = LoggerFactory.getLogger(MxStrategy.class);

    private static final String SENDER_BIC = "OWNRBICXXXX";

    @Override
    public MessageStandard getStandard() {
        return MessageStandard.MX;
    }

    @Override
    public String buildSettlementInstruction(CanonicalSettlement settlement) {
        MxSese02300109 mx = new MxSese02300109();

        mx.setAppHdr(buildAppHeader(settlement));

        ReceiveDelivery1Code movementType = (settlement.direction() == SettlementDirection.RECEIVE)
                ? ReceiveDelivery1Code.RECE
                : ReceiveDelivery1Code.DELI;

        mx.setSctiesSttlmTxInstr(
                new SecuritiesSettlementTransactionInstructionV09()
                        .setTxId(settlement.transactionId())
                        .setSttlmTpAndAddtlParams(
                                new SettlementTypeAndAdditionalParameters19()
                                        .setSctiesMvmntTp(movementType)
                                        .setPmt(DeliveryReceiptType2Code.APMT)
                        )
                        .setTradDtls(
                                new SecuritiesTradeDetails97()
                                        .setSttlmDt(new SettlementDate17Choice()
                                                .setDt(new DateAndDateTime2Choice()
                                                        .setDt(toXmlDate(settlement.settlementDate()))))
                        )
                        .setFinInstrmId(
                                new SecurityIdentification19()
                                        .setISIN(settlement.isin())
                        )
                        .setQtyAndAcctDtls(
                                new QuantityAndAccount79()
                                        .setSttlmQty(new Quantity6Choice()
                                                .setQty(new FinancialInstrumentQuantity1Choice()
                                                        .setUnit(settlement.quantity())))
                                        .setSfkpgAcct(new SecuritiesAccount19()
                                                .setId(settlement.safekeepingAccount()))
                        )
                        .setDlvrgSttlmPties(
                                new SettlementParties76()
                                        .setPty1(new PartyIdentificationAndAccount168()
                                                .setId(new PartyIdentification120Choice()
                                                        .setAnyBIC(padBic(settlement.counterparty().bic()))))
                        )
        );

        String message = mx.message();
        log.debug("Built sese.023.001.09 for tradeRef={}: length={}",
                settlement.transactionId(), message.length());
        return message;
    }

    @Override
    public String getOutboundMessageType(CanonicalSettlement settlement) {
        return "sese.023.001.09";
    }

    @Override
    public String getInboundStatusType() {
        return "sese.024.001.10";
    }

    @Override
    public CanonicalStatusAdvice parseStatusReply(String rawPayload) {
        try {
            MxSese02400110 mx = MxSese02400110.parse(rawPayload);
            if (mx == null || mx.getSctiesSttlmTxStsAdvc() == null) {
                log.warn("Failed to parse sese.024 — null result");
                return CanonicalStatusAdvice.unknown(
                        extractTradeRef(rawPayload, null),
                        "Parse returned null");
            }

            SecuritiesSettlementTransactionStatusAdviceV10 advice = mx.getSctiesSttlmTxStsAdvc();

            String tradeRef = null;
            if (advice.getTxId() != null) {
                tradeRef = advice.getTxId().getAcctOwnrTxId();
            }

            return resolveOutcome(advice, tradeRef);
        } catch (Exception e) {
            log.error("Error parsing sese.024.001.10", e);
            return CanonicalStatusAdvice.unknown(
                    extractTradeRef(rawPayload, null),
                    e.getMessage());
        }
    }

    @Override
    public String extractTradeRef(String rawPayload, String fallbackCorrelationId) {
        if (rawPayload == null) return fallbackCorrelationId;

        try {
            MxSese02400110 mx = MxSese02400110.parse(rawPayload);
            if (mx != null && mx.getSctiesSttlmTxStsAdvc() != null
                    && mx.getSctiesSttlmTxStsAdvc().getTxId() != null) {
                String ref = mx.getSctiesSttlmTxStsAdvc().getTxId().getAcctOwnrTxId();
                if (ref != null && !ref.isBlank()) return ref;
            }
        } catch (Exception e) {
            log.debug("Prowide MX parsing failed for trade ref extraction", e);
        }

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("<AcctOwnrTxId>([^<]+)</AcctOwnrTxId>")
                .matcher(rawPayload);
        if (m.find()) {
            return m.group(1).strip();
        }

        return fallbackCorrelationId;
    }

    private CanonicalStatusAdvice resolveOutcome(
            SecuritiesSettlementTransactionStatusAdviceV10 advice, String tradeRef) {

        MatchingStatus24Choice matchingStatus = advice.getMtchgSts();
        if (matchingStatus != null) {
            if (matchingStatus.getMtchd() != null) {
                return CanonicalStatusAdvice.matched(tradeRef, "MTCHD");
            }
            if (matchingStatus.getUmtchd() != null) {
                return CanonicalStatusAdvice.failed(tradeRef, "UMTCHD", "Unmatched");
            }
        }

        ProcessingStatus74Choice prcgSts = advice.getPrcgSts();
        if (prcgSts != null) {
            if (prcgSts.getAckdAccptd() != null) {
                return CanonicalStatusAdvice.matched(tradeRef, "ACKD");
            }
            if (prcgSts.getRjctd() != null) {
                return CanonicalStatusAdvice.failed(tradeRef, "RJCTD", "Rejected");
            }
            if (prcgSts.getCanc() != null) {
                return CanonicalStatusAdvice.failed(tradeRef, "CANC", "Cancelled");
            }
            if (prcgSts.getPdgPrcg() != null || prcgSts.getPdgCxl() != null) {
                return CanonicalStatusAdvice.pending(tradeRef, "PDNG");
            }
        }

        SettlementStatus17Choice sttlmSts = advice.getSttlmSts();
        if (sttlmSts != null) {
            if (sttlmSts.getPdg() != null) {
                return CanonicalStatusAdvice.pending(tradeRef, "PDNG");
            }
            if (sttlmSts.getFlng() != null) {
                return CanonicalStatusAdvice.failed(tradeRef, "FLNG", "Failing");
            }
        }

        return CanonicalStatusAdvice.unknown(tradeRef, "No recognisable status element found");
    }

    private BusinessAppHdrV02 buildAppHeader(CanonicalSettlement settlement) {
        BusinessAppHdrV02 hdr = new BusinessAppHdrV02();
        hdr.setFr(buildParty(SENDER_BIC));
        hdr.setTo(buildParty(padBic(settlement.counterparty().bic())));
        hdr.setBizMsgIdr(settlement.transactionId());
        hdr.setMsgDefIdr("sese.023.001.09");
        hdr.setCreDt(toXmlDate(LocalDate.now()));
        return hdr;
    }

    private Party44Choice buildParty(String bic) {
        return new Party44Choice()
                .setFIId(new BranchAndFinancialInstitutionIdentification6()
                        .setFinInstnId(new FinancialInstitutionIdentification18()
                                .setBICFI(bic)));
    }

    private XMLGregorianCalendar toXmlDate(LocalDate date) {
        try {
            GregorianCalendar cal = new GregorianCalendar(
                    date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
            return DatatypeFactory.newInstance().newXMLGregorianCalendar(cal);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to convert date: " + date, e);
        }
    }

    private String padBic(String bic) {
        if (bic.length() == 8) {
            return bic + "XXXX";
        }
        return bic;
    }
}
