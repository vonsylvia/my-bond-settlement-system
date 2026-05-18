package com.settlement.strategy;

import com.prowidesoftware.swift.model.mx.MxSese02300109;
import com.prowidesoftware.swift.model.mx.MxSese02400110;
import com.prowidesoftware.swift.model.mx.dic.*;
import com.settlement.canonical.*;
import com.settlement.canonical.CanonicalStatusAdvice.StatusOutcome;
import com.settlement.entity.MessageStandard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MxStrategyTest {

    private MxStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MxStrategy();
    }

    @Test
    void getStandard_shouldReturnMX() {
        assertThat(strategy.getStandard()).isEqualTo(MessageStandard.MX);
    }

    @Test
    void getOutboundMessageType_shouldReturnSese023() {
        assertThat(strategy.getOutboundMessageType(createSample()))
                .isEqualTo("sese.023.001.09");
    }

    @Test
    void getInboundStatusType_shouldReturnSese024() {
        assertThat(strategy.getInboundStatusType()).isEqualTo("sese.024.001.10");
    }

    // ── Build tests ──

    @Test
    void buildSettlementInstruction_shouldProduceValidXml() {
        String xml = strategy.buildSettlementInstruction(createSample());

        assertThat(xml).isNotNull();
        assertThat(xml).contains("sese.023.001.09");
        assertThat(xml).contains("TR-MX-TEST001");
        assertThat(xml).contains("US0378331005");
    }

    @Test
    void buildSettlementInstruction_shouldContainIsin() {
        String xml = strategy.buildSettlementInstruction(createSample());
        assertThat(xml).containsPattern("<[^>]*ISIN>US0378331005</");
    }

    @Test
    void buildSettlementInstruction_shouldContainTradeRef() {
        String xml = strategy.buildSettlementInstruction(createSample());
        assertThat(xml).containsPattern("<[^>]*TxId>TR-MX-TEST001</");
    }

    @Test
    void buildSettlementInstruction_shouldContainSafekeepingAccount() {
        String xml = strategy.buildSettlementInstruction(createSample());
        assertThat(xml).containsPattern("<[^>]*SfkpgAcct>");
        assertThat(xml).contains("ACC-MX01");
    }

    @Test
    void buildSettlementInstruction_shouldContainBic() {
        String xml = strategy.buildSettlementInstruction(createSample());
        assertThat(xml).contains("GOLDUS33XXX");
    }

    @Test
    void buildSettlementInstruction_shouldSetCounterpartyAsDeliveringPartyForReceive() {
        String xml = strategy.buildSettlementInstruction(createSampleWithDirection(SettlementDirection.RECEIVE));
        MxSese02300109 parsed = MxSese02300109.parse(xml);
        SecuritiesSettlementTransactionInstructionV09 instruction = parsed.getSctiesSttlmTxInstr();

        assertThat(instruction.getDlvrgSttlmPties()).isNotNull();
        assertThat(instruction.getDlvrgSttlmPties().getPty1().getId().getAnyBIC()).isEqualTo("GOLDUS33XXX");
        assertThat(instruction.getRcvgSttlmPties()).isNull();
    }

    @Test
    void buildSettlementInstruction_shouldSetCounterpartyAsReceivingPartyForDeliver() {
        String xml = strategy.buildSettlementInstruction(createSampleWithDirection(SettlementDirection.DELIVER));
        MxSese02300109 parsed = MxSese02300109.parse(xml);
        SecuritiesSettlementTransactionInstructionV09 instruction = parsed.getSctiesSttlmTxInstr();

        assertThat(instruction.getRcvgSttlmPties()).isNotNull();
        assertThat(instruction.getRcvgSttlmPties().getPty1().getId().getAnyBIC()).isEqualTo("GOLDUS33XXX");
        assertThat(instruction.getDlvrgSttlmPties()).isNull();
    }

    @Test
    void buildSettlementInstruction_shouldSetReceiveForBuy() {
        CanonicalSettlement cs = createSampleWithDirection(SettlementDirection.RECEIVE);
        String xml = strategy.buildSettlementInstruction(cs);
        assertThat(xml).containsPattern("<[^>]*SctiesMvmntTp>RECE</");
    }

    @Test
    void buildSettlementInstruction_shouldSetDeliverForSell() {
        CanonicalSettlement cs = createSampleWithDirection(SettlementDirection.DELIVER);
        String xml = strategy.buildSettlementInstruction(cs);
        assertThat(xml).containsPattern("<[^>]*SctiesMvmntTp>DELI</");
    }

    @Test
    void buildSettlementInstruction_shouldBeRoundTrippable() {
        String xml = strategy.buildSettlementInstruction(createSample());
        MxSese02300109 parsed = MxSese02300109.parse(xml);

        assertThat(parsed).isNotNull();
        assertThat(parsed.getSctiesSttlmTxInstr().getTxId()).isEqualTo("TR-MX-TEST001");
        assertThat(parsed.getSctiesSttlmTxInstr().getFinInstrmId().getISIN()).isEqualTo("US0378331005");
    }

    @Test
    void buildSettlementInstruction_shouldSetPaymentAgainst() {
        String xml = strategy.buildSettlementInstruction(createSample());
        assertThat(xml).containsPattern("<[^>]*Pmt>APMT</");
    }

    // ── Parse status reply tests ──

    @Test
    void parseStatusReply_shouldReturnMatched_forMatchedStatus() {
        String xml = buildStatusAdvice("TR-MX-TEST001", "matched");
        CanonicalStatusAdvice result = strategy.parseStatusReply(xml);

        assertThat(result.outcome()).isEqualTo(StatusOutcome.MATCHED);
        assertThat(result.transactionId()).isEqualTo("TR-MX-TEST001");
    }

    @Test
    void parseStatusReply_shouldReturnFailed_forUnmatchedStatus() {
        String xml = buildStatusAdvice("TR-MX-TEST002", "unmatched");
        CanonicalStatusAdvice result = strategy.parseStatusReply(xml);

        assertThat(result.outcome()).isEqualTo(StatusOutcome.FAILED);
    }

    @Test
    void parseStatusReply_shouldReturnFailed_forRejected() {
        String xml = buildStatusAdvice("TR-MX-TEST003", "rejected");
        CanonicalStatusAdvice result = strategy.parseStatusReply(xml);

        assertThat(result.outcome()).isEqualTo(StatusOutcome.FAILED);
        assertThat(result.statusCode()).isEqualTo("RJCTD");
    }

    @Test
    void parseStatusReply_shouldReturnPending_forPendingProcessing() {
        String xml = buildStatusAdvice("TR-MX-TEST004", "pending");
        CanonicalStatusAdvice result = strategy.parseStatusReply(xml);

        assertThat(result.outcome()).isEqualTo(StatusOutcome.PENDING);
    }

    @Test
    void parseStatusReply_shouldReturnUnknown_forGarbled() {
        CanonicalStatusAdvice result = strategy.parseStatusReply("not xml at all");
        assertThat(result.outcome()).isEqualTo(StatusOutcome.UNKNOWN);
    }

    // ── Extract trade ref tests ──

    @Test
    void extractTradeRef_shouldExtractFromSese024() {
        String xml = buildStatusAdvice("TR-MX-REF123", "matched");
        String ref = strategy.extractTradeRef(xml);
        assertThat(ref).isEqualTo("TR-MX-REF123");
    }

    @Test
    void extractTradeRef_shouldReturnNull_forGarbled() {
        String ref = strategy.extractTradeRef("not xml");
        assertThat(ref).isNull();
    }

    @Test
    void extractTradeRef_shouldExtractViaRegex_whenParserFails() {
        String partialXml = "<something><AcctOwnrTxId>TR-REGEX-001</AcctOwnrTxId></something>";
        String ref = strategy.extractTradeRef(partialXml);
        assertThat(ref).isEqualTo("TR-REGEX-001");
    }

    // ── Helpers ──

    private CanonicalSettlement createSample() {
        return createSampleWithDirection(SettlementDirection.RECEIVE);
    }

    private CanonicalSettlement createSampleWithDirection(SettlementDirection direction) {
        return new CanonicalSettlement(
                "TR-MX-TEST001", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), direction,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33XXX"),
                "ACC-MX01", null, null, null);
    }

    private String buildStatusAdvice(String tradeRef, String statusType) {
        MxSese02400110 mx = new MxSese02400110();
        SecuritiesSettlementTransactionStatusAdviceV10 advice =
                new SecuritiesSettlementTransactionStatusAdviceV10();

        advice.setTxId(new TransactionIdentifications31()
                .setAcctOwnrTxId(tradeRef));

        switch (statusType) {
            case "matched" -> advice.setMtchgSts(
                    new MatchingStatus24Choice().setMtchd(new ProprietaryReason4()));
            case "unmatched" -> advice.setMtchgSts(
                    new MatchingStatus24Choice().setUmtchd(new UnmatchedStatus16Choice()));
            case "rejected" -> advice.setPrcgSts(
                    new ProcessingStatus74Choice().setRjctd(new RejectionStatus21Choice()));
            case "pending" -> advice.setPrcgSts(
                    new ProcessingStatus74Choice().setPdgPrcg(new PendingProcessingStatus11Choice()));
            case "cancelled" -> advice.setPrcgSts(
                    new ProcessingStatus74Choice().setCanc(new CancellationStatus24Choice()));
        }

        mx.setSctiesSttlmTxStsAdvc(advice);
        return mx.message();
    }
}
