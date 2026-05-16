package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.*;
import com.settlement.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DvpSettlementServiceTest {

    @Mock
    private SettlementInstructionDao instructionDao;

    @Mock
    private ChatsGateway chatsGateway;

    @Mock
    private AuditLogDao auditLogDao;

    @Mock
    private BondHoldingDao holdingDao;

    @Mock
    private SecurityMovementDao movementDao;

    private DvpSettlementService dvpService;

    @BeforeEach
    void setUp() {
        dvpService = new DvpSettlementService(instructionDao, chatsGateway, auditLogDao, holdingDao, movementDao);
    }

    @Test
    void lockForDvp_shouldReserveFundsAndSetDvpLocked_forBuy() {
        SettlementInstruction instruction = createBuyInstruction();
        when(chatsGateway.reserveFunds("ACC-001", "HKD", new BigDecimal("5000000.00"), "TR-DVP001"))
                .thenReturn(true);
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dvpService.lockForDvp(instruction);

        assertThat(instruction.getStatus()).isEqualTo(InstructionStatus.DVP_LOCKED);
        verify(chatsGateway).reserveFunds("ACC-001", "HKD", new BigDecimal("5000000.00"), "TR-DVP001");
        verify(auditLogDao).save(any(AuditLog.class));
    }

    @Test
    void lockForDvp_shouldFail_whenInsufficientFunds() {
        SettlementInstruction instruction = createBuyInstruction();
        when(chatsGateway.reserveFunds(any(), any(), any(), any())).thenReturn(false);
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> dvpService.lockForDvp(instruction))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("insufficient funds");

        assertThat(instruction.getStatus()).isEqualTo(InstructionStatus.FAILED);
    }

    @Test
    void lockForDvp_shouldSkip_forFopInstruction() {
        SettlementInstruction instruction = createBuyInstruction();
        instruction.setPaymentType("FREE_OF_PAYMENT");

        dvpService.lockForDvp(instruction);

        verify(chatsGateway, never()).reserveFunds(any(), any(), any(), any());
    }

    @Test
    void lockForDvp_shouldReject_finalizedInstruction() {
        SettlementInstruction instruction = createBuyInstruction();
        instruction.setFinal(true);

        assertThatThrownBy(() -> dvpService.lockForDvp(instruction))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("finalized");
    }

    @Test
    void completeDvp_shouldSetMatchedWithFinalityAndUpdateHoldings() {
        SettlementInstruction instruction = createBuyInstruction();
        instruction.setStatus(InstructionStatus.DVP_LOCKED);
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        BondHolding sellerHolding = new BondHolding();
        sellerHolding.setAccountId("GOLDUS33XXX");
        sellerHolding.setIsin("US0378331005");
        sellerHolding.setQuantity(new BigDecimal("2000000.00"));
        when(holdingDao.findByAccountAndIsinForUpdate("GOLDUS33XXX", "US0378331005"))
                .thenReturn(Optional.of(sellerHolding));
        when(holdingDao.findByAccountAndIsinForUpdate("ACC-001", "US0378331005"))
                .thenReturn(Optional.empty());
        when(holdingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dvpService.completeDvp(instruction);

        assertThat(instruction.getStatus()).isEqualTo(InstructionStatus.MATCHED);
        assertThat(instruction.isFinal()).isTrue();
        assertThat(instruction.getFinalityTimestamp()).isNotNull();
        verify(holdingDao, times(2)).save(any(BondHolding.class));
        verify(movementDao, times(2)).save(any(SecurityMovement.class));
        verify(chatsGateway).releaseFunds("GOLDUS33XXX", "HKD", new BigDecimal("5000000.00"), "TR-DVP001");
    }

    @Test
    void completeDvp_shouldRejectUnlessLocked() {
        SettlementInstruction instruction = createBuyInstruction();

        assertThatThrownBy(() -> dvpService.completeDvp(instruction))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DVP_LOCKED");

        verify(holdingDao, never()).save(any());
    }

    @Test
    void rollbackDvp_shouldReleaseFundsAndSetFailed() {
        SettlementInstruction instruction = createBuyInstruction();
        instruction.setStatus(InstructionStatus.DVP_LOCKED);
        when(instructionDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        dvpService.rollbackDvp(instruction);

        assertThat(instruction.getStatus()).isEqualTo(InstructionStatus.FAILED);
        verify(chatsGateway).releaseFunds("ACC-001", "HKD", new BigDecimal("5000000.00"), "TR-DVP001");
    }

    @Test
    void rollbackDvp_shouldDoNothing_whenNotDvpLocked() {
        SettlementInstruction instruction = createBuyInstruction();
        instruction.setStatus(InstructionStatus.SENT);

        dvpService.rollbackDvp(instruction);

        verify(chatsGateway, never()).releaseFunds(any(), any(), any(), any());
        verify(instructionDao, never()).save(any());
    }

    private SettlementInstruction createBuyInstruction() {
        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setId(1L);
        instruction.setTradeRef("TR-DVP001");
        instruction.setIsin("US0378331005");
        instruction.setSettlementDate(LocalDate.of(2026, 5, 15));
        instruction.setQuantity(new BigDecimal("1000000.00"));
        instruction.setCounterparty("Goldman Sachs");
        instruction.setBicCode("GOLDUS33XXX");
        instruction.setDirection(Direction.BUY);
        instruction.setStatus(InstructionStatus.SENT);
        instruction.setAccountId("ACC-001");
        instruction.setCurrency("HKD");
        instruction.setPaymentType("AGAINST_PAYMENT");
        instruction.setSettlementAmount(new BigDecimal("5000000.00"));
        return instruction;
    }
}
