package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.BusinessException;
import com.settlement.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementInstructionDao instructionDao;

    @Mock
    private BondHoldingDao holdingDao;

    @Mock
    private AuditLogDao auditLogDao;

    @Mock
    private SwiftMessageBuilder messageBuilder;

    @Mock
    private AsyncSettlementProcessor asyncProcessor;

    @SuppressWarnings("unchecked")
    @Mock
    private ObjectProvider<AsyncSettlementProcessor> asyncProcessorProvider;

    private SettlementService settlementService;

    private SettlementRequest validRequest;

    @BeforeEach
    void setUp() {
        lenient().when(asyncProcessorProvider.getObject()).thenReturn(asyncProcessor);
        settlementService = new SettlementService(
                instructionDao, holdingDao, auditLogDao, messageBuilder, asyncProcessorProvider);

        validRequest = new SettlementRequest();
        validRequest.setIsin("US0378331005");
        validRequest.setSettlementDate(LocalDate.of(2026, 5, 15));
        validRequest.setQuantity(new BigDecimal("1000000.00"));
        validRequest.setCounterparty("Goldman Sachs");
        validRequest.setBicCode("GOLDUS33XXX");
        validRequest.setDirection("BUY");
        validRequest.setAccountId("ACC-001");
    }

    @Test
    void submitInstruction_shouldPersistWithPendingStatus() {
        when(messageBuilder.buildMT541(any())).thenReturn("{MT541 content}");
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getIsin()).isEqualTo("US0378331005");
        assertThat(result.getDirection()).isEqualTo(Direction.BUY);
        assertThat(result.getStatus()).isEqualTo(InstructionStatus.PENDING);
        assertThat(result.getTradeRef()).startsWith("TR-");
        assertThat(result.getMt541Raw()).isEqualTo("{MT541 content}");

        verify(instructionDao).save(any(SettlementInstruction.class));
        verify(messageBuilder).buildMT541(any());
        verify(auditLogDao).save(any());
    }

    @Test
    void submitInstruction_shouldTriggerAsyncProcessing() {
        when(messageBuilder.buildMT541(any())).thenReturn("{MT541}");
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        verify(asyncProcessor).processSettlementAsync(result.getTradeRef());
    }

    @Test
    void submitInstruction_shouldGenerateUniqueTradeRef() {
        when(messageBuilder.buildMT541(any())).thenReturn("{MT541}");
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementInstruction result1 = settlementService.submitInstruction(validRequest);
        SettlementInstruction result2 = settlementService.submitInstruction(validRequest);

        assertThat(result1.getTradeRef()).isNotEqualTo(result2.getTradeRef());
    }

    @Test
    void findByTradeRef_shouldReturnInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-ABC123");
        when(instructionDao.findByTradeRef("TR-ABC123")).thenReturn(Optional.of(instruction));

        SettlementInstruction result = settlementService.findByTradeRef("TR-ABC123");

        assertThat(result.getTradeRef()).isEqualTo("TR-ABC123");
    }

    @Test
    void findByTradeRef_shouldThrowWhenNotFound() {
        when(instructionDao.findByTradeRef("TR-NONEXIST")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.findByTradeRef("TR-NONEXIST"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("TR-NONEXIST");
    }

    @Test
    void submitInstruction_shouldSetMt541Raw() {
        String mt541Content = "{1:F01...MT541 message...}";
        when(messageBuilder.buildMT541(any())).thenReturn(mt541Content);
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        assertThat(result.getMt541Raw()).isEqualTo(mt541Content);
    }

    @Test
    void manualRetry_shouldResetFailedInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-RETRY001");
        instruction.setStatus(InstructionStatus.FAILED);
        instruction.setRetryCount(3);
        instruction.setFailureReason("Connection refused");
        when(instructionDao.findByTradeRef("TR-RETRY001")).thenReturn(Optional.of(instruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementInstruction result = settlementService.manualRetry("TR-RETRY001");

        assertThat(result.getStatus()).isEqualTo(InstructionStatus.PENDING);
        assertThat(result.getRetryCount()).isZero();
        assertThat(result.getFailureReason()).isNull();
        verify(asyncProcessor).processSettlementAsync("TR-RETRY001");
        verify(auditLogDao).save(any());
    }

    @Test
    void manualRetry_shouldRejectNonFailedInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef("TR-SENT001");
        instruction.setStatus(InstructionStatus.SENT);
        when(instructionDao.findByTradeRef("TR-SENT001")).thenReturn(Optional.of(instruction));

        assertThatThrownBy(() -> settlementService.manualRetry("TR-SENT001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("SENT");
    }

    @Test
    void manualRetry_shouldThrowWhenNotFound() {
        when(instructionDao.findByTradeRef("TR-NONE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> settlementService.manualRetry("TR-NONE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
