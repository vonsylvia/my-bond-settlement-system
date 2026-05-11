package com.settlement.reconcile;

import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.ReconciliationSnapshotDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dto.PositionDiscrepancy;
import com.settlement.dto.ReconciliationResult;
import com.settlement.entity.BondHolding;
import com.settlement.entity.ReconciliationSnapshot;
import com.settlement.entity.SnapshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Compares cached positions (BondHolding) against the source-of-truth
 * ledger (SecurityMovement) to detect data inconsistencies.
 *
 * <p>Two modes of operation:
 * <ul>
 *   <li><b>Incremental</b> ({@link #reconcile()}) — only checks positions with
 *       movements since the last snapshot; falls back to full scan if no snapshot exists.</li>
 *   <li><b>Daily close</b> ({@link #dailyClose()}) — full scan of all positions,
 *       persists a snapshot that becomes the new baseline for subsequent incremental runs.</li>
 * </ul>
 */
@Service
public class PositionReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(PositionReconciliationService.class);

    private final BondHoldingDao holdingDao;
    private final SecurityMovementDao movementDao;
    private final ReconciliationSnapshotDao snapshotDao;

    public PositionReconciliationService(BondHoldingDao holdingDao,
                                         SecurityMovementDao movementDao,
                                         ReconciliationSnapshotDao snapshotDao) {
        this.holdingDao = holdingDao;
        this.movementDao = movementDao;
        this.snapshotDao = snapshotDao;
    }

    /**
     * Incremental reconciliation: only checks positions that have changed
     * since the last snapshot. If no snapshot exists, falls back to full scan.
     */
    @Transactional(readOnly = true)
    public ReconciliationResult reconcile() {
        LocalDateTime since = snapshotDao.findLatestTimestamp();

        if (since == null) {
            log.info("No previous snapshot found — performing full reconciliation");
            return reconcileFull(SnapshotType.INCREMENTAL);
        }

        log.info("Incremental reconciliation: checking positions changed since {}", since);

        List<Object[]> changedKeys = movementDao.findChangedPositionKeysSince(since);
        if (changedKeys.isEmpty()) {
            log.info("Incremental reconciliation: no movements since {} — all positions consistent", since);
            return new ReconciliationResult(SnapshotType.INCREMENTAL, 0, List.of());
        }

        List<PositionDiscrepancy> discrepancies = new ArrayList<>();
        for (Object[] key : changedKeys) {
            String accountId = (String) key[0];
            String isin = (String) key[1];

            BigDecimal ledger = movementDao.computeBalance(accountId, isin);
            BigDecimal cached = holdingDao.findByAccountAndIsin(accountId, isin)
                    .map(BondHolding::getQuantity)
                    .orElse(BigDecimal.ZERO);

            if (cached.compareTo(ledger) != 0) {
                discrepancies.add(new PositionDiscrepancy(accountId, isin, cached, ledger));
                log.warn("Position discrepancy: account={}, isin={}, cached={}, ledger={}",
                        accountId, isin, cached, ledger);
            }
        }

        logSummary("Incremental", changedKeys.size(), discrepancies.size());
        return new ReconciliationResult(SnapshotType.INCREMENTAL, changedKeys.size(), discrepancies);
    }

    /**
     * End-of-day full reconciliation: scans all positions, persists a
     * snapshot as the new baseline for incremental runs.
     */
    @Transactional
    public ReconciliationResult dailyClose() {
        log.info("Starting daily close reconciliation (full scan)");
        ReconciliationResult result = reconcileFull(SnapshotType.DAILY_CLOSE);

        snapshotDao.save(new ReconciliationSnapshot(
                SnapshotType.DAILY_CLOSE,
                result.getTotalPositions(),
                result.getDiscrepancyCount()));

        log.info("Daily close snapshot saved: positions={}, discrepancies={}",
                result.getTotalPositions(), result.getDiscrepancyCount());
        return result;
    }

    private ReconciliationResult reconcileFull(SnapshotType type) {
        Map<String, BigDecimal> ledgerBalances = loadLedgerBalances();
        Map<String, BigDecimal> cachedBalances = loadCachedBalances();

        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(ledgerBalances.keySet());
        allKeys.addAll(cachedBalances.keySet());

        List<PositionDiscrepancy> discrepancies = new ArrayList<>();
        for (String key : allKeys) {
            BigDecimal cached = cachedBalances.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal ledger = ledgerBalances.getOrDefault(key, BigDecimal.ZERO);

            if (cached.compareTo(ledger) != 0) {
                String[] parts = key.split("\\|", 2);
                discrepancies.add(new PositionDiscrepancy(parts[0], parts[1], cached, ledger));
                log.warn("Position discrepancy: account={}, isin={}, cached={}, ledger={}",
                        parts[0], parts[1], cached, ledger);
            }
        }

        logSummary("Full", allKeys.size(), discrepancies.size());
        return new ReconciliationResult(type, allKeys.size(), discrepancies);
    }

    private Map<String, BigDecimal> loadLedgerBalances() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (Object[] row : movementDao.computeAllBalances()) {
            String key = row[0] + "|" + row[1];
            map.put(key, (BigDecimal) row[2]);
        }
        return map;
    }

    private Map<String, BigDecimal> loadCachedBalances() {
        Map<String, BigDecimal> map = new HashMap<>();
        for (BondHolding h : holdingDao.findAll()) {
            String key = h.getAccountId() + "|" + h.getIsin();
            map.put(key, h.getQuantity());
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
