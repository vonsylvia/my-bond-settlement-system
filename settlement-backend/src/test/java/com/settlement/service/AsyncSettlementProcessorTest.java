package com.settlement.service;

import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AsyncSettlementProcessorTest {

    @Mock
    private SettlementXaExecutor xaExecutor;

    @Mock
    private AlertWebhookService alertWebhookService;

    private AsyncSettlementProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new AsyncSettlementProcessor(xaExecutor, alertWebhookService);
    }

    @Test
    void processSettlementAsync_shouldCallExecutor() {
        doNothing().when(xaExecutor).executeSettlement("TR-ASYNC001");

        processor.processSettlementAsync("TR-ASYNC001");

        verify(xaExecutor).executeSettlement("TR-ASYNC001");
        verify(xaExecutor, never()).recordFailure(anyString(), anyInt(), any(), anyBoolean(), anyInt());
    }

    @Test
    void processSettlementAsync_shouldRetryOnFailure() {
        doThrow(new RuntimeException("MQ down"))
                .doNothing()
                .when(xaExecutor).executeSettlement("TR-ASYNC002");

        processor.processSettlementAsync("TR-ASYNC002");

        verify(xaExecutor, times(2)).executeSettlement("TR-ASYNC002");
        verify(xaExecutor).recordFailure(eq("TR-ASYNC002"), eq(1), any(), eq(false), eq(3));
    }

    @Test
    void processSettlementAsync_shouldSendWebhookWhenExhausted() {
        doThrow(new RuntimeException("MQ down"))
                .when(xaExecutor).executeSettlement("TR-ASYNC003");

        SettlementInstruction failedInstr = new SettlementInstruction();
        failedInstr.setTradeRef("TR-ASYNC003");
        failedInstr.setStatus(InstructionStatus.FAILED);
        failedInstr.setFailureReason("MQ down");
        when(xaExecutor.findByTradeRef("TR-ASYNC003")).thenReturn(failedInstr);

        processor.processSettlementAsync("TR-ASYNC003");

        verify(xaExecutor, times(3)).executeSettlement("TR-ASYNC003");
        verify(xaExecutor).recordFailure(eq("TR-ASYNC003"), eq(3), any(), eq(true), eq(3));
        verify(alertWebhookService).sendExhaustedAlert(failedInstr);
    }

    @Test
    void processSettlementAsync_shouldRecordFailureForEachAttempt() {
        doThrow(new RuntimeException("timeout"))
                .when(xaExecutor).executeSettlement("TR-ASYNC004");

        lenient().when(xaExecutor.findByTradeRef("TR-ASYNC004")).thenReturn(null);

        processor.processSettlementAsync("TR-ASYNC004");

        verify(xaExecutor).recordFailure(eq("TR-ASYNC004"), eq(1), any(), eq(false), eq(3));
        verify(xaExecutor).recordFailure(eq("TR-ASYNC004"), eq(2), any(), eq(false), eq(3));
        verify(xaExecutor).recordFailure(eq("TR-ASYNC004"), eq(3), any(), eq(true), eq(3));
    }

    @Test
    void recoverOrphanedInstructions_shouldDelegateToXaExecutor() {
        when(xaExecutor.recoverOrphanedInstructions())
                .thenReturn(java.util.List.of("TR-ORPHAN1", "TR-ORPHAN2"));
        doNothing().when(xaExecutor).executeSettlement(anyString());

        processor.recoverOrphanedInstructions();

        verify(xaExecutor).recoverOrphanedInstructions();
    }
}
