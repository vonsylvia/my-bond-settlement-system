package com.settlement.dao;

import com.settlement.entity.EodPositionSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class EodPositionSnapshotDao {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(EodPositionSnapshot snapshot) {
        entityManager.persist(snapshot);
    }

    public void saveAll(List<EodPositionSnapshot> snapshots) {
        for (EodPositionSnapshot s : snapshots) {
            entityManager.persist(s);
        }
    }

    /**
     * Returns the most recent business date for which EOD snapshots exist.
     */
    public Optional<LocalDate> findLatestBusinessDate() {
        List<LocalDate> results = entityManager.createQuery(
            "SELECT MAX(s.businessDate) FROM EodPositionSnapshot s", LocalDate.class
        ).getResultList();
        return results.isEmpty() || results.getFirst() == null
                ? Optional.empty()
                : Optional.of(results.getFirst());
    }

    /**
     * Returns the actual creation timestamp of the latest EOD snapshot.
     * Used as the reconciliation cutoff to correctly capture movements
     * that occurred after the snapshot was taken (regardless of business date).
     */
    public Optional<LocalDateTime> findLatestSnapshotTimestamp() {
        List<LocalDateTime> results = entityManager.createQuery(
            "SELECT MAX(s.createdAt) FROM EodPositionSnapshot s", LocalDateTime.class
        ).getResultList();
        return results.isEmpty() || results.getFirst() == null
                ? Optional.empty()
                : Optional.of(results.getFirst());
    }

    /**
     * Loads all EOD snapshots for a given business date as a map of
     * "accountId|isin" → balance.
     */
    public Map<String, BigDecimal> findBalancesByDate(LocalDate businessDate) {
        TypedQuery<EodPositionSnapshot> query = entityManager.createQuery(
            "SELECT s FROM EodPositionSnapshot s WHERE s.businessDate = :date",
            EodPositionSnapshot.class
        );
        query.setParameter("date", businessDate);

        Map<String, BigDecimal> map = new HashMap<>();
        for (EodPositionSnapshot s : query.getResultList()) {
            map.put(s.getAccountId() + "|" + s.getIsin(), s.getBalance());
        }
        return map;
    }

    /**
     * Returns the EOD balance for a specific position on a given date.
     */
    public Optional<BigDecimal> findBalance(LocalDate businessDate, String accountId, String isin) {
        List<EodPositionSnapshot> results = entityManager.createQuery(
            "SELECT s FROM EodPositionSnapshot s " +
            "WHERE s.businessDate = :date AND s.accountId = :accountId AND s.isin = :isin",
            EodPositionSnapshot.class
        ).setParameter("date", businessDate)
         .setParameter("accountId", accountId)
         .setParameter("isin", isin)
         .getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst().getBalance());
    }

    /**
     * Checks whether snapshots exist for the given business date.
     */
    public boolean existsForDate(LocalDate businessDate) {
        Long count = entityManager.createQuery(
            "SELECT COUNT(s) FROM EodPositionSnapshot s WHERE s.businessDate = :date", Long.class
        ).setParameter("date", businessDate).getSingleResult();
        return count > 0;
    }
}
