package com.settlement.strategy;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.PartyInfo;
import com.settlement.canonical.PaymentType;
import com.settlement.canonical.SettlementDirection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MtStrategyParseTest {

    private MtStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MtStrategy();
    }

    @Test
    void parseSettlementInstruction_shouldRoundTripFromBuild() {
        CanonicalSettlement original = new CanonicalSettlement(
                "TR-PARSE-001", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33"),
                "ACC-001", null, null, null);

        String mt541 = strategy.buildSettlementInstruction(original);
        CanonicalSettlement parsed = strategy.parseSettlementInstruction(mt541);

        assertThat(parsed.transactionId()).isEqualTo("TR-PARSE-001");
        assertThat(parsed.isin()).isEqualTo("US0378331005");
        assertThat(parsed.quantity()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(parsed.safekeepingAccount()).isEqualTo("ACC-001");
    }

    @Test
    void parseSettlementInstruction_shouldRoundTripDirectionAndPaymentTypeForMt54xFamily() {
        assertMt54xRoundTrip(SettlementDirection.RECEIVE, PaymentType.FREE_OF_PAYMENT);
        assertMt54xRoundTrip(SettlementDirection.RECEIVE, PaymentType.AGAINST_PAYMENT);
        assertMt54xRoundTrip(SettlementDirection.DELIVER, PaymentType.FREE_OF_PAYMENT);
        assertMt54xRoundTrip(SettlementDirection.DELIVER, PaymentType.AGAINST_PAYMENT);
    }

    @Test
    void parseSettlementInstruction_shouldExtractTradeRef() {
        String mt541 = buildSampleMt541("TR-REF-XYZ");
        CanonicalSettlement parsed = strategy.parseSettlementInstruction(mt541);

        assertThat(parsed.transactionId()).isEqualTo("TR-REF-XYZ");
    }

    @Test
    void parseSettlementInstruction_shouldExtractIsin() {
        String mt541 = buildSampleMt541("TR-ISIN-001");
        CanonicalSettlement parsed = strategy.parseSettlementInstruction(mt541);

        assertThat(parsed.isin()).isEqualTo("US0378331005");
    }

    @Test
    void parseSettlementInstruction_shouldRejectNullPayload() {
        assertThatThrownBy(() -> strategy.parseSettlementInstruction(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseSettlementInstruction_shouldRejectGarbledPayload() {
        assertThatThrownBy(() -> strategy.parseSettlementInstruction("not a swift message"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private String buildSampleMt541(String tradeRef) {
        CanonicalSettlement canonical = new CanonicalSettlement(
                tradeRef, "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("500000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33"),
                "ACC-002", null, null, null);
        return strategy.buildSettlementInstruction(canonical);
    }

    private void assertMt54xRoundTrip(SettlementDirection direction, PaymentType paymentType) {
        CanonicalSettlement original = new CanonicalSettlement(
                "TR-" + direction + "-" + paymentType,
                "US0378331005",
                LocalDate.of(2026, 5, 15),
                new BigDecimal("500000.00"),
                direction,
                paymentType,
                PartyInfo.ofBic("OWNRBICXXX"),
                PartyInfo.ofBic("GOLDUS33"),
                "ACC-002",
                null,
                null,
                null);

        CanonicalSettlement parsed = strategy.parseSettlementInstruction(
                strategy.buildSettlementInstruction(original));

        assertThat(parsed.direction()).isEqualTo(direction);
        assertThat(parsed.paymentType()).isEqualTo(paymentType);
    }
}
