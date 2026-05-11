package com.settlement.dao;

import com.settlement.entity.SecurityMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class SecurityMovementDao {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(SecurityMovement movement) {
        entityManager.persist(movement);
    }

    public List<SecurityMovement> findByAccountAndIsin(String accountId, String isin) {
        TypedQuery<SecurityMovement> query = entityManager.createQuery(
            "SELECT m FROM SecurityMovement m WHERE m.accountId = :accountId AND m.isin = :isin " +
            "ORDER BY m.createdAt",
            SecurityMovement.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("isin", isin);
        return query.getResultList();
    }

    public List<SecurityMovement> findByTradeRef(String tradeRef) {
        TypedQuery<SecurityMovement> query = entityManager.createQuery(
            "SELECT m FROM SecurityMovement m WHERE m.tradeRef = :tradeRef ORDER BY m.createdAt",
            SecurityMovement.class
        );
        query.setParameter("tradeRef", tradeRef);
        return query.getResultList();
    }

    /**
     * Computes the true balance from all ledger entries for each (account, isin) pair.
     * Returns rows of [accountId, isin, computedBalance].
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> computeAllBalances() {
        return entityManager.createQuery(
            "SELECT m.accountId, m.isin, " +
            "SUM(CASE WHEN m.movementType = com.settlement.entity.MovementType.CREDIT " +
            "THEN m.quantity ELSE -m.quantity END) " +
            "FROM SecurityMovement m GROUP BY m.accountId, m.isin " +
            "ORDER BY m.accountId, m.isin"
        ).getResultList();
    }

    /**
     * Returns distinct (accountId, isin) pairs that have had movements
     * after the given timestamp. Used by incremental reconciliation to
     * limit scope to recently-changed positions only.
     */
    @SuppressWarnings("unchecked")
    public List<Object[]> findChangedPositionKeysSince(LocalDateTime since) {
        return entityManager.createQuery(
            "SELECT DISTINCT m.accountId, m.isin FROM SecurityMovement m " +
            "WHERE m.createdAt > :since ORDER BY m.accountId, m.isin"
        ).setParameter("since", since).getResultList();
    }

    /**
     * Computes the true balance from ledger entries for a specific (account, isin) pair.
     */
    public BigDecimal computeBalance(String accountId, String isin) {
        Object result = entityManager.createQuery(
            "SELECT SUM(CASE WHEN m.movementType = com.settlement.entity.MovementType.CREDIT " +
            "THEN m.quantity ELSE -m.quantity END) " +
            "FROM SecurityMovement m WHERE m.accountId = :accountId AND m.isin = :isin"
        ).setParameter("accountId", accountId)
         .setParameter("isin", isin)
         .getSingleResult();
        return result != null ? (BigDecimal) result : BigDecimal.ZERO;
    }
}
