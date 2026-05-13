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

class MxStrategyParseTest {

    private MxStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new MxStrategy();
    }

    @Test
    void parseSettlementInstruction_shouldRoundTripFromBuild() {
        CanonicalSettlement original = new CanonicalSettlement(
                "TR-MX-PARSE-001", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33"),
                "ACC-MX01", null, null, null);

        String xml = strategy.buildSettlementInstruction(original);
        CanonicalSettlement parsed = strategy.parseSettlementInstruction(xml);

        assertThat(parsed.transactionId()).isEqualTo("TR-MX-PARSE-001");
        assertThat(parsed.isin()).isEqualTo("US0378331005");
        assertThat(parsed.settlementDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(parsed.quantity()).isEqualByComparingTo(new BigDecimal("1000000.00"));
        assertThat(parsed.direction()).isEqualTo(SettlementDirection.RECEIVE);
        assertThat(parsed.safekeepingAccount()).isEqualTo("ACC-MX01");
    }

    @Test
    void parseSettlementInstruction_shouldExtractCounterpartyBic() {
        CanonicalSettlement original = new CanonicalSettlement(
                "TR-MX-BIC-001", "DE000A0TGJ55", LocalDate.of(2026, 6, 1),
                new BigDecimal("250000.00"), SettlementDirection.DELIVER,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("DEUTDEFF"),
                "ACC-DE01", null, null, null);

        String xml = strategy.buildSettlementInstruction(original);
        CanonicalSettlement parsed = strategy.parseSettlementInstruction(xml);

        assertThat(parsed.counterparty().bic()).isEqualTo("DEUTDEFF");
        assertThat(parsed.direction()).isEqualTo(SettlementDirection.DELIVER);
    }

    @Test
    void parseSettlementInstruction_shouldRejectNullPayload() {
        assertThatThrownBy(() -> strategy.parseSettlementInstruction(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseSettlementInstruction_shouldRejectGarbledXml() {
        assertThatThrownBy(() -> strategy.parseSettlementInstruction("<not>valid</not>"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
