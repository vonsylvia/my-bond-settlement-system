package com.settlement.strategy;

import com.prowidesoftware.swift.model.mx.BusinessAppHdrV02;
import com.prowidesoftware.swift.model.mx.MxSese02300109;
import com.prowidesoftware.swift.model.mx.MxSese02400110;
import com.prowidesoftware.swift.model.mx.dic.*;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.canonical.PartyInfo;
import com.settlement.canonical.PaymentType;
import com.settlement.canonical.SettlementDirection;
import com.settlement.entity.MessageStandard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;
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

        // Business application header: ISO 20022 sender/receiver envelope metadata.
        mx.setAppHdr(buildAppHeader(settlement));

        // SctiesMvmntTp: securities movement direction, receive or deliver.
        ReceiveDelivery1Code movementType = (settlement.direction() == SettlementDirection.RECEIVE)
                ? ReceiveDelivery1Code.RECE
                : ReceiveDelivery1Code.DELI;
        // Pmt: settlement payment condition, free of payment or against payment.
        DeliveryReceiptType2Code paymentType = (settlement.paymentType() == PaymentType.FREE_OF_PAYMENT)
                ? DeliveryReceiptType2Code.FREE
                : DeliveryReceiptType2Code.APMT;

        mx.setSctiesSttlmTxInstr(
                new SecuritiesSettlementTransactionInstructionV09()
                        // TxId: account owner's transaction id / trade reference.
                        .setTxId(settlement.transactionId())
                        // SttlmTpAndAddtlParams: settlement movement and payment attributes.
                        .setSttlmTpAndAddtlParams(
                                new SettlementTypeAndAdditionalParameters19()
                                        // SctiesMvmntTp: RECE for receive, DELI for deliver.
                                        .setSctiesMvmntTp(movementType)
                                        // Pmt: FREE for free of payment, APMT for against payment.
                                        .setPmt(paymentType)
                        )
                        // TradDtls/SttlmDt/Dt: requested settlement date.
                        .setTradDtls(
                                new SecuritiesTradeDetails97()
                                        .setSttlmDt(new SettlementDate17Choice()
                                                .setDt(new DateAndDateTime2Choice()
                                                        .setDt(toXmlDate(settlement.settlementDate()))))
                        )
                        // FinInstrmId/ISIN: financial instrument identification.
                        .setFinInstrmId(
                                new SecurityIdentification19()
                                        .setISIN(settlement.isin())
                        )
                        // QtyAndAcctDtls: settlement quantity and safekeeping account.
                        .setQtyAndAcctDtls(
                                new QuantityAndAccount79()
                                        // SttlmQty/Qty/Unit: settlement quantity in units/face amount.
                                        .setSttlmQty(new Quantity6Choice()
                                                .setQty(new FinancialInstrumentQuantity1Choice()
                                                        .setUnit(settlement.quantity())))
                                        // SfkpgAcct/Id: safekeeping securities account.
                                        .setSfkpgAcct(new SecuritiesAccount19()
                                                .setId(settlement.safekeepingAccount()))
                        )
                        // DlvrgSttlmPties/Pty1/AnyBIC: counterparty settlement party BIC.
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
    public String buildStatusReply(CanonicalStatusAdvice statusAdvice) {
        MxSese02400110 mx = new MxSese02400110();
        SecuritiesSettlementTransactionStatusAdviceV10 advice =
                new SecuritiesSettlementTransactionStatusAdviceV10();

        String tradeRef = statusAdvice.transactionId() != null
                ? statusAdvice.transactionId() : "UNKNOWN";
        advice.setTxId(new TransactionIdentifications31()
                .setAcctOwnrTxId(tradeRef));

        switch (statusAdvice.outcome()) {
            case MATCHED -> advice.setMtchgSts(
                    new MatchingStatus24Choice().setMtchd(new ProprietaryReason4()));
            case FAILED -> {
                if ("UMTCHD".equals(statusAdvice.statusCode()) || "NMAT".equals(statusAdvice.statusCode())) {
                    advice.setMtchgSts(
                            new MatchingStatus24Choice().setUmtchd(new UnmatchedStatus16Choice()));
                } else if ("CANC".equals(statusAdvice.statusCode())) {
                    advice.setPrcgSts(
                            new ProcessingStatus74Choice().setCanc(new CancellationStatus24Choice()));
                } else {
                    advice.setPrcgSts(
                            new ProcessingStatus74Choice().setRjctd(new RejectionStatus21Choice()));
                }
            }
            case PENDING, UNKNOWN -> advice.setPrcgSts(
                    new ProcessingStatus74Choice().setPdgPrcg(new PendingProcessingStatus11Choice()));
        }

        mx.setSctiesSttlmTxStsAdvc(advice);
        return mx.message();
    }

    @Override
    public CanonicalSettlement parseSettlementInstruction(String rawPayload) {
        try {
            MxSese02300109 mx = MxSese02300109.parse(rawPayload);
            if (mx == null || mx.getSctiesSttlmTxInstr() == null) {
                throw new IllegalArgumentException("Cannot parse sese.023 — null or missing instruction body");
            }

            SecuritiesSettlementTransactionInstructionV09 instr = mx.getSctiesSttlmTxInstr();

            String transactionId = instr.getTxId();

            SettlementDirection direction = SettlementDirection.RECEIVE;
            if (instr.getSttlmTpAndAddtlParams() != null
                    && instr.getSttlmTpAndAddtlParams().getSctiesMvmntTp() != null) {
                direction = (instr.getSttlmTpAndAddtlParams().getSctiesMvmntTp() == ReceiveDelivery1Code.DELI)
                        ? SettlementDirection.DELIVER
                        : SettlementDirection.RECEIVE;
            }

            PaymentType paymentType = PaymentType.AGAINST_PAYMENT;
            if (instr.getSttlmTpAndAddtlParams() != null
                    && instr.getSttlmTpAndAddtlParams().getPmt() == DeliveryReceiptType2Code.FREE) {
                paymentType = PaymentType.FREE_OF_PAYMENT;
            }

            String isin = null;
            if (instr.getFinInstrmId() != null) {
                isin = instr.getFinInstrmId().getISIN();
            }

            LocalDate settlementDate = null;
            if (instr.getTradDtls() != null && instr.getTradDtls().getSttlmDt() != null) {
                DateAndDateTime2Choice dtChoice = instr.getTradDtls().getSttlmDt().getDt();
                if (dtChoice != null && dtChoice.getDt() != null) {
                    XMLGregorianCalendar xmlCal = dtChoice.getDt();
                    settlementDate = LocalDate.of(xmlCal.getYear(), xmlCal.getMonth(), xmlCal.getDay());
                }
            }

            BigDecimal quantity = null;
            String safekeepingAccount = null;
            if (instr.getQtyAndAcctDtls() != null) {
                QuantityAndAccount79 qtyAcct = instr.getQtyAndAcctDtls();
                if (qtyAcct.getSttlmQty() != null && qtyAcct.getSttlmQty().getQty() != null
                        && qtyAcct.getSttlmQty().getQty().getUnit() != null) {
                    quantity = qtyAcct.getSttlmQty().getQty().getUnit();
                }
                if (qtyAcct.getSfkpgAcct() != null) {
                    safekeepingAccount = qtyAcct.getSfkpgAcct().getId();
                }
            }

            String counterpartyBic = extractCounterpartyBic(instr);

            String senderBic = SENDER_BIC;
            if (mx.getAppHdr() instanceof BusinessAppHdrV02 hdr) {
                if (hdr.getFr() != null && hdr.getFr().getFIId() != null
                        && hdr.getFr().getFIId().getFinInstnId() != null) {
                    senderBic = hdr.getFr().getFIId().getFinInstnId().getBICFI();
                }
            }

            return new CanonicalSettlement(
                    transactionId, isin, settlementDate, quantity,
                    direction, paymentType,
                    PartyInfo.ofBic(stripBicPadding(senderBic)),
                    PartyInfo.ofBic(counterpartyBic != null ? stripBicPadding(counterpartyBic) : "UNKNOWN"),
                    safekeepingAccount,
                    null, null, null);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse MX settlement instruction: " + e.getMessage(), e);
        }
    }

    private String extractCounterpartyBic(SecuritiesSettlementTransactionInstructionV09 instr) {
        if (instr.getDlvrgSttlmPties() != null && instr.getDlvrgSttlmPties().getPty1() != null) {
            PartyIdentification120Choice id = instr.getDlvrgSttlmPties().getPty1().getId();
            if (id != null && id.getAnyBIC() != null) {
                return id.getAnyBIC();
            }
        }
        if (instr.getRcvgSttlmPties() != null && instr.getRcvgSttlmPties().getPty1() != null) {
            PartyIdentification120Choice id = instr.getRcvgSttlmPties().getPty1().getId();
            if (id != null && id.getAnyBIC() != null) {
                return id.getAnyBIC();
            }
        }
        return null;
    }

    private String stripBicPadding(String bic) {
        if (bic == null) return null;
        if (bic.length() == 12 && bic.endsWith("XXXX")) {
            return bic.substring(0, 8);
        }
        if (bic.length() == 11 && bic.endsWith("XXX")) {
            return bic.substring(0, 8);
        }
        return bic;
    }

    @Override
    public CanonicalStatusAdvice parseStatusReply(String rawPayload) {
        try {
            MxSese02400110 mx = MxSese02400110.parse(rawPayload);
            if (mx == null || mx.getSctiesSttlmTxStsAdvc() == null) {
                log.warn("Failed to parse sese.024 — null result");
                return CanonicalStatusAdvice.unknown(
                        extractTradeRef(rawPayload),
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
                    extractTradeRef(rawPayload),
                    e.getMessage());
        }
    }

    @Override
    public String extractTradeRef(String rawPayload) {
        if (rawPayload == null) return null;

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

        return null;
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
