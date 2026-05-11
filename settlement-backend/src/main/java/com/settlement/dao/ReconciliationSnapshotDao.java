package com.settlement.dao;

import com.settlement.entity.ReconciliationSnapshot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReconciliationSnapshotDao {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(ReconciliationSnapshot snapshot) {
        entityManager.persist(snapshot);
    }

    /**
     * Returns the most recent snapshot (by createdAt), used as the baseline
     * timestamp for incremental reconciliation.
     */
    public Optional<ReconciliationSnapshot> findLatest() {
        List<ReconciliationSnapshot> results = entityManager.createQuery(
                "SELECT s FROM ReconciliationSnapshot s ORDER BY s.createdAt DESC",
                ReconciliationSnapshot.class
        ).setMaxResults(1).getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Returns the timestamp of the latest snapshot, or {@code null} if
     * no snapshot has ever been taken (triggers full reconciliation).
     */
    public LocalDateTime findLatestTimestamp() {
        return findLatest().map(ReconciliationSnapshot::getCreatedAt).orElse(null);
    }
}
