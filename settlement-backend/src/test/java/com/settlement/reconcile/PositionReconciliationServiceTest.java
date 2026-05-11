package com.settlement.reconcile;

import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.ReconciliationSnapshotDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dto.ReconciliationResult;
import com.settlement.entity.BondHolding;
import com.settlement.entity.ReconciliationSnapshot;
import com.settlement.entity.SnapshotType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionReconciliationServiceTest {

    @Mock
    private BondHoldingDao holdingDao;

    @Mock
    private SecurityMovementDao movementDao;

    @Mock
    private ReconciliationSnapshotDao snapshotDao;

    private PositionReconciliationService service;

    @BeforeEach
    void setUp() {
        service = new PositionReconciliationService(holdingDao, movementDao, snapshotDao);
    }

    // --- Incremental reconciliation ---

    @Test
    void reconcile_shouldFallBackToFullScan_whenNoSnapshotExists() {
        when(snapshotDao.findLatestTimestamp()).thenReturn(null);

        BondHolding h = holding("ACC-001", "US0378331005", "1000000.00");
        when(holdingDao.findAll()).thenReturn(List.of(h));
        when(movementDao.computeAllBalances()).thenReturn(
                ledgerRows(new Object[]{"ACC-001", "US0378331005", new BigDecimal("1000000.00")}));

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getType()).isEqualTo(SnapshotType.INCREMENTAL);
        assertThat(result.getTotalPositions()).isEqualTo(1);
    }

    @Test
    void reconcile_shouldReturnConsistent_whenNoMovementsSinceSnapshot() {
        when(snapshotDao.findLatestTimestamp()).thenReturn(LocalDateTime.of(2026, 5, 11, 18, 0));
        when(movementDao.findChangedPositionKeysSince(any())).thenReturn(positionKeys());

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getTotalPositions()).isEqualTo(0);
        verify(holdingDao, never()).findAll();
        verify(movementDao, never()).computeAllBalances();
    }

    @Test
    void reconcile_shouldCheckOnlyChangedPositions() {
        LocalDateTime since = LocalDateTime.of(2026, 5, 11, 18, 0);
        when(snapshotDao.findLatestTimestamp()).thenReturn(since);
        when(movementDao.findChangedPositionKeysSince(since)).thenReturn(
                positionKeys(new Object[]{"ACC-001", "US0378331005"}));

        when(movementDao.computeBalance("ACC-001", "US0378331005"))
                .thenReturn(new BigDecimal("1500000.00"));
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005"))
                .thenReturn(Optional.of(holding("ACC-001", "US0378331005", "1500000.00")));

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getTotalPositions()).isEqualTo(1);
        assertThat(result.getType()).isEqualTo(SnapshotType.INCREMENTAL);
        verify(holdingDao, never()).findAll();
    }

    @Test
    void reconcile_shouldDetectIncrementalDiscrepancy() {
        LocalDateTime since = LocalDateTime.of(2026, 5, 11, 18, 0);
        when(snapshotDao.findLatestTimestamp()).thenReturn(since);
        when(movementDao.findChangedPositionKeysSince(since)).thenReturn(
                positionKeys(new Object[]{"ACC-001", "US0378331005"}));

        when(movementDao.computeBalance("ACC-001", "US0378331005"))
                .thenReturn(new BigDecimal("900000.00"));
        when(holdingDao.findByAccountAndIsin("ACC-001", "US0378331005"))
                .thenReturn(Optional.of(holding("ACC-001", "US0378331005", "1000000.00")));

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.getDiscrepancyCount()).isEqualTo(1);
        assertThat(result.getDiscrepancies().getFirst().getDifference())
                .isEqualByComparingTo("100000.00");
    }

    @Test
    void reconcile_shouldTreatMissingCachedAsZero() {
        LocalDateTime since = LocalDateTime.of(2026, 5, 11, 18, 0);
        when(snapshotDao.findLatestTimestamp()).thenReturn(since);
        when(movementDao.findChangedPositionKeysSince(since)).thenReturn(
                positionKeys(new Object[]{"ACC-002", "GB00B0SWJX34"}));

        when(movementDao.computeBalance("ACC-002", "GB00B0SWJX34"))
                .thenReturn(new BigDecimal("200000.00"));
        when(holdingDao.findByAccountAndIsin("ACC-002", "GB00B0SWJX34"))
                .thenReturn(Optional.empty());

        ReconciliationResult result = service.reconcile();

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.getDiscrepancies().getFirst().getCachedBalance())
                .isEqualByComparingTo("0");
    }

    // --- Daily close ---

    @Test
    void dailyClose_shouldPerformFullScanAndSaveSnapshot() {
        BondHolding h = holding("ACC-001", "US0378331005", "1000000.00");
        when(holdingDao.findAll()).thenReturn(List.of(h));
        when(movementDao.computeAllBalances()).thenReturn(
                ledgerRows(new Object[]{"ACC-001", "US0378331005", new BigDecimal("1000000.00")}));

        ReconciliationResult result = service.dailyClose();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getType()).isEqualTo(SnapshotType.DAILY_CLOSE);
        assertThat(result.getTotalPositions()).isEqualTo(1);

        ArgumentCaptor<ReconciliationSnapshot> captor = ArgumentCaptor.forClass(ReconciliationSnapshot.class);
        verify(snapshotDao).save(captor.capture());
        ReconciliationSnapshot snapshot = captor.getValue();
        assertThat(snapshot.getSnapshotType()).isEqualTo(SnapshotType.DAILY_CLOSE);
        assertThat(snapshot.getPositionsChecked()).isEqualTo(1);
        assertThat(snapshot.getDiscrepancyCount()).isEqualTo(0);
        assertThat(snapshot.isConsistent()).isTrue();
    }

    @Test
    void dailyClose_shouldDetectDiscrepanciesAndSaveSnapshot() {
        BondHolding h = holding("ACC-001", "US0378331005", "1000000.00");
        when(holdingDao.findAll()).thenReturn(List.of(h));
        when(movementDao.computeAllBalances()).thenReturn(
                ledgerRows(new Object[]{"ACC-001", "US0378331005", new BigDecimal("900000.00")}));

        ReconciliationResult result = service.dailyClose();

        assertThat(result.isConsistent()).isFalse();
        assertThat(result.getDiscrepancyCount()).isEqualTo(1);

        ArgumentCaptor<ReconciliationSnapshot> captor = ArgumentCaptor.forClass(ReconciliationSnapshot.class);
        verify(snapshotDao).save(captor.capture());
        assertThat(captor.getValue().isConsistent()).isFalse();
    }

    @Test
    void dailyClose_shouldHandleEmptyPositions() {
        when(holdingDao.findAll()).thenReturn(List.of());
        when(movementDao.computeAllBalances()).thenReturn(ledgerRows());

        ReconciliationResult result = service.dailyClose();

        assertThat(result.isConsistent()).isTrue();
        assertThat(result.getTotalPositions()).isEqualTo(0);
        verify(snapshotDao).save(any(ReconciliationSnapshot.class));
    }

    // --- Helpers ---

    private static BondHolding holding(String accountId, String isin, String quantity) {
        BondHolding h = new BondHolding();
        h.setAccountId(accountId);
        h.setIsin(isin);
        h.setQuantity(new BigDecimal(quantity));
        return h;
    }

    private static List<Object[]> ledgerRows(Object[]... rows) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] row : rows) {
            list.add(row);
        }
        return list;
    }

    private static List<Object[]> positionKeys(Object[]... keys) {
        List<Object[]> list = new ArrayList<>();
        for (Object[] key : keys) {
            list.add(key);
        }
        return list;
    }
}
