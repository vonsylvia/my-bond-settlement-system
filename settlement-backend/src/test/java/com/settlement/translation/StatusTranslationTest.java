package com.settlement.translation;

import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.canonical.CanonicalStatusAdvice.StatusOutcome;
import com.settlement.entity.MessageStandard;
import com.settlement.strategy.MtStrategy;
import com.settlement.strategy.MxStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatusTranslationTest {

    private TranslationService translationService;

    @BeforeEach
    void setUp() {
        MtStrategy mtStrategy = new MtStrategy();
        MxStrategy mxStrategy = new MxStrategy();
        SwiftMessageStrategyFactory factory = new SwiftMessageStrategyFactory(List.of(mtStrategy, mxStrategy));
        translationService = new TranslationService(factory);
    }

    @Test
    void mtBuildStatusReply_matched_shouldProduceMT548() {
        MtStrategy mt = new MtStrategy();
        CanonicalStatusAdvice advice = CanonicalStatusAdvice.matched("TR-BUILD001", "MTCHD");

        String result = mt.buildStatusReply(advice);

        assertThat(result).contains("548");
        assertThat(result).contains(":20C::SEME//TR-BUILD001");
        assertThat(result).contains(":25D::MTCH//MATC");
    }

    @Test
    void mtBuildStatusReply_failed_shouldContainNMAT() {
        MtStrategy mt = new MtStrategy();
        CanonicalStatusAdvice advice = CanonicalStatusAdvice.failed("TR-FAIL001", "UMTCHD", "No match");

        String result = mt.buildStatusReply(advice);

        assertThat(result).contains(":25D::MTCH//NMAT");
    }

    @Test
    void mxBuildStatusReply_matched_shouldProduceSese024() {
        MxStrategy mx = new MxStrategy();
        CanonicalStatusAdvice advice = CanonicalStatusAdvice.matched("TR-MX-STATUS", "MTCHD");

        String result = mx.buildStatusReply(advice);

        assertThat(result).contains("sese.024.001.10");
        assertThat(result).contains("TR-MX-STATUS");
    }

    @Test
    void translateStatusReply_mt548ToMx_shouldSucceed() {
        String mt548 =
                "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
                ":16R:GENL\r\n" +
                ":20C::SEME//TR-TRANS001\r\n" +
                ":23G:INST\r\n" +
                ":16S:GENL\r\n" +
                ":16R:STAT\r\n" +
                ":25D::MTCH//MATC\r\n" +
                ":16S:STAT\r\n" +
                "-}";

        TranslationService.StatusTranslationResult result =
                translationService.translateStatusReply(mt548);

        assertThat(result.sourceStandard()).isEqualTo(MessageStandard.MT);
        assertThat(result.targetStandard()).isEqualTo(MessageStandard.MX);
        assertThat(result.sourceMessageType()).isEqualTo("MT548");
        assertThat(result.targetMessageType()).isEqualTo("sese.024.001.10");
        assertThat(result.translatedPayload()).contains("sese.024.001.10");
        assertThat(result.canonical().outcome()).isEqualTo(StatusOutcome.MATCHED);
        assertThat(result.canonical().transactionId()).isEqualTo("TR-TRANS001");
    }

    @Test
    void translateStatusReply_mt548Failed_shouldPreserveOutcome() {
        String mt548 =
                "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
                ":16R:GENL\r\n" +
                ":20C::SEME//TR-FAIL002\r\n" +
                ":23G:INST\r\n" +
                ":16S:GENL\r\n" +
                ":16R:STAT\r\n" +
                ":25D::MTCH//NMAT\r\n" +
                ":16S:STAT\r\n" +
                "-}";

        TranslationService.StatusTranslationResult result =
                translationService.translateStatusReply(mt548);

        assertThat(result.canonical().outcome()).isEqualTo(StatusOutcome.FAILED);
    }

    @Test
    void translateStatusReply_blankPayload_shouldThrow() {
        assertThatThrownBy(() -> translationService.translateStatusReply(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }
}
