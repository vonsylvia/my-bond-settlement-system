package com.settlement.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class CounterpartyCapabilityTest {

    @Test
    void resolveOutboundStandard_mtOnly_shouldReturnMT() {
        CounterpartyCapability cap = new CounterpartyCapability(
                "GOLDUS33XXX", "Goldman Sachs",
                SupportedStandard.MT_ONLY, MessageStandard.MT);
        assertThat(cap.resolveOutboundStandard()).isEqualTo(MessageStandard.MT);
    }

    @Test
    void resolveOutboundStandard_mxOnly_shouldReturnMX() {
        CounterpartyCapability cap = new CounterpartyCapability(
                "DEUTDEFFXXX", "Deutsche Bank",
                SupportedStandard.MX_ONLY, MessageStandard.MX);
        assertThat(cap.resolveOutboundStandard()).isEqualTo(MessageStandard.MX);
    }

    @Test
    void resolveOutboundStandard_dual_shouldReturnPreferred() {
        CounterpartyCapability cap = new CounterpartyCapability(
                "HSBCHKHHXXX", "HSBC HK",
                SupportedStandard.DUAL, MessageStandard.MX);
        assertThat(cap.resolveOutboundStandard()).isEqualTo(MessageStandard.MX);
    }

    @Test
    void resolveOutboundStandard_dual_defaultsToMT() {
        CounterpartyCapability cap = new CounterpartyCapability(
                "BOFAUS3NXXX", "BofA",
                SupportedStandard.DUAL, MessageStandard.MT);
        assertThat(cap.resolveOutboundStandard()).isEqualTo(MessageStandard.MT);
    }

    @Test
    void resolveOutboundStandard_shouldFallbackToMT_whenEffectiveDateInFuture() {
        CounterpartyCapability cap = new CounterpartyCapability(
                "FUTUREBICXX", "Future Bank",
                SupportedStandard.MX_ONLY, MessageStandard.MX);
        cap.setEffectiveDate(LocalDate.now().plusDays(30));

        assertThat(cap.resolveOutboundStandard()).isEqualTo(MessageStandard.MT);
    }

    @Test
    void resolveOutboundStandard_shouldUseConfigured_whenEffectiveDateInPast() {
        CounterpartyCapability cap = new CounterpartyCapability(
                "PASTBICXXXX", "Past Bank",
                SupportedStandard.MX_ONLY, MessageStandard.MX);
        cap.setEffectiveDate(LocalDate.now().minusDays(1));

        assertThat(cap.resolveOutboundStandard()).isEqualTo(MessageStandard.MX);
    }

    @Test
    void resolveOutboundStandard_shouldUseConfigured_whenEffectiveDateIsToday() {
        CounterpartyCapability cap = new CounterpartyCapability(
                "TODAYBICXXX", "Today Bank",
                SupportedStandard.DUAL, MessageStandard.MX);
        cap.setEffectiveDate(LocalDate.now());

        assertThat(cap.resolveOutboundStandard()).isEqualTo(MessageStandard.MX);
    }
}
