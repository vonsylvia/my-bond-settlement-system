package com.settlement.translation;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.PartyInfo;
import com.settlement.canonical.PaymentType;
import com.settlement.canonical.SettlementDirection;
import com.settlement.entity.MessageStandard;
import com.settlement.strategy.MtStrategy;
import com.settlement.strategy.MxStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TranslationServiceTest {

    private TranslationService translationService;
    private MtStrategy mtStrategy;
    private MxStrategy mxStrategy;

    @BeforeEach
    void setUp() {
        mtStrategy = new MtStrategy();
        mxStrategy = new MxStrategy();
        SwiftMessageStrategyFactory factory = new SwiftMessageStrategyFactory(List.of(mtStrategy, mxStrategy));
        translationService = new TranslationService(factory);
    }

    @Test
    void translate_mtToMx_shouldProduceValidMxXml() {
        String mt541 = mtStrategy.buildSettlementInstruction(createSample());

        TranslationResult result = translationService.translate(mt541, MessageStandard.MX);

        assertThat(result.sourceStandard()).isEqualTo(MessageStandard.MT);
        assertThat(result.targetStandard()).isEqualTo(MessageStandard.MX);
        assertThat(result.sourceMessageType()).isEqualTo("MT541");
        assertThat(result.targetMessageType()).isEqualTo("sese.023.001.09");
        assertThat(result.translatedPayload()).contains("sese.023.001.09");
        assertThat(result.translatedPayload()).contains("TR-XLATE-001");
        assertThat(result.canonical().transactionId()).isEqualTo("TR-XLATE-001");
    }

    @Test
    void translate_mxToMt_shouldProduceValidMtFin() {
        String mx023 = mxStrategy.buildSettlementInstruction(createSample());

        TranslationResult result = translationService.translate(mx023, MessageStandard.MT);

        assertThat(result.sourceStandard()).isEqualTo(MessageStandard.MX);
        assertThat(result.targetStandard()).isEqualTo(MessageStandard.MT);
        assertThat(result.sourceMessageType()).isEqualTo("sese.023.001.09");
        assertThat(result.targetMessageType()).isEqualTo("MT541");
        assertThat(result.translatedPayload()).contains("TR-XLATE-001");
        assertThat(result.canonical().isin()).isEqualTo("US0378331005");
    }

    @Test
    void translate_autoDetect_mtToMx() {
        String mt541 = mtStrategy.buildSettlementInstruction(createSample());

        TranslationResult result = translationService.translate(mt541);

        assertThat(result.sourceStandard()).isEqualTo(MessageStandard.MT);
        assertThat(result.targetStandard()).isEqualTo(MessageStandard.MX);
    }

    @Test
    void translate_autoDetect_mxToMt() {
        String mx023 = mxStrategy.buildSettlementInstruction(createSample());

        TranslationResult result = translationService.translate(mx023);

        assertThat(result.sourceStandard()).isEqualTo(MessageStandard.MX);
        assertThat(result.targetStandard()).isEqualTo(MessageStandard.MT);
    }

    @Test
    void translate_shouldPreserveKeyFields_roundTrip() {
        CanonicalSettlement original = createSample();
        String mt541 = mtStrategy.buildSettlementInstruction(original);

        TranslationResult toMx = translationService.translate(mt541, MessageStandard.MX);
        TranslationResult backToMt = translationService.translate(toMx.translatedPayload(), MessageStandard.MT);

        assertThat(backToMt.canonical().transactionId()).isEqualTo(original.transactionId());
        assertThat(backToMt.canonical().isin()).isEqualTo(original.isin());
        assertThat(backToMt.canonical().quantity()).isEqualByComparingTo(original.quantity());
    }

    @Test
    void translate_sameStandard_shouldThrow() {
        String mt541 = mtStrategy.buildSettlementInstruction(createSample());

        assertThatThrownBy(() -> translationService.translate(mt541, MessageStandard.MT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("both MT");
    }

    @Test
    void translate_blankPayload_shouldThrow() {
        assertThatThrownBy(() -> translationService.translate("", MessageStandard.MX))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> translationService.translate(null, MessageStandard.MX))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detect_shouldIdentifyMtMessage() {
        String mt541 = mtStrategy.buildSettlementInstruction(createSample());

        TranslationService.DetectionResult detection = translationService.detect(mt541);

        assertThat(detection.standard()).isEqualTo(MessageStandard.MT);
        assertThat(detection.messageType()).isEqualTo("MT541");
        assertThat(detection.tradeRef()).isEqualTo("TR-XLATE-001");
    }

    @Test
    void detect_shouldIdentifyMxMessage() {
        String mx023 = mxStrategy.buildSettlementInstruction(createSample());

        TranslationService.DetectionResult detection = translationService.detect(mx023);

        assertThat(detection.standard()).isEqualTo(MessageStandard.MX);
        assertThat(detection.messageType()).isEqualTo("sese.023.001.09");
        assertThat(detection.tradeRef()).isEqualTo("TR-XLATE-001");
    }

    private CanonicalSettlement createSample() {
        return new CanonicalSettlement(
                "TR-XLATE-001", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33"),
                "ACC-001", null, null, null);
    }
}
