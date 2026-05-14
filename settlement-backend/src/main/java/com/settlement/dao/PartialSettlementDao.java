package com.settlement.dao;

import com.settlement.entity.PartialSettlement;
import com.settlement.entity.PartialSettlementStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PartialSettlementDao {

    @PersistenceContext
    private EntityManager entityManager;

    public PartialSettlement save(PartialSettlement ps) {
        if (ps.getId() == null) {
            entityManager.persist(ps);
            return ps;
        }
        return entityManager.merge(ps);
    }

    public Optional<PartialSettlement> findById(Long id) {
        return Optional.ofNullable(entityManager.find(PartialSettlement.class, id));
    }

    public List<PartialSettlement> findByParentInstructionId(Long parentId) {
        TypedQuery<PartialSettlement> query = entityManager.createQuery(
            "SELECT p FROM PartialSettlement p WHERE p.parentInstructionId = :parentId ORDER BY p.splitSequence",
            PartialSettlement.class
        );
        query.setParameter("parentId", parentId);
        return query.getResultList();
    }

    public List<PartialSettlement> findByParentAndStatus(Long parentId, PartialSettlementStatus status) {
        TypedQuery<PartialSettlement> query = entityManager.createQuery(
            "SELECT p FROM PartialSettlement p WHERE p.parentInstructionId = :parentId AND p.status = :status ORDER BY p.splitSequence",
            PartialSettlement.class
        );
        query.setParameter("parentId", parentId);
        query.setParameter("status", status);
        return query.getResultList();
    }

    public long countByParentAndStatus(Long parentId, PartialSettlementStatus status) {
        return entityManager.createQuery(
            "SELECT COUNT(p) FROM PartialSettlement p WHERE p.parentInstructionId = :parentId AND p.status = :status",
            Long.class
        ).setParameter("parentId", parentId).setParameter("status", status).getSingleResult();
    }
}
