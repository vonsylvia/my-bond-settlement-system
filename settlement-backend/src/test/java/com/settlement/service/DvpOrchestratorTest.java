package com.settlement.service;

import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DvpOrchestratorTest {

    @Mock
    private SettlementInstructionDao instructionDao;

    @Mock
    private DvpSettlementService dvpSettlementService;

    private DvpOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        Executor directExecutor = Runnable::run;
        orchestrator = new DvpOrchestrator(
                instructionDao, dvpSettlementService, directExecutor);
    }

    @Test
    void processMatchedInstructionAsync_shouldLockAndCompleteDvp() {
        SettlementInstruction instruction = matchedInstruction();
        when(instructionDao.findByTradeRef("TR-DVP-AUTO"))
                .thenReturn(Optional.of(instruction))
                .thenReturn(Optional.of(instruction));
        doAnswer(inv -> {
            instruction.setStatus(InstructionStatus.DVP_LOCKED);
            return null;
        }).when(dvpSettlementService).lockForDvp(instruction);

        orchestrator.processMatchedInstructionAsync("TR-DVP-AUTO");

        verify(dvpSettlementService).lockForDvp(instruction);
        verify(dvpSettlementService).completeDvp(instruction);
        verify(dvpSettlementService, never()).recordAutomaticDvpFailure(anyString(), anyString());
    }

    @Test
    void processMatchedInstructionAsync_shouldSkipAlreadyFinalInstruction() {
        SettlementInstruction instruction = matchedInstruction();
        instruction.setFinal(true);
        when(instructionDao.findByTradeRef("TR-DVP-AUTO")).thenReturn(Optional.of(instruction));

        orchestrator.processMatchedInstructionAsync("TR-DVP-AUTO");

        verifyNoInteractions(dvpSettlementService);
    }

    @Test
    void processMatchedInstructionAsync_shouldSkipFreeOfPaymentInstruction() {
        SettlementInstruction instruction = matchedInstruction();
        instruction.setPaymentType("FREE_OF_PAYMENT");
        when(instructionDao.findByTradeRef("TR-DVP-AUTO")).thenReturn(Optional.of(instruction));

        orchestrator.processMatchedInstructionAsync("TR-DVP-AUTO");

        verifyNoInteractions(dvpSettlementService);
    }

    @Test
    void processMatchedInstructionAsync_shouldRollbackAndAudit_whenCompletionFailsAfterLock() {
        SettlementInstruction instruction = matchedInstruction();
        when(instructionDao.findByTradeRef("TR-DVP-AUTO"))
                .thenReturn(Optional.of(instruction))
                .thenReturn(Optional.of(instruction))
                .thenReturn(Optional.of(instruction));
        doAnswer(inv -> {
            instruction.setStatus(InstructionStatus.DVP_LOCKED);
            return null;
        }).when(dvpSettlementService).lockForDvp(instruction);
        doThrow(new IllegalStateException("securities leg failed"))
                .when(dvpSettlementService).completeDvp(instruction);

        orchestrator.processMatchedInstructionAsync("TR-DVP-AUTO");

        verify(dvpSettlementService).rollbackDvp(instruction);
        verify(dvpSettlementService).recordAutomaticDvpFailure("TR-DVP-AUTO", "securities leg failed");
    }

    private static SettlementInstruction matchedInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setId(1L);
        instruction.setTradeRef("TR-DVP-AUTO");
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        instruction.setQuantity(new BigDecimal("1000000.00"));
        instruction.setCounterparty("Goldman Sachs");
        instruction.setBicCode("GOLDUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setStatus(InstructionStatus.MATCHED);
        instruction.setAccountId("ACC-001");
        instruction.setCurrency("HKD");
        instruction.setPaymentType("AGAINST_PAYMENT");
        instruction.setSettlementAmount(new BigDecimal("5000000.00"));
        return instruction;
    }
}
