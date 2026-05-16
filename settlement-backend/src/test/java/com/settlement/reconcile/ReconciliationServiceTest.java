package com.settlement.reconcile;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.entity.*;
import com.settlement.service.AlertWebhookService;
import com.settlement.strategy.MtStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private SettlementInstructionDao instructionDao;

    @Mock
    private BondHoldingDao holdingDao;

    @Mock
    private SecurityMovementDao movementDao;

    @Mock
    private AuditLogDao auditLogDao;

    @Mock
    private SwiftMessageDao swiftMessageDao;

    @Mock
    private AlertWebhookService alertService;

    private ReconciliationMetrics metrics;

    private ReconciliationService reconciliationService;

    private SettlementInstruction sampleInstruction;

    private static final String MT548_MATCHED =
            "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
            ":16R:GENL\r\n" +
            ":20C::SEME//TR-TEST123456\r\n" +
            ":23G:INST\r\n" +
            ":16S:GENL\r\n" +
            ":16R:STAT\r\n" +
            ":25D::MTCH//MATC\r\n" +
            ":16S:STAT\r\n" +
            "-}";

    private static final String MT548_REJECTED =
            "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
            ":16R:GENL\r\n" +
            ":20C::SEME//TR-TEST123456\r\n" +
            ":23G:INST\r\n" +
            ":16S:GENL\r\n" +
            ":16R:STAT\r\n" +
            ":25D::MTCH//REJT\r\n" +
            ":16S:STAT\r\n" +
            "-}";

    @BeforeEach
    void setUp() {
        metrics = new ReconciliationMetrics();
        MtStrategy mtStrategy = new MtStrategy();
        SwiftMessageStrategyFactory factory = new SwiftMessageStrategyFactory(List.of(mtStrategy));
        reconciliationService = new ReconciliationService(
                instructionDao, auditLogDao,
                swiftMessageDao, metrics, alertService, factory);

        sampleInstruction = new SettlementInstruction();
        sampleInstruction.setId(1L);
        sampleInstruction.setTradeRef("TR-TEST123456");
        sampleInstruction.setIsin("US0378331005");
        sampleInstruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        sampleInstruction.setQuantity(new BigDecimal("1000000.00"));
        sampleInstruction.setCounterparty("Goldman Sachs");
        sampleInstruction.setBicCode("GOLDUS33XXX");
        sampleInstruction.setDirection(Direction.BUY);
        sampleInstruction.setAccountId("ACC-001");
        sampleInstruction.setStatus(InstructionStatus.SENT);
        sampleInstruction.setPreferredStandard(MessageStandard.MT);
    }

    @Test
    void processSwiftReply_shouldMarkMatchedWithoutFinalPositionMovement() {
        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(swiftMessageDao.nextSequenceNo(anyLong(), anyString(), any())).thenReturn(1);
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED);

        assertThat(sampleInstruction.getStatus()).isEqualTo(InstructionStatus.MATCHED);
        assertThat(sampleInstruction.isFinal()).isFalse();
        assertThat(sampleInstruction.getFinalityTimestamp()).isNull();
        verify(holdingDao, never()).save(any());
        verify(movementDao, never()).save(any());
    }

    @Test
    void processSwiftReply_shouldStoreInboundMessage() {
        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(swiftMessageDao.nextSequenceNo(anyLong(), anyString(), any())).thenReturn(1);
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED);

        ArgumentCaptor<SwiftMessage> msgCaptor = ArgumentCaptor.forClass(SwiftMessage.class);
        verify(swiftMessageDao).save(msgCaptor.capture());
        SwiftMessage saved = msgCaptor.getValue();
        assertThat(saved.getMessageType()).isEqualTo("MT548");
        assertThat(saved.getDirection()).isEqualTo(MessageDirection.INBOUND);
        assertThat(saved.getMessageStandard()).isEqualTo(MessageStandard.MT);
        assertThat(saved.getRawPayload()).isEqualTo(MT548_MATCHED);
    }

    @Test
    void processSwiftReply_shouldMarkFailed_onRejectStatus() {
        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(swiftMessageDao.nextSequenceNo(anyLong(), anyString(), any())).thenReturn(1);
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_REJECTED);

        assertThat(sampleInstruction.getStatus()).isEqualTo(InstructionStatus.FAILED);
        verify(holdingDao, never()).save(any());
    }

    @Test
    void processSwiftReply_shouldLogUnmatched_whenInstructionNotFound() {
        String mt548WithUnknownRef =
                "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
                ":16R:GENL\r\n" +
                ":20C::SEME//TR-UNKNOWN\r\n" +
                ":23G:INST\r\n" +
                ":16S:GENL\r\n" +
                ":16R:STAT\r\n" +
                ":25D::MTCH//MATC\r\n" +
                ":16S:STAT\r\n" +
                "-}";
        when(instructionDao.findByTradeRef("TR-UNKNOWN")).thenReturn(Optional.empty());

        reconciliationService.processSwiftReply("TR-UNKNOWN", mt548WithUnknownRef);

        verify(auditLogDao).save(any(AuditLog.class));
        verify(instructionDao, never()).save(any());
    }

    @Test
    void processSwiftReply_shouldRejectMessage_whenTradeRefCannotBeExtracted() {
        String garbledMessage = "this is not a valid MT548 message at all";

        reconciliationService.processSwiftReply("CORR-ID-123", garbledMessage);

        verify(instructionDao, never()).findByTradeRef(anyString());
        verify(instructionDao, never()).save(any());
        verify(holdingDao, never()).save(any());

        verify(auditLogDao).save(any(AuditLog.class));
        assertThat(metrics.getUnmatchedCount()).isEqualTo(1);
        verify(alertService).sendUnknownStatusAlert("CORR-ID-123", null);
    }

    @Test
    void processSwiftReply_shouldAuditUnknownStatus_whenMt548StatusUnparseable() {
        String mt548WithBadStatus =
                "{1:F01BANKUS33AXXX0000000000}{2:O5481200260515GOLDUS33AXXX00000000002605150000N}{4:\r\n" +
                ":16R:GENL\r\n" +
                ":20C::SEME//TR-TEST123456\r\n" +
                ":23G:INST\r\n" +
                ":16S:GENL\r\n" +
                "-}";

        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(swiftMessageDao.nextSequenceNo(anyLong(), anyString(), any())).thenReturn(1);
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", mt548WithBadStatus);

        assertThat(sampleInstruction.getStatus()).isEqualTo(InstructionStatus.SENT);
        verify(holdingDao, never()).save(any());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao, atLeastOnce()).save(auditCaptor.capture());
        boolean hasUnknownEvent = auditCaptor.getAllValues().stream()
                .anyMatch(a -> a.getEventType() == AuditEventType.SETTLEMENT_STATUS_UNKNOWN);
        assertThat(hasUnknownEvent).isTrue();

        assertThat(metrics.getUnknownCount()).isEqualTo(1);
        verify(alertService).sendUnknownStatusAlert("TR-TEST123456", "US0378331005");
    }

    @Test
    void processSwiftReply_shouldRecordMetrics_onMatch() {
        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(swiftMessageDao.nextSequenceNo(anyLong(), anyString(), any())).thenReturn(1);
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED);

        java.util.Map<String, Object> snap = metrics.snapshot();
        assertThat(snap.get("totalProcessed")).isEqualTo(1L);
        assertThat(snap.get("totalMatched")).isEqualTo(1L);
        assertThat(snap.get("totalUnknown")).isEqualTo(0L);
    }
}
