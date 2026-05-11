package com.settlement.dao;

import com.settlement.entity.BondHolding;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class BondHoldingDao {

    @PersistenceContext
    private EntityManager entityManager;

    public BondHolding save(BondHolding holding) {
        if (holding.getId() == null) {
            entityManager.persist(holding);
            return holding;
        }
        return entityManager.merge(holding);
    }

    public Optional<BondHolding> findByAccountAndIsin(String accountId, String isin) {
        TypedQuery<BondHolding> query = entityManager.createQuery(
            "SELECT h FROM BondHolding h WHERE h.accountId = :accountId AND h.isin = :isin",
            BondHolding.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("isin", isin);
        List<BondHolding> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<BondHolding> findByAccount(String accountId) {
        TypedQuery<BondHolding> query = entityManager.createQuery(
            "SELECT h FROM BondHolding h WHERE h.accountId = :accountId ORDER BY h.isin",
            BondHolding.class
        );
        query.setParameter("accountId", accountId);
        return query.getResultList();
    }

    public List<BondHolding> findAll() {
        return entityManager.createQuery(
            "SELECT h FROM BondHolding h ORDER BY h.accountId, h.isin",
            BondHolding.class
        ).getResultList();
    }
}
