package com.settlement.service;

import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.NonRetryableSettlementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.*;

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
        ScheduledExecutorService immediateScheduler = new ImmediateScheduledExecutor();
        processor = new AsyncSettlementProcessor(
                xaExecutor, alertWebhookService, Runnable::run, immediateScheduler);
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
    void processSettlementAsync_shouldNotRetry_onNonRetryableException() {
        doThrow(new NonRetryableSettlementException("No outbound message"))
                .when(xaExecutor).executeSettlement("TR-ASYNC005");

        SettlementInstruction failedInstr = new SettlementInstruction();
        failedInstr.setTradeRef("TR-ASYNC005");
        failedInstr.setStatus(InstructionStatus.FAILED);
        failedInstr.setFailureReason("No outbound message");
        when(xaExecutor.findByTradeRef("TR-ASYNC005")).thenReturn(failedInstr);

        processor.processSettlementAsync("TR-ASYNC005");

        verify(xaExecutor, times(1)).executeSettlement("TR-ASYNC005");
        verify(xaExecutor).recordFailure(eq("TR-ASYNC005"), eq(1), any(), eq(true), eq(3));
        verify(alertWebhookService).sendExhaustedAlert(failedInstr);
    }

    @Test
    void recoverOrphanedInstructions_shouldDelegateToXaExecutor() {
        when(xaExecutor.recoverOrphanedInstructions())
                .thenReturn(java.util.List.of("TR-ORPHAN1", "TR-ORPHAN2"));
        doNothing().when(xaExecutor).executeSettlement(anyString());

        processor.recoverOrphanedInstructions();

        verify(xaExecutor).recoverOrphanedInstructions();
        verify(xaExecutor).executeSettlement("TR-ORPHAN1");
        verify(xaExecutor).executeSettlement("TR-ORPHAN2");
    }

    /**
     * A ScheduledExecutorService that runs tasks immediately (no delay)
     * for deterministic unit testing.
     */
    private static class ImmediateScheduledExecutor extends AbstractExecutorService
            implements ScheduledExecutorService {

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            command.run();
            return null;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            try { callable.call(); } catch (Exception ignored) {}
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override public void shutdown() {}
        @Override public java.util.List<Runnable> shutdownNow() { return java.util.List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { command.run(); }
    }
}
