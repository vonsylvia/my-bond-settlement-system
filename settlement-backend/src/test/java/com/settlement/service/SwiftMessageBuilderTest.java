package com.settlement.service;

import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class SwiftMessageBuilderTest {

    private SwiftMessageBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new SwiftMessageBuilder();
    }

    @Test
    void buildMT541_shouldContainTradeRef() {
        SettlementInstruction instruction = createSampleInstruction();

        String message = builder.buildMT541(instruction);

        assertThat(message).isNotNull();
        assertThat(message).contains("TR-TEST123456");
    }

    @Test
    void buildMT541_shouldContainISIN() {
        SettlementInstruction instruction = createSampleInstruction();

        String message = builder.buildMT541(instruction);

        assertThat(message).contains("US0378331005");
    }

    @Test
    void buildMT541_shouldContainQuantity() {
        SettlementInstruction instruction = createSampleInstruction();

        String message = builder.buildMT541(instruction);

        assertThat(message).contains("1000000");
    }

    @Test
    void buildMT541_shouldContainBIC() {
        SettlementInstruction instruction = createSampleInstruction();

        String message = builder.buildMT541(instruction);

        assertThat(message).contains("GOLDUS33XXX");
    }

    @Test
    void buildMT541_shouldContainSettlementDate() {
        SettlementInstruction instruction = createSampleInstruction();

        String message = builder.buildMT541(instruction);

        assertThat(message).contains("20260515");
    }

    @Test
    void buildMT541_shouldContainNewMessageFunction() {
        SettlementInstruction instruction = createSampleInstruction();

        String message = builder.buildMT541(instruction);

        assertThat(message).contains("NEWM");
    }

    @Test
    void buildMT541_shouldPadShortBIC() {
        SettlementInstruction instruction = createSampleInstruction();
        instruction.setBicCode("GOLDUS33");

        String message = builder.buildMT541(instruction);

        assertThat(message).contains("GOLDUS33XXX");
    }

    private SettlementInstruction createSampleInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-TEST123456");
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        instruction.setQuantity(new BigDecimal("1000000.00"));
        instruction.setCounterparty("Goldman Sachs");
        instruction.setBicCode("GOLDUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setAccountId("ACC-001");
        instruction.setStatus(InstructionStatus.PENDING);
        return instruction;
    }
}
