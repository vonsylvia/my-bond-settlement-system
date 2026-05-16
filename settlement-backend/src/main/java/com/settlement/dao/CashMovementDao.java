package com.settlement.dao;

import com.settlement.entity.CashMovement;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CashMovementDao {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(CashMovement movement) {
        entityManager.persist(movement);
    }

    public List<CashMovement> findByTradeRef(String tradeRef) {
        TypedQuery<CashMovement> query = entityManager.createQuery(
            "SELECT m FROM CashMovement m WHERE m.tradeRef = :tradeRef ORDER BY m.createdAt",
            CashMovement.class
        );
        query.setParameter("tradeRef", tradeRef);
        return query.getResultList();
    }

    public List<CashMovement> findByAccountAndCurrency(String accountId, String currency) {
        TypedQuery<CashMovement> query = entityManager.createQuery(
            "SELECT m FROM CashMovement m WHERE m.accountId = :accountId AND m.currency = :currency ORDER BY m.createdAt",
            CashMovement.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("currency", currency);
        return query.getResultList();
    }
}
