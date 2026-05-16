package com.settlement.dao;

import com.settlement.entity.CashAccount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CashAccountDao {

    @PersistenceContext
    private EntityManager entityManager;

    public CashAccount save(CashAccount account) {
        if (account.getId() == null) {
            entityManager.persist(account);
            return account;
        }
        return entityManager.merge(account);
    }

    public Optional<CashAccount> findByAccountAndCurrency(String accountId, String currency) {
        TypedQuery<CashAccount> query = entityManager.createQuery(
            "SELECT c FROM CashAccount c WHERE c.accountId = :accountId AND c.currency = :currency",
            CashAccount.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("currency", currency);
        List<CashAccount> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public Optional<CashAccount> findByAccountAndCurrencyForUpdate(String accountId, String currency) {
        TypedQuery<CashAccount> query = entityManager.createQuery(
            "SELECT c FROM CashAccount c WHERE c.accountId = :accountId AND c.currency = :currency",
            CashAccount.class
        );
        query.setParameter("accountId", accountId);
        query.setParameter("currency", currency);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        List<CashAccount> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<CashAccount> findByAccount(String accountId) {
        TypedQuery<CashAccount> query = entityManager.createQuery(
            "SELECT c FROM CashAccount c WHERE c.accountId = :accountId ORDER BY c.currency",
            CashAccount.class
        );
        query.setParameter("accountId", accountId);
        return query.getResultList();
    }

    public List<CashAccount> findAll() {
        return entityManager.createQuery(
            "SELECT c FROM CashAccount c ORDER BY c.accountId, c.currency",
            CashAccount.class
        ).getResultList();
    }
}
