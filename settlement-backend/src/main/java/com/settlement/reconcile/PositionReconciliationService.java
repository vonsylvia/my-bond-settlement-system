package com.settlement.reconcile;

import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.EodPositionSnapshotDao;
import com.settlement.dao.ReconciliationSnapshotDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dto.PositionDiscrepancy;
import com.settlement.dto.ReconciliationResult;
import com.settlement.entity.BondHolding;
import com.settlement.entity.EodPositionSnapshot;
import com.settlement.entity.ReconciliationSnapshot;
import com.settlement.entity.SnapshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CSD-style position reconciliation.
 *
 * <p>The authoritative position lives in {@code BondHolding} and is updated
 * transactionally on each settlement. The {@code SecurityMovement} table is
 * an append-only audit journal.
 *
 * <p>Reconciliation verifies data integrity by checking:
 * <pre>
 *   current_position == eod_snapshot(prev_day) + SUM(movements since snapshot)
 * </pre>
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Incremental</b> ({@link #reconcile()}) — only checks positions with
 *       movements since the last EOD snapshot; bounded SUM over at most one day of data.</li>
 *   <li><b>Daily close</b> ({@link #dailyClose()}) — full verification of all
 *       positions, then persists per-position EOD snapshots as the new baseline.</li>
 * </ul>
 */
@Service
public class PositionReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PositionReconciliationService.class);

    private final BondHoldingDao holdingDao;
    private final SecurityMovementDao movementDao;
    private final EodPositionSnapshotDao eodSnapshotDao;
    private final ReconciliationSnapshotDao reconciliationSnapshotDao;

    public PositionReconciliationService(BondHoldingDao holdingDao,
                                         SecurityMovementDao movementDao,
                                         EodPositionSnapshotDao eodSnapshotDao,
                                         ReconciliationSnapshotDao reconciliationSnapshotDao) {
        this.holdingDao = holdingDao;
        this.movementDao = movementDao;
        this.eodSnapshotDao = eodSnapshotDao;
        this.reconciliationSnapshotDao = reconciliationSnapshotDao;
    }

    /**
     * Incremental reconciliation: verifies positions that have changed since
     * the last EOD snapshot. For each changed position:
     * <pre>current_position == eod_balance + SUM(movements since EOD)</pre>
     *
     * <p>If no EOD snapshot exists, falls back to full scan using
     * all-time movement SUM (first-run bootstrap).
     */
    @Transactional(readOnly = true)
    public ReconciliationResult reconcile() {
        Optional<LocalDate> latestEodDate = eodSnapshotDao.findLatestBusinessDate();

        if (latestEodDate.isEmpty()) {
            log.info("No EOD snapshot found — performing bootstrap full reconciliation");
            return reconcileFullBootstrap(SnapshotType.INCREMENTAL);
        }

        LocalDate eodDate = latestEodDate.get();
        LocalDateTime cutoff = eodSnapshotDao.findLatestSnapshotTimestamp()
                .orElse(eodDate.plusDays(1).atStartOfDay());
        log.info("Incremental reconciliation: verifying positions changed since EOD {} (cutoff={})", eodDate, cutoff);

        List<Object[]> changedKeys = movementDao.findChangedPositionKeysSince(cutoff);
        if (changedKeys.isEmpty()) {
            log.info("No movements since EOD {} — all positions consistent", eodDate);
            return new ReconciliationResult(SnapshotType.INCREMENTAL, 0, List.of());
        }

        Map<String, BigDecimal> eodBalances = eodSnapshotDao.findBalancesByDate(eodDate);
        List<PositionDiscrepancy> discrepancies = new ArrayList<>();

        for (Object[] key : changedKeys) {
            String accountId = (String) key[0];
            String isin = (String) key[1];
            String compositeKey = accountId + "|" + isin;

            BigDecimal eodBalance = eodBalances.getOrDefault(compositeKey, BigDecimal.ZERO);
            BigDecimal netMovement = movementDao.computeBalanceSince(accountId, isin, cutoff);
            BigDecimal expectedPosition = eodBalance.add(netMovement);

            BigDecimal actualPosition = holdingDao.findByAccountAndIsin(accountId, isin)
                    .map(BondHolding::getQuantity)
                    .orElse(BigDecimal.ZERO);

            if (actualPosition.compareTo(expectedPosition) != 0) {
                discrepancies.add(new PositionDiscrepancy(accountId, isin, actualPosition, expectedPosition));
                log.warn("Position discrepancy: account={}, isin={}, position={}, expected(eod+movements)={}",
                        accountId, isin, actualPosition, expectedPosition);
            }
        }

        logSummary("Incremental", changedKeys.size(), discrepancies.size());
        return new ReconciliationResult(SnapshotType.INCREMENTAL, changedKeys.size(), discrepancies);
    }

    /**
     * End-of-day full reconciliation and snapshot: verifies all positions,
     * then persists per-position EOD snapshots as the baseline for the next
     * day's incremental reconciliation.
     */
    @Transactional
    public ReconciliationResult dailyClose() {
        LocalDate today = LocalDate.now();
        log.info("Starting daily close for business date {}", today);

        if (eodSnapshotDao.existsForDate(today)) {
            log.warn("EOD snapshots already exist for {} — skipping to avoid duplicates", today);
            return new ReconciliationResult(SnapshotType.DAILY_CLOSE, 0, List.of());
        }

        Optional<LocalDate> prevEod = eodSnapshotDao.findLatestBusinessDate();
        ReconciliationResult result;

        if (prevEod.isEmpty()) {
            result = reconcileFullBootstrap(SnapshotType.DAILY_CLOSE);
        } else {
            result = reconcileFullAgainstSnapshot(prevEod.get());
        }

        // Persist per-position EOD snapshots from the authoritative position table
        List<BondHolding> allPositions = holdingDao.findAll();
        List<EodPositionSnapshot> snapshots = new ArrayList<>(allPositions.size());
        for (BondHolding h : allPositions) {
            snapshots.add(new EodPositionSnapshot(today, h.getAccountId(), h.getIsin(), h.getQuantity()));
        }
        eodSnapshotDao.saveAll(snapshots);

        // Also save a reconciliation run record
        reconciliationSnapshotDao.save(new ReconciliationSnapshot(
                SnapshotType.DAILY_CLOSE,
                result.getTotalPositions(),
                result.getDiscrepancyCount()));

        log.info("Daily close complete: date={}, positions={}, discrepancies={}, snapshots_saved={}",
                today, result.getTotalPositions(), result.getDiscrepancyCount(), snapshots.size());
        return result;
    }

    /**
     * Full reconciliation against the previous EOD snapshot:
     * for every position, verify current == eod_snapshot + movements_since.
     */
    private ReconciliationResult reconcileFullAgainstSnapshot(LocalDate eodDate) {
        LocalDateTime cutoff = eodSnapshotDao.findLatestSnapshotTimestamp()
                .orElse(eodDate.plusDays(1).atStartOfDay());
        Map<String, BigDecimal> eodBalances = eodSnapshotDao.findBalancesByDate(eodDate);
        Map<String, BigDecimal> netMovements = loadNetMovementsSince(cutoff);

        Map<String, BigDecimal> currentPositions = loadCurrentPositions();

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(currentPositions.keySet());
        allKeys.addAll(eodBalances.keySet());
        allKeys.addAll(netMovements.keySet());

        List<PositionDiscrepancy> discrepancies = new ArrayList<>();
        for (String key : allKeys) {
            BigDecimal eod = eodBalances.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal net = netMovements.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal expected = eod.add(net);
            BigDecimal actual = currentPositions.getOrDefault(key, BigDecimal.ZERO);

            if (actual.compareTo(expected) != 0) {
                String[] parts = key.split("\\|", 2);
                discrepancies.add(new PositionDiscrepancy(parts[0], parts[1], actual, expected));
                log.warn("Position discrepancy: key={}, position={}, expected(eod+movements)={}",
                        key, actual, expected);
            }
        }

        logSummary("Full (snapshot-based)", allKeys.size(), discrepancies.size());
        return new ReconciliationResult(SnapshotType.DAILY_CLOSE, allKeys.size(), discrepancies);
    }

    /**
     * Bootstrap reconciliation (no prior EOD snapshot exists): compares
     * current positions against the all-time movement SUM. Only used on
     * first run or after data migration.
     */
    private ReconciliationResult reconcileFullBootstrap(SnapshotType type) {
        Map<String, BigDecimal> ledgerBalances = loadAllTimeLedgerBalances();
        Map<String, BigDecimal> currentPositions = loadCurrentPositions();

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(ledgerBalances.keySet());
        allKeys.addAll(currentPositions.keySet());

        List<PositionDiscrepancy> discrepancies = new ArrayList<>();
        for (String key : allKeys) {
            BigDecimal position = currentPositions.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal ledger = ledgerBalances.getOrDefault(key, BigDecimal.ZERO);

            if (position.compareTo(ledger) != 0) {
                String[] parts = key.split("\\|", 2);
                discrepancies.add(new PositionDiscrepancy(parts[0], parts[1], position, ledger));
                log.warn("Position discrepancy (bootstrap): key={}, position={}, ledger={}",
                        key, position, ledger);
            }
        }

        logSummary("Full (bootstrap)", allKeys.size(), discrepancies.size());
        return new ReconciliationResult(type, allKeys.size(), discrepancies);
    }

    private Map<String, BigDecimal> loadAllTimeLedgerBalances() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] row : movementDao.computeAllBalances()) {
            map.put(row[0] + "|" + row[1], (BigDecimal) row[2]);
        }
        return map;
    }

    private Map<String, BigDecimal> loadNetMovementsSince(LocalDateTime since) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] row : movementDao.computeAllBalancesSince(since)) {
            map.put(row[0] + "|" + row[1], (BigDecimal) row[2]);
        }
        return map;
    }

    private Map<String, BigDecimal> loadCurrentPositions() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (BondHolding h : holdingDao.findAll()) {
            map.put(h.getAccountId() + "|" + h.getIsin(), h.getQuantity());
        }
        return map;
    }

    private void logSummary(String mode, int checked, int discrepancies) {
        if (discrepancies == 0) {
            log.info("{} reconciliation complete: all {} positions consistent", mode, checked);
        } else {
            log.error("{} reconciliation complete: {} discrepancies found out of {} positions",
                    mode, discrepancies, checked);
        }
    }
}
