package com.settlement.dao;

import com.settlement.entity.AuditLog;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AuditLogDao {

    @PersistenceContext
    private EntityManager entityManager;

    public void save(AuditLog auditLog) {
        entityManager.persist(auditLog);
    }

    public List<AuditLog> findByTradeRef(String tradeRef) {
        TypedQuery<AuditLog> query = entityManager.createQuery(
            "SELECT a FROM AuditLog a WHERE a.tradeRef = :tradeRef ORDER BY a.createdAt DESC",
            AuditLog.class
        );
        query.setParameter("tradeRef", tradeRef);
        return query.getResultList();
    }
}
