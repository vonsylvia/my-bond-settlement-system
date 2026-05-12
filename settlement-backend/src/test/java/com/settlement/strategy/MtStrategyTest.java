package com.settlement.strategy;

import com.settlement.canonical.*;
import com.settlement.canonical.CanonicalStatusAdvice.StatusOutcome;
import com.settlement.entity.MessageStandard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MtStrategyTest {

    private MtStrategy strategy;

    private static final String MT548_MATCHED =
            "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
            ":16R:GENL\r\n" +
            ":20C::SEME//TR-TEST123456\r\n" +
            ":23G:INST\r\n" +
            ":16S:GENL\r\n" +
            ":16R:STAT\r\n" +
            ":25D::MTCH//MATC\r\n" +
            ":16S:STAT\r\n" +
            "-}";

    private static final String MT548_REJECTED =
            "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
            ":16R:GENL\r\n" +
            ":20C::SEME//TR-TEST123456\r\n" +
            ":23G:INST\r\n" +
            ":16S:GENL\r\n" +
            ":16R:STAT\r\n" +
            ":25D::MTCH//REJT\r\n" +
            ":16S:STAT\r\n" +
            "-}";

    @BeforeEach
    void setUp() {
        strategy = new MtStrategy();
    }

    @Test
    void getStandard_shouldReturnMT() {
        assertThat(strategy.getStandard()).isEqualTo(MessageStandard.MT);
    }

    @Test
    void buildSettlementInstruction_shouldContainAllFields() {
        CanonicalSettlement canonical = createSample();
        String message = strategy.buildSettlementInstruction(canonical);

        assertThat(message).isNotNull();
        assertThat(message).contains("TR-TEST123456");
        assertThat(message).contains("US0378331005");
        assertThat(message).contains("1000000");
        assertThat(message).contains("GOLDUS33XXX");
        assertThat(message).contains("20260515");
        assertThat(message).contains("NEWM");
    }

    @Test
    void parseStatusReply_shouldReturnMatched() {
        CanonicalStatusAdvice result = strategy.parseStatusReply(MT548_MATCHED);

        assertThat(result.outcome()).isEqualTo(StatusOutcome.MATCHED);
        assertThat(result.transactionId()).isEqualTo("TR-TEST123456");
        assertThat(result.statusCode()).isEqualTo("MATC");
    }

    @Test
    void parseStatusReply_shouldReturnFailed_forRejected() {
        CanonicalStatusAdvice result = strategy.parseStatusReply(MT548_REJECTED);

        assertThat(result.outcome()).isEqualTo(StatusOutcome.FAILED);
        assertThat(result.statusCode()).isEqualTo("REJT");
    }

    @Test
    void parseStatusReply_shouldReturnUnknown_forGarbled() {
        CanonicalStatusAdvice result = strategy.parseStatusReply("not a swift message");

        assertThat(result.outcome()).isEqualTo(StatusOutcome.UNKNOWN);
    }

    @Test
    void extractTradeRef_shouldExtractFromMessage() {
        String ref = strategy.extractTradeRef(MT548_MATCHED);
        assertThat(ref).isEqualTo("TR-TEST123456");
    }

    @Test
    void extractTradeRef_shouldReturnNull_forGarbled() {
        String ref = strategy.extractTradeRef("garbled");
        assertThat(ref).isNull();
    }

    @Test
    void sanitiseSwiftMessage_shouldNormaliseLineEndings() {
        String onlyLf = "{4:\n:20C::SEME//REF1\n:25D::MTCH//MATC\n-}";
        String result = MtStrategy.sanitiseSwiftMessage(onlyLf);
        assertThat(result).doesNotContain("\n\n");
        assertThat(result).contains("\r\n");
    }

    @Test
    void sanitiseSwiftMessage_shouldStripBomAndNulBytes() {
        String withBomAndNul = "\uFEFF{4:\r\n:20C::SEME//REF1\0\r\n-}";
        String result = MtStrategy.sanitiseSwiftMessage(withBomAndNul);
        assertThat(result).doesNotContain("\uFEFF");
        assertThat(result).doesNotContain("\0");
        assertThat(result).startsWith("{4:");
    }

    @Test
    void sanitiseSwiftMessage_shouldHandleNullAndEmpty() {
        assertThat(MtStrategy.sanitiseSwiftMessage(null)).isNull();
        assertThat(MtStrategy.sanitiseSwiftMessage("")).isEmpty();
    }

    @Test
    void getOutboundMessageType_shouldReturnMT541() {
        assertThat(strategy.getOutboundMessageType(createSample())).isEqualTo("MT541");
    }

    @Test
    void getInboundStatusType_shouldReturnMT548() {
        assertThat(strategy.getInboundStatusType()).isEqualTo("MT548");
    }

    private CanonicalSettlement createSample() {
        return new CanonicalSettlement(
                "TR-TEST123456", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33XXX"),
                "ACC-001", null, null, null);
    }
}
