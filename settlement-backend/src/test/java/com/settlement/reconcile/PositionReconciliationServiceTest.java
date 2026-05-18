package com.settlement.reconcile;

import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.EodPositionSnapshotDao;
import com.settlement.dao.ReconciliationSnapshotDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dto.ReconciliationResult;
import com.settlement.entity.BondHolding;
import com.settlement.entity.EodPositionSnapshot;
import com.settlement.entity.ReconciliationSnapshot;
import com.settlement.entity.SnapshotType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionReconciliationServiceTest {

    @Mock
    private BondHoldingDao holdingDao;

    @Mock
    private SecurityMovementDao movementDao;

    @Mock
    private EodPositionSnapshotDao eodSnapshotDao;

    @Mock
    private ReconciliationSnapshotDao reconciliationSnapshotDao;

    private PositionReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new PositionReconciliationService(holdingDao, movementDao, eodSnapshotDao, reconciliationSnapshotDao);
    }

    // --- Incremental reconciliation ---

    @Test
    void reconcile_shouldFallBackToBootstrap_whenNoEodSnapshotExists() {
        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.empty());

        BondHolding h = holding("ACC-001", "US0378331005", "1000000.00");
        when(holdingDao.findAll()).thenReturn(List.of(h));
        when(movementDao.computeAllBalances()).thenReturn(
                rows(new Object[]{"ACC-001", "US0378331005", new BigDecimal("1000000.00")}));

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getTotalPositions()).isEqualTo(1);
    }

    @Test
    void reconcile_shouldReturnConsistent_whenNoMovementsSinceEod() {
        LocalDateTime snapshotTime = LocalDateTime.of(2026, 5, 10, 18, 0);
        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.of(LocalDate.of(2026, 5, 10)));
        when(eodSnapshotDao.findLatestSnapshotTimestamp()).thenReturn(Optional.of(snapshotTime));
        when(movementDao.findChangedPositionKeysSince(snapshotTime)).thenReturn(List.of());

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getTotalPositions()).isEqualTo(0);
        verify(holdingDao, never()).findAll();
    }

    @Test
    void reconcile_shouldVerifyPositionEqualsEodPlusMovements() {
        LocalDate eodDate = LocalDate.of(2026, 5, 10);
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 10, 18, 0);

        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.of(eodDate));
        when(eodSnapshotDao.findLatestSnapshotTimestamp()).thenReturn(Optional.of(cutoff));
        when(movementDao.findChangedPositionKeysSince(cutoff)).thenReturn(
                rows(new Object[]{"ACC-001", "US0378331005"}));

        Map<String, BigDecimal> eodBalances = Map.of("ACC-001|US0378331005", new BigDecimal("800000.00"));
        when(eodSnapshotDao.findBalancesByDate(eodDate)).thenReturn(eodBalances);

        when(movementDao.computeBalanceSince("ACC-001", "US0378331005", cutoff))
                .thenReturn(new BigDecimal("200000.00"));

        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005"))
                .thenReturn(Optional.of(holding("ACC-001", "US0378331005", "1000000.00")));

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getTotalPositions()).isEqualTo(1);
    }

    @Test
    void reconcile_shouldDetectDiscrepancy_whenPositionDrifted() {
        LocalDate eodDate = LocalDate.of(2026, 5, 10);
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 10, 18, 0);

        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.of(eodDate));
        when(eodSnapshotDao.findLatestSnapshotTimestamp()).thenReturn(Optional.of(cutoff));
        when(movementDao.findChangedPositionKeysSince(cutoff)).thenReturn(
                rows(new Object[]{"ACC-001", "US0378331005"}));

        Map<String, BigDecimal> eodBalances = Map.of("ACC-001|US0378331005", new BigDecimal("800000.00"));
        when(eodSnapshotDao.findBalancesByDate(eodDate)).thenReturn(eodBalances);

        when(movementDao.computeBalanceSince("ACC-001", "US0378331005", cutoff))
                .thenReturn(new BigDecimal("200000.00"));

        // Position shows 900000 but expected is 800000 + 200000 = 1000000
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005"))
                .thenReturn(Optional.of(holding("ACC-001", "US0378331005", "900000.00")));

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.getDiscrepancyCount()).isEqualTo(1);
        assertThat(result.getDiscrepancies().getFirst().getCachedBalance())
                .isEqualByComparingTo("900000.00");
    }

    @Test
    void reconcile_shouldTreatMissingPositionAsZero() {
        LocalDate eodDate = LocalDate.of(2026, 5, 10);
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 10, 18, 0);

        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.of(eodDate));
        when(eodSnapshotDao.findLatestSnapshotTimestamp()).thenReturn(Optional.of(cutoff));
        when(movementDao.findChangedPositionKeysSince(cutoff)).thenReturn(
                rows(new Object[]{"ACC-002", "GB00B0SWJX34"}));

        Map<String, BigDecimal> eodBalances = Map.of("ACC-002|GB00B0SWJX34", new BigDecimal("100000.00"));
        when(eodSnapshotDao.findBalancesByDate(eodDate)).thenReturn(eodBalances);

        when(movementDao.computeBalanceSince("ACC-002", "GB00B0SWJX34", cutoff))
                .thenReturn(new BigDecimal("50000.00"));
        when(holdingDao.findByAccountAndIsin("ACC-002", "GB00B0SWJX34"))
                .thenReturn(Optional.empty());

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.getDiscrepancies().getFirst().getCachedBalance())
                .isEqualByComparingTo("0");
    }

    // --- Daily close ---

    @Test
    void dailyClose_shouldSaveEodSnapshotsAndReconciliationRecord() {
        when(eodSnapshotDao.existsForDate(any())).thenReturn(false);
        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.empty());

        BondHolding h = holding("ACC-001", "US0378331005", "1000000.00");
        when(holdingDao.findAll()).thenReturn(List.of(h));
        when(movementDao.computeAllBalances()).thenReturn(
                rows(new Object[]{"ACC-001", "US0378331005", new BigDecimal("1000000.00")}));

        ReconciliationResult result = service.dailyClose();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getType()).isEqualTo(SnapshotType.DAILY_CLOSE);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EodPositionSnapshot>> snapshotCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(eodSnapshotDao).saveAll(snapshotCaptor.capture());
        List<EodPositionSnapshot> snapshots = snapshotCaptor.getValue();
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().getBusinessDate()).isEqualTo(LocalDate.now());
        assertThat(snapshots.getFirst().getAccountId()).isEqualTo("ACC-001");
        assertThat(snapshots.getFirst().getIsin()).isEqualTo("US0378331005");
        assertThat(snapshots.getFirst().getBalance()).isEqualByComparingTo("1000000.00");

        ArgumentCaptor<ReconciliationSnapshot> reconciliationCaptor =
                ArgumentCaptor.forClass(ReconciliationSnapshot.class);
        verify(reconciliationSnapshotDao).save(reconciliationCaptor.capture());
        ReconciliationSnapshot savedReconciliation = reconciliationCaptor.getValue();
        assertThat(savedReconciliation.getSnapshotType()).isEqualTo(SnapshotType.DAILY_CLOSE);
        assertThat(savedReconciliation.getPositionsChecked()).isEqualTo(1);
        assertThat(savedReconciliation.getDiscrepancyCount()).isZero();
        assertThat(savedReconciliation.isConsistent()).isTrue();
    }

    @Test
    void dailyClose_shouldSkipIfSnapshotsAlreadyExistForToday() {
        when(eodSnapshotDao.existsForDate(any())).thenReturn(true);

        ReconciliationResult result = service.dailyClose();

        assertThat(result.getTotalPositions()).isEqualTo(0);
        verify(eodSnapshotDao, never()).saveAll(any());
        verify(reconciliationSnapshotDao, never()).save(any());
    }

    @Test
    void dailyClose_shouldDetectDiscrepanciesAndStillSaveSnapshots() {
        when(eodSnapshotDao.existsForDate(any())).thenReturn(false);
        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.empty());

        BondHolding h = holding("ACC-001", "US0378331005", "1000000.00");
        when(holdingDao.findAll()).thenReturn(List.of(h));
        when(movementDao.computeAllBalances()).thenReturn(
                rows(new Object[]{"ACC-001", "US0378331005", new BigDecimal("900000.00")}));

        ReconciliationResult result = service.dailyClose();

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.getDiscrepancyCount()).isEqualTo(1);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EodPositionSnapshot>> snapshotCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(eodSnapshotDao).saveAll(snapshotCaptor.capture());
        List<EodPositionSnapshot> snapshots = snapshotCaptor.getValue();
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().getAccountId()).isEqualTo("ACC-001");
        assertThat(snapshots.getFirst().getIsin()).isEqualTo("US0378331005");
        assertThat(snapshots.getFirst().getBalance()).isEqualByComparingTo("1000000.00");

        ArgumentCaptor<ReconciliationSnapshot> reconciliationCaptor =
                ArgumentCaptor.forClass(ReconciliationSnapshot.class);
        verify(reconciliationSnapshotDao).save(reconciliationCaptor.capture());
        ReconciliationSnapshot savedReconciliation = reconciliationCaptor.getValue();
        assertThat(savedReconciliation.getSnapshotType()).isEqualTo(SnapshotType.DAILY_CLOSE);
        assertThat(savedReconciliation.getPositionsChecked()).isEqualTo(1);
        assertThat(savedReconciliation.getDiscrepancyCount()).isEqualTo(1);
        assertThat(savedReconciliation.isConsistent()).isFalse();
    }

    @Test
    void dailyClose_shouldUseSnapshotBasedReconciliation_whenPreviousEodExists() {
        LocalDate prevEod = LocalDate.of(2026, 5, 10);
        LocalDateTime cutoff = LocalDateTime.of(2026, 5, 10, 18, 0);

        when(eodSnapshotDao.existsForDate(any())).thenReturn(false);
        when(eodSnapshotDao.findLatestBusinessDate()).thenReturn(Optional.of(prevEod));
        when(eodSnapshotDao.findLatestSnapshotTimestamp()).thenReturn(Optional.of(cutoff));
        when(eodSnapshotDao.findBalancesByDate(prevEod))
                .thenReturn(Map.of("ACC-001|US0378331005", new BigDecimal("800000.00")));
        when(movementDao.computeAllBalancesSince(cutoff))
                .thenReturn(rows(new Object[]{"ACC-001", "US0378331005", new BigDecimal("200000.00")}));

        BondHolding h = holding("ACC-001", "US0378331005", "1000000.00");
        when(holdingDao.findAll()).thenReturn(List.of(h));

        ReconciliationResult result = service.dailyClose();

        assertThat(result.isConsistent()).isTrue();
        verify(movementDao, never()).computeAllBalances();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EodPositionSnapshot>> snapshotCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(eodSnapshotDao).saveAll(snapshotCaptor.capture());
        List<EodPositionSnapshot> snapshots = snapshotCaptor.getValue();
        assertThat(snapshots).hasSize(1);
        assertThat(snapshots.getFirst().getAccountId()).isEqualTo("ACC-001");
        assertThat(snapshots.getFirst().getIsin()).isEqualTo("US0378331005");
        assertThat(snapshots.getFirst().getBalance()).isEqualByComparingTo("1000000.00");
    }

    // --- Helpers ---

    private static BondHolding holding(String accountId, String isin, String quantity) {
        BondHolding h = new BondHolding();
        h.setAccountId(accountId);
        h.setIsin(isin);
        h.setQuantity(new BigDecimal(quantity));
        return h;
    }

    private static List<Object[]> rows(Object[]... items) {
        List<Object[]> list = new ArrayList<>();
        Collections.addAll(list, items);
        return list;
    }
}
