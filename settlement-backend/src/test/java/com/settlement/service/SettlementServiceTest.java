package com.settlement.service;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.PartyInfo;
import com.settlement.canonical.PaymentType;
import com.settlement.canonical.SettlementDirection;
import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.dto.SettlementRequest;
import com.settlement.entity.*;
import com.settlement.exception.BusinessException;
import com.settlement.exception.ResourceNotFoundException;
import com.settlement.strategy.CanonicalMapper;
import com.settlement.strategy.SwiftMessageStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementInstructionDao instructionDao;

    @Mock
    private BondHoldingDao holdingDao;

    @Mock
    private AuditLogDao auditLogDao;

    @Mock
    private SwiftMessageDao swiftMessageDao;

    @Mock
    private SwiftMessageStrategyFactory strategyFactory;

    @Mock
    private CanonicalMapper canonicalMapper;

    @Mock
    private AsyncSettlementProcessor asyncProcessor;

    @Mock
    private SwiftMessageStrategy mtStrategy;

    private SettlementService settlementService;

    private SettlementRequest validRequest;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
                instructionDao, holdingDao, auditLogDao, swiftMessageDao,
                strategyFactory, canonicalMapper, asyncProcessor);

        validRequest = new SettlementRequest();
        validRequest.setIsin("US0378331005");
        validRequest.setSettlementDate(LocalDate.of(2026, 5, 15));
        validRequest.setQuantity(new BigDecimal("1000000.00"));
        validRequest.setCounterparty("Goldman Sachs");
        validRequest.setBicCode("GOLDUS33XXX");
        validRequest.setDirection("BUY");
        validRequest.setAccountId("ACC-001");

        CanonicalSettlement dummyCanonical = new CanonicalSettlement(
                "TR-DUMMY", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33XXX"),
                "ACC-001", null, null, null);
        lenient().when(canonicalMapper.toCanonical(any())).thenReturn(dummyCanonical);
    }

    @Test
    void submitInstruction_shouldPersistWithPendingStatus() {
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getIsin()).isEqualTo("US0378331005");
        assertThat(result.getSettlementDate()).isEqualTo(LocalDate.of(2026, 5, 15));
        assertThat(result.getQuantity()).isEqualByComparingTo("1000000.00");
        assertThat(result.getCounterparty()).isEqualTo("Goldman Sachs");
        assertThat(result.getBicCode()).isEqualTo("GOLDUS33XXX");
        assertThat(result.getDirection()).isEqualTo(Direction.BUY);
        assertThat(result.getAccountId()).isEqualTo("ACC-001");
        assertThat(result.getStatus()).isEqualTo(InstructionStatus.PENDING);
        assertThat(result.getTradeRef()).startsWith("TR-");
        assertThat(result.getRequestedStandard()).isEqualTo(MessageStandard.MT);
        assertThat(result.getCurrency()).isEqualTo("HKD");
        assertThat(result.getPaymentType()).isEqualTo(PaymentType.AGAINST_PAYMENT.name());

        verify(instructionDao).save(result);
    }

    @Test
    void submitInstruction_shouldPassMappedCanonicalSettlementToStrategy() {
        CanonicalSettlement canonical = new CanonicalSettlement(
                "TR-CANONICAL", "US0378331005", LocalDate.of(2026, 5, 15),
                new BigDecimal("1000000.00"), SettlementDirection.RECEIVE,
                PaymentType.AGAINST_PAYMENT,
                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33XXX"),
                "ACC-001", "CASH-001", "XHKG", "HKICL");
        when(canonicalMapper.toCanonical(any())).thenReturn(canonical);
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        settlementService.submitInstruction(validRequest);

        ArgumentCaptor<SettlementInstruction> instructionCaptor =
                ArgumentCaptor.forClass(SettlementInstruction.class);
        verify(canonicalMapper).toCanonical(instructionCaptor.capture());
        SettlementInstruction mappedInstruction = instructionCaptor.getValue();
        assertThat(mappedInstruction.getId()).isEqualTo(1L);
        assertThat(mappedInstruction.getTradeRef()).startsWith("TR-");
        assertThat(mappedInstruction.getIsin()).isEqualTo("US0378331005");
        assertThat(mappedInstruction.getDirection()).isEqualTo(Direction.BUY);
        assertThat(mappedInstruction.getRequestedStandard()).isEqualTo(MessageStandard.MT);

        verify(mtStrategy).buildSettlementInstruction(canonical);
        verify(mtStrategy).getOutboundMessageType(canonical);
    }

    @Test
    void submitInstruction_shouldStoreSwiftMessageWithInstructionReferenceAndPayload() {
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        ArgumentCaptor<SwiftMessage> msgCaptor = ArgumentCaptor.forClass(SwiftMessage.class);
        verify(swiftMessageDao).save(msgCaptor.capture());
        SwiftMessage primaryMsg = msgCaptor.getValue();
        assertThat(primaryMsg.getInstructionId()).isEqualTo(result.getId());
        assertThat(primaryMsg.getTradeRef()).isEqualTo(result.getTradeRef());
        assertThat(primaryMsg.getRawPayload()).isEqualTo("{MT541 content}");
        assertThat(primaryMsg.getMessageType()).isEqualTo("MT541");
        assertThat(primaryMsg.getMessageStandard()).isEqualTo(MessageStandard.MT);
        assertThat(primaryMsg.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
        assertThat(primaryMsg.getSequenceNo()).isEqualTo(1);
        assertThat(primaryMsg.isTranslated()).isFalse();
    }

    @Test
    void submitInstruction_shouldWriteInstructionCreatedAuditLog() {
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getTradeRef()).isEqualTo(result.getTradeRef());
        assertThat(auditLog.getEventType()).isEqualTo(AuditEventType.INSTRUCTION_CREATED);
        assertThat(auditLog.getDetail())
                .contains("Instruction saved, async XA processing queued")
                .contains("ISIN=US0378331005")
                .contains("QTY=1000000.00");
    }

    @Test
    void submitInstruction_shouldTriggerAsyncProcessing() {
        mockSuccessfulMtSubmission("{MT541}", "MT541");

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        verify(asyncProcessor).processSettlementAsync(result.getTradeRef());
    }

    @Test
    void submitInstruction_shouldGenerateUniqueTradeRef() {
        mockSuccessfulMtSubmission("{MT541}", "MT541");

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
        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getTradeRef()).isEqualTo("TR-RETRY001");
        assertThat(auditLog.getEventType()).isEqualTo(AuditEventType.MANUAL_RETRY);
        assertThat(auditLog.getDetail()).contains("Manual retry triggered");
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

    @Test
    void submitInstruction_shouldStoreOnlySelectedMessageStandard() {
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(InstructionStatus.PENDING);
        verify(swiftMessageDao, times(1)).save(any(SwiftMessage.class));
    }

    @Test
    void submitOpenApiInstruction_shouldStoreParticipantReferenceAndHash() {
        when(instructionDao.findByParticipantAndClientReference("BANKA", "BANKA-REF-001"))
                .thenReturn(Optional.empty());
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        SettlementInstruction result = settlementService.submitOpenApiInstruction(
                "BANKA", "BANKA-REF-001", validRequest);

        assertThat(result.getParticipantId()).isEqualTo("BANKA");
        assertThat(result.getClientReference()).isEqualTo("BANKA-REF-001");
        assertThat(result.getOpenApiRequestHash()).hasSize(64);
        verify(instructionDao).save(result);
        verify(asyncProcessor).processSettlementAsync(result.getTradeRef());
    }

    @Test
    void submitOpenApiInstruction_shouldWriteAuditLogWithParticipantContext() {
        when(instructionDao.findByParticipantAndClientReference("BANKA", "BANKA-REF-001"))
                .thenReturn(Optional.empty());
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        SettlementInstruction result = settlementService.submitOpenApiInstruction(
                " BANKA ", " BANKA-REF-001 ", validRequest);

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getTradeRef()).isEqualTo(result.getTradeRef());
        assertThat(auditLog.getEventType()).isEqualTo(AuditEventType.INSTRUCTION_CREATED);
        assertThat(auditLog.getDetail())
                .contains("Open API participantId=BANKA clientReference=BANKA-REF-001")
                .contains("Instruction saved, async XA processing queued");
    }

    @Test
    void submitOpenApiInstruction_shouldReturnExistingInstruction_forIdempotentReplay() {
        SettlementInstruction existing = new SettlementInstruction();
        existing.setTradeRef("TR-EXISTING");
        existing.setParticipantId("BANKA");
        existing.setClientReference("BANKA-REF-001");

        when(instructionDao.findByParticipantAndClientReference("BANKA", "BANKA-REF-001"))
                .thenReturn(Optional.empty());
        mockSuccessfulMtSubmission("{MT541 content}", "MT541");

        SettlementInstruction created = settlementService.submitOpenApiInstruction(
                "BANKA", "BANKA-REF-001", validRequest);
        existing.setOpenApiRequestHash(created.getOpenApiRequestHash());

        when(instructionDao.findByParticipantAndClientReference("BANKA", "BANKA-REF-001"))
                .thenReturn(Optional.of(existing));

        SettlementInstruction replay = settlementService.submitOpenApiInstruction(
                "BANKA", "BANKA-REF-001", validRequest);

        assertThat(replay).isSameAs(existing);
        verify(instructionDao, times(1)).save(any(SettlementInstruction.class));
    }

    @Test
    void submitOpenApiInstruction_shouldRejectSameReferenceWithDifferentPayload() {
        SettlementInstruction existing = new SettlementInstruction();
        existing.setTradeRef("TR-EXISTING");
        existing.setParticipantId("BANKA");
        existing.setClientReference("BANKA-REF-001");
        existing.setOpenApiRequestHash("different");
        when(instructionDao.findByParticipantAndClientReference("BANKA", "BANKA-REF-001"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> settlementService.submitOpenApiInstruction(
                "BANKA", "BANKA-REF-001", validRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Duplicate clientReference");
    }

    private void mockSuccessfulMtSubmission(String rawPayload, String messageType) {
        when(strategyFactory.getStrategy(MessageStandard.MT)).thenReturn(mtStrategy);
        when(mtStrategy.buildSettlementInstruction(any(CanonicalSettlement.class))).thenReturn(rawPayload);
        when(mtStrategy.getOutboundMessageType(any(CanonicalSettlement.class))).thenReturn(messageType);
        when(instructionDao.save(any(SettlementInstruction.class))).thenAnswer(inv -> {
            SettlementInstruction si = inv.getArgument(0);
            si.setId(1L);
            return si;
        });
        when(swiftMessageDao.save(any(SwiftMessage.class))).thenAnswer(inv -> inv.getArgument(0));
    }
}
