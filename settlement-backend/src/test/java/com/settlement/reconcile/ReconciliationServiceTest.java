package com.settlement.reconcile;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.*;
import com.settlement.service.AlertWebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private SettlementInstructionDao instructionDao;

    @Mock
    private BondHoldingDao holdingDao;

    @Mock
    private AuditLogDao auditLogDao;

    @Mock
    private AlertWebhookService alertService;

    private ReconciliationMetrics metrics;

    private ReconciliationService reconciliationService;

    private SettlementInstruction sampleInstruction;

    // Valid SWIFT MT548 with MATC status in standard FIN format
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
        reconciliationService = new ReconciliationService(instructionDao, holdingDao, auditLogDao, metrics, alertService);

        sampleInstruction = new SettlementInstruction();
        sampleInstruction.setTradeRef("TR-TEST123456");
        sampleInstruction.setIsin("US0378331005");
        sampleInstruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        sampleInstruction.setQuantity(new BigDecimal("1000000.00"));
        sampleInstruction.setCounterparty("Goldman Sachs");
        sampleInstruction.setBicCode("GOLDUS33XXX");
        sampleInstruction.setDirection(Direction.BUY);
        sampleInstruction.setAccountId("ACC-001");
        sampleInstruction.setStatus(InstructionStatus.SENT);
    }

    @Test
    void processSwiftReply_shouldMatchAndUpdateHoldings_forBuy() {
        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005")).thenReturn(Optional.empty());
        when(holdingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED);

        assertThat(sampleInstruction.getStatus()).isEqualTo(InstructionStatus.MATCHED);

        ArgumentCaptor<BondHolding> holdingCaptor = ArgumentCaptor.forClass(BondHolding.class);
        verify(holdingDao).save(holdingCaptor.capture());
        BondHolding savedHolding = holdingCaptor.getValue();
        assertThat(savedHolding.getQuantity()).isEqualByComparingTo("1000000.00");
        assertThat(savedHolding.getAccountId()).isEqualTo("ACC-001");
        assertThat(savedHolding.getIsin()).isEqualTo("US0378331005");
    }

    @Test
    void processSwiftReply_shouldAddToExistingHoldings_forBuy() {
        BondHolding existingHolding = new BondHolding();
        existingHolding.setAccountId("ACC-001");
        existingHolding.setIsin("US0378331005");
        existingHolding.setQuantity(new BigDecimal("500000.00"));

        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005")).thenReturn(Optional.of(existingHolding));
        when(holdingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED);

        ArgumentCaptor<BondHolding> captor = ArgumentCaptor.forClass(BondHolding.class);
        verify(holdingDao).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("1500000.00");
    }

    @Test
    void processSwiftReply_shouldSubtractHoldings_forSell() {
        sampleInstruction.setDirection(Direction.SELL);
        sampleInstruction.setQuantity(new BigDecimal("300000.00"));

        BondHolding existingHolding = new BondHolding();
        existingHolding.setAccountId("ACC-001");
        existingHolding.setIsin("US0378331005");
        existingHolding.setQuantity(new BigDecimal("500000.00"));

        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005")).thenReturn(Optional.of(existingHolding));
        when(holdingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED);

        ArgumentCaptor<BondHolding> captor = ArgumentCaptor.forClass(BondHolding.class);
        verify(holdingDao).save(captor.capture());
        assertThat(captor.getValue().getQuantity()).isEqualByComparingTo("200000.00");
    }

    @Test
    void processSwiftReply_shouldFailOnInsufficientHoldings_forSell() {
        sampleInstruction.setDirection(Direction.SELL);
        sampleInstruction.setQuantity(new BigDecimal("600000.00"));

        BondHolding existingHolding = new BondHolding();
        existingHolding.setAccountId("ACC-001");
        existingHolding.setIsin("US0378331005");
        existingHolding.setQuantity(new BigDecimal("500000.00"));

        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005")).thenReturn(Optional.of(existingHolding));

        assertThatThrownBy(() -> reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient");
    }

    @Test
    void processSwiftReply_shouldMarkFailed_onRejectStatus() {
        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_REJECTED);

        assertThat(sampleInstruction.getStatus()).isEqualTo(InstructionStatus.FAILED);
        verify(holdingDao, never()).save(any());
    }

    @Test
    void processSwiftReply_shouldLogUnmatched_whenInstructionNotFound() {
        when(instructionDao.findByTradeRef("TR-UNKNOWN")).thenReturn(Optional.empty());

        reconciliationService.processSwiftReply("TR-UNKNOWN", "some raw message");

        verify(auditLogDao).save(any(AuditLog.class));
        verify(instructionDao, never()).save(any());
    }

    @Test
    void sanitiseSwiftMessage_shouldNormaliseLineEndings() {
        String onlyLf = "{4:\n:20C::SEME//REF1\n:25D::MTCH//MATC\n-}";
        String result = ReconciliationService.sanitiseSwiftMessage(onlyLf);
        assertThat(result).doesNotContain("\n\n");
        assertThat(result).contains("\r\n");
    }

    @Test
    void sanitiseSwiftMessage_shouldStripBomAndNulBytes() {
        String withBomAndNul = "\uFEFF{4:\r\n:20C::SEME//REF1\0\r\n-}";
        String result = ReconciliationService.sanitiseSwiftMessage(withBomAndNul);
        assertThat(result).doesNotContain("\uFEFF");
        assertThat(result).doesNotContain("\0");
        assertThat(result).startsWith("{4:");
    }

    @Test
    void sanitiseSwiftMessage_shouldTrimTrailingSpaces() {
        String padded = ":20C::SEME//REF1   \r\n:25D::MTCH//MATC  \r\n";
        String result = ReconciliationService.sanitiseSwiftMessage(padded);
        assertThat(result).doesNotContain("   \r\n");
        assertThat(result).contains(":20C::SEME//REF1\r\n");
    }

    @Test
    void sanitiseSwiftMessage_shouldHandleNullAndEmpty() {
        assertThat(ReconciliationService.sanitiseSwiftMessage(null)).isNull();
        assertThat(ReconciliationService.sanitiseSwiftMessage("")).isEmpty();
    }

    @Test
    void processSwiftReply_shouldAuditUnknownStatus_whenMt548Unparseable() {
        String garbledMessage = "this is not a valid MT548 message at all";

        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", garbledMessage);

        assertThat(sampleInstruction.getStatus()).isEqualTo(InstructionStatus.SENT);
        verify(holdingDao, never()).save(any());

        ArgumentCaptor<AuditLog> auditCaptor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogDao).save(auditCaptor.capture());
        AuditLog auditLog = auditCaptor.getValue();
        assertThat(auditLog.getEventType()).isEqualTo(AuditEventType.SETTLEMENT_STATUS_UNKNOWN);
        assertThat(auditLog.getDetail()).contains("manual review");

        assertThat(metrics.getUnknownCount()).isEqualTo(1);
        verify(alertService).sendUnknownStatusAlert("TR-TEST123456", "US0378331005");
    }

    @Test
    void processSwiftReply_shouldRecordMetrics_onMatch() {
        when(instructionDao.findByTradeRef("TR-TEST123456")).thenReturn(Optional.of(sampleInstruction));
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005")).thenReturn(Optional.empty());
        when(holdingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reconciliationService.processSwiftReply("TR-TEST123456", MT548_MATCHED);

        Map<String, Object> snap = metrics.snapshot();
        assertThat(snap.get("totalProcessed")).isEqualTo(1L);
        assertThat(snap.get("totalMatched")).isEqualTo(1L);
        assertThat(snap.get("totalUnknown")).isEqualTo(0L);
    }
}
