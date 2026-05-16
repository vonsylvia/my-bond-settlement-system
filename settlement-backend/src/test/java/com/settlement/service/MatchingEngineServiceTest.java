package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.MatchingInstructionDao;
import com.settlement.entity.*;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchingEngineServiceTest {

    @Mock
    private MatchingInstructionDao matchingDao;

    @Mock
    private AuditLogDao auditLogDao;

    private MatchingEngineService matchingEngine;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngineService(matchingDao, auditLogDao);
    }

    @Test
    void submitForMatching_shouldSetAlleged_whenNoCounterpartyFound() {
        MatchingInstruction buy = createBuyInstruction();
        when(matchingDao.save(any())).thenAnswer(inv -> {
            MatchingInstruction mi = inv.getArgument(0);
            mi.setId(1L);
            return mi;
        });
        when(matchingDao.findUnmatchedCandidates(any(), any(), any(), any()))
                .thenReturn(List.of());

        MatchingInstruction result = matchingEngine.submitForMatching(buy);

        assertThat(result.getMatchingStatus()).isEqualTo(MatchingStatus.ALLEGED);
        verify(auditLogDao).save(any(AuditLog.class));
    }

    @Test
    void submitForMatching_shouldMatch_whenCounterpartyExists() {
        MatchingInstruction buy = createBuyInstruction();
        buy.setId(1L);

        MatchingInstruction sell = createSellInstruction();
        sell.setId(2L);

        when(matchingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(matchingDao.findUnmatchedCandidates(
                eq("US0378331005"),
                eq(LocalDate.of(2026, 5, 15)),
                eq(Direction.SELL),
                eq("GOLDUS33XXX")))
                .thenReturn(List.of(sell));

        MatchingInstruction result = matchingEngine.submitForMatching(buy);

        assertThat(result.getMatchingStatus()).isEqualTo(MatchingStatus.MATCHED);
        assertThat(result.getMatchedWithId()).isEqualTo(2L);
        assertThat(sell.getMatchingStatus()).isEqualTo(MatchingStatus.MATCHED);
        assertThat(sell.getMatchedWithId()).isEqualTo(1L);

        verify(auditLogDao, times(2)).save(any(AuditLog.class));
    }

    @Test
    void submitForMatching_shouldNotMatch_whenQuantityDiffers() {
        MatchingInstruction buy = createBuyInstruction();
        buy.setId(1L);

        MatchingInstruction sell = createSellInstruction();
        sell.setId(2L);
        sell.setQuantity(new BigDecimal("500000.00"));

        when(matchingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(matchingDao.findUnmatchedCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(sell));

        MatchingInstruction result = matchingEngine.submitForMatching(buy);

        assertThat(result.getMatchingStatus()).isEqualTo(MatchingStatus.ALLEGED);
    }

    @Test
    void submitForMatching_shouldMatch_whenAmountWithinTolerance() {
        MatchingInstruction buy = createBuyInstruction();
        buy.setId(1L);
        buy.setAmount(new BigDecimal("50000.00"));

        MatchingInstruction sell = createSellInstruction();
        sell.setId(2L);
        sell.setAmount(new BigDecimal("50001.50"));

        when(matchingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(matchingDao.findUnmatchedCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(sell));

        MatchingInstruction result = matchingEngine.submitForMatching(buy);

        assertThat(result.getMatchingStatus()).isEqualTo(MatchingStatus.MATCHED);
    }

    @Test
    void submitForMatching_shouldNotMatch_whenAmountExceedsTolerance() {
        MatchingInstruction buy = createBuyInstruction();
        buy.setId(1L);
        buy.setAmount(new BigDecimal("50000.00"));

        MatchingInstruction sell = createSellInstruction();
        sell.setId(2L);
        sell.setAmount(new BigDecimal("50003.00"));

        when(matchingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(matchingDao.findUnmatchedCandidates(any(), any(), any(), any()))
                .thenReturn(List.of(sell));

        MatchingInstruction result = matchingEngine.submitForMatching(buy);

        assertThat(result.getMatchingStatus()).isEqualTo(MatchingStatus.ALLEGED);
    }

    @Test
    void cancelMatchingInstructionEntity_shouldUnmatchCounterparty() {
        MatchingInstruction buy = createBuyInstruction();
        buy.setId(1L);
        buy.setMatchingStatus(MatchingStatus.MATCHED);
        buy.setMatchedWithId(2L);

        MatchingInstruction sell = createSellInstruction();
        sell.setId(2L);
        sell.setMatchingStatus(MatchingStatus.MATCHED);
        sell.setMatchedWithId(1L);

        when(matchingDao.findById(1L)).thenReturn(Optional.of(buy));
        when(matchingDao.findById(2L)).thenReturn(Optional.of(sell));
        when(matchingDao.save(any())).thenAnswer(inv -> inv.getArgument(0));

        matchingEngine.cancelMatchingInstruction(1L);

        assertThat(buy.getMatchingStatus()).isEqualTo(MatchingStatus.CANCELLED);
        assertThat(sell.getMatchingStatus()).isEqualTo(MatchingStatus.UNMATCHED);
        assertThat(sell.getMatchedWithId()).isNull();
    }

    private MatchingInstruction createBuyInstruction() {
        MatchingInstruction mi = new MatchingInstruction();
        mi.setTradeRef("TR-BUY001");
        mi.setIsin("US0378331005");
        mi.setSettlementDate(LocalDate.of(2026, 5, 15));
        mi.setQuantity(new BigDecimal("1000000.00"));
        mi.setCounterpartyBic("GOLDUS33XXX");
        mi.setSubmitterBic("HSBCHKHHXXX");
        mi.setDirection(Direction.BUY);
        return mi;
    }

    private MatchingInstruction createSellInstruction() {
        MatchingInstruction mi = new MatchingInstruction();
        mi.setTradeRef("TR-SELL001");
        mi.setIsin("US0378331005");
        mi.setSettlementDate(LocalDate.of(2026, 5, 15));
        mi.setQuantity(new BigDecimal("1000000.00"));
        mi.setCounterpartyBic("HSBCHKHHXXX");
        mi.setSubmitterBic("GOLDUS33XXX");
        mi.setDirection(Direction.SELL);
        return mi;
    }
}
