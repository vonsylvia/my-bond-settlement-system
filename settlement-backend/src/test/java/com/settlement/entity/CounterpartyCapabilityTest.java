package com.settlement.entity;

import org.junit.jupiter.api.Test;

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
}
