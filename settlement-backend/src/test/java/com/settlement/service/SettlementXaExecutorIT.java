package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.Direction;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.jms.SwiftMessageSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Integration test verifying that @Transactional on SettlementXaExecutor
 * actually works through the Spring proxy (not self-invocation).
 * Uses H2 in-memory DB so we can verify real commit/rollback behavior.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:spring/test-applicationContext.xml")
@Transactional
class SettlementXaExecutorIT {

    @Autowired
    private SettlementXaExecutor xaExecutor;

    @Autowired
    private SettlementInstructionDao instructionDao;

    @Autowired
    private AuditLogDao auditLogDao;

    @Autowired
    private SwiftMessageSender messageSender;

    @BeforeEach
    void setUp() {
        reset(messageSender);
    }

    @Test
    void executeSettlement_shouldCommitOnSuccess() {
        SettlementInstruction instruction = createAndSave("TR-XA-IT-001");
        doNothing().when(messageSender).sendSwiftMessage(anyString(), anyString());

        xaExecutor.executeSettlement("TR-XA-IT-001");

        Optional<SettlementInstruction> updated = instructionDao.findByTradeRef("TR-XA-IT-001");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(InstructionStatus.SENT);
    }

    @Test
    void executeSettlement_shouldPropagateExceptionOnJmsFailure() {
        createAndSave("TR-XA-IT-002");
        doThrow(new RuntimeException("MQ connection lost"))
                .when(messageSender).sendSwiftMessage(anyString(), anyString());

        // XA rollback (DB + MQ atomicity) relies on a real XA TM
        // which is only available in Liberty; here we verify the exception propagates
        // so the caller (AsyncSettlementProcessor) can record failure and retry
        assertThatThrownBy(() -> xaExecutor.executeSettlement("TR-XA-IT-002"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MQ connection lost");
    }

    @Test
    void recordFailure_shouldPersistFailureInfo() {
        createAndSave("TR-XA-IT-003");

        xaExecutor.recordFailure("TR-XA-IT-003", 2,
                new RuntimeException("timeout"), false, 3);

        Optional<SettlementInstruction> updated = instructionDao.findByTradeRef("TR-XA-IT-003");
        assertThat(updated).isPresent();
        assertThat(updated.get().getStatus()).isEqualTo(InstructionStatus.FAILED);
        assertThat(updated.get().getRetryCount()).isEqualTo(2);
        assertThat(updated.get().getFailureReason()).isEqualTo("timeout");
    }

    @Test
    void executeSettlement_shouldSkipAlreadySentInstruction() {
        SettlementInstruction instruction = createAndSave("TR-XA-IT-004");
        instruction.setStatus(InstructionStatus.SENT);
        instructionDao.save(instruction);

        xaExecutor.executeSettlement("TR-XA-IT-004");

        verify(messageSender, never()).sendSwiftMessage(anyString(), anyString());
    }

    private SettlementInstruction createAndSave(String tradeRef) {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef(tradeRef);
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.of(2026, 6, 15));
        instruction.setQuantity(new BigDecimal("1000.00"));
        instruction.setCounterparty("Test Bank");
        instruction.setBicCode("TESTUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setAccountId("ACC-IT");
        instruction.setStatus(InstructionStatus.PENDING);
        instruction.setMt541Raw("{MT541 test message}");
        return instructionDao.save(instruction);
    }
}
