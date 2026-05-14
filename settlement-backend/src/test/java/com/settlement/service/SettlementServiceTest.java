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
import com.settlement.translation.TranslationService;
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
    private TranslationService translationService;

    @Mock
    private SwiftMessageStrategy mtStrategy;

    private SettlementService settlementService;

    private SettlementRequest validRequest;

    @BeforeEach
    void setUp() {
        settlementService = new SettlementService(
                instructionDao, holdingDao, auditLogDao, swiftMessageDao,
                strategyFactory, canonicalMapper, asyncProcessor, translationService);

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
        when(strategyFactory.getStrategy(MessageStandard.MT)).thenReturn(mtStrategy);
        when(mtStrategy.buildSettlementInstruction(any())).thenReturn("{MT541 content}");
        when(mtStrategy.getOutboundMessageType(any())).thenReturn("MT541");
        when(instructionDao.save(any())).thenAnswer(inv -> {
            SettlementInstruction si = inv.getArgument(0);
            si.setId(1L);
            return si;
        });
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getIsin()).isEqualTo("US0378331005");
        assertThat(result.getDirection()).isEqualTo(Direction.BUY);
        assertThat(result.getStatus()).isEqualTo(InstructionStatus.PENDING);
        assertThat(result.getTradeRef()).startsWith("TR-");
        assertThat(result.getPreferredStandard()).isEqualTo(MessageStandard.MT);

        verify(instructionDao).save(any(SettlementInstruction.class));
        verify(swiftMessageDao, atLeastOnce()).save(any(SwiftMessage.class));
        verify(auditLogDao).save(any());
    }

    @Test
    void submitInstruction_shouldStoreSwiftMessageSeparately() {
        when(strategyFactory.getStrategy(MessageStandard.MT)).thenReturn(mtStrategy);
        when(mtStrategy.buildSettlementInstruction(any())).thenReturn("{MT541 content}");
        when(mtStrategy.getOutboundMessageType(any())).thenReturn("MT541");
        when(instructionDao.save(any())).thenAnswer(inv -> {
            SettlementInstruction si = inv.getArgument(0);
            si.setId(1L);
            return si;
        });
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        settlementService.submitInstruction(validRequest);

        ArgumentCaptor<SwiftMessage> msgCaptor = ArgumentCaptor.forClass(SwiftMessage.class);
        verify(swiftMessageDao, atLeastOnce()).save(msgCaptor.capture());
        SwiftMessage primaryMsg = msgCaptor.getAllValues().getFirst();
        assertThat(primaryMsg.getRawPayload()).isEqualTo("{MT541 content}");
        assertThat(primaryMsg.getMessageType()).isEqualTo("MT541");
        assertThat(primaryMsg.getMessageStandard()).isEqualTo(MessageStandard.MT);
        assertThat(primaryMsg.getDirection()).isEqualTo(MessageDirection.OUTBOUND);
    }

    @Test
    void submitInstruction_shouldTriggerAsyncProcessing() {
        when(strategyFactory.getStrategy(MessageStandard.MT)).thenReturn(mtStrategy);
        when(mtStrategy.buildSettlementInstruction(any())).thenReturn("{MT541}");
        when(mtStrategy.getOutboundMessageType(any())).thenReturn("MT541");
        when(instructionDao.save(any())).thenAnswer(inv -> {
            SettlementInstruction si = inv.getArgument(0);
            si.setId(1L);
            return si;
        });
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        verify(asyncProcessor).processSettlementAsync(result.getTradeRef());
    }

    @Test
    void submitInstruction_shouldGenerateUniqueTradeRef() {
        when(strategyFactory.getStrategy(MessageStandard.MT)).thenReturn(mtStrategy);
        when(mtStrategy.buildSettlementInstruction(any())).thenReturn("{MT541}");
        when(mtStrategy.getOutboundMessageType(any())).thenReturn("MT541");
        when(instructionDao.save(any())).thenAnswer(inv -> {
            SettlementInstruction si = inv.getArgument(0);
            si.setId(1L);
            return si;
        });
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

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

    @Test
    void submitInstruction_shouldStoreDualFormatWhenTranslationSucceeds() {
        com.settlement.translation.TranslationResult translationResult =
                new com.settlement.translation.TranslationResult(
                        MessageStandard.MT, MessageStandard.MX,
                        "MT541", "sese.023.001.09",
                        "<translated-xml/>",
                        new CanonicalSettlement("TR-DUAL", "US0378331005",
                                LocalDate.of(2026, 5, 15), new BigDecimal("1000000.00"),
                                SettlementDirection.RECEIVE, PaymentType.AGAINST_PAYMENT,
                                PartyInfo.ofBic("OWNRBICXXX"), PartyInfo.ofBic("GOLDUS33XXX"),
                                "ACC-001", null, null, null));

        when(strategyFactory.getStrategy(MessageStandard.MT)).thenReturn(mtStrategy);
        when(mtStrategy.buildSettlementInstruction(any())).thenReturn("{MT541 content}");
        when(mtStrategy.getOutboundMessageType(any())).thenReturn("MT541");
        when(instructionDao.save(any())).thenAnswer(inv -> {
            SettlementInstruction si = inv.getArgument(0);
            si.setId(1L);
            return si;
        });
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(translationService.translate("{MT541 content}")).thenReturn(translationResult);

        settlementService.submitInstruction(validRequest);

        ArgumentCaptor<SwiftMessage> msgCaptor = ArgumentCaptor.forClass(SwiftMessage.class);
        verify(swiftMessageDao, times(2)).save(msgCaptor.capture());

        SwiftMessage primary = msgCaptor.getAllValues().get(0);
        assertThat(primary.getMessageStandard()).isEqualTo(MessageStandard.MT);
        assertThat(primary.isTranslated()).isFalse();

        SwiftMessage translated = msgCaptor.getAllValues().get(1);
        assertThat(translated.getMessageStandard()).isEqualTo(MessageStandard.MX);
        assertThat(translated.getMessageType()).isEqualTo("sese.023.001.09");
        assertThat(translated.isTranslated()).isTrue();
        assertThat(translated.getRawPayload()).isEqualTo("<translated-xml/>");
    }

    @Test
    void submitInstruction_shouldNotFailWhenTranslationFails() {
        when(strategyFactory.getStrategy(MessageStandard.MT)).thenReturn(mtStrategy);
        when(mtStrategy.buildSettlementInstruction(any())).thenReturn("{MT541 content}");
        when(mtStrategy.getOutboundMessageType(any())).thenReturn("MT541");
        when(instructionDao.save(any())).thenAnswer(inv -> {
            SettlementInstruction si = inv.getArgument(0);
            si.setId(1L);
            return si;
        });
        when(swiftMessageDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(translationService.translate("{MT541 content}"))
                .thenThrow(new IllegalArgumentException("Parse failed"));

        SettlementInstruction result = settlementService.submitInstruction(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(InstructionStatus.PENDING);
        verify(swiftMessageDao, times(1)).save(any(SwiftMessage.class));
    }
}
