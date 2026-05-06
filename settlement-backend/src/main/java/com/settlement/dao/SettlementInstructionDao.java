package com.settlement.dao;

import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SettlementInstructionDao {

    @PersistenceContext
    private EntityManager entityManager;

    public SettlementInstruction save(SettlementInstruction instruction) {
        if (instruction.getId() == null) {
            entityManager.persist(instruction);
            return instruction;
        }
        return entityManager.merge(instruction);
    }

    public Optional<SettlementInstruction> findByTradeRef(String tradeRef) {
        TypedQuery<SettlementInstruction> query = entityManager.createQuery(
            "SELECT s FROM SettlementInstruction s WHERE s.tradeRef = :tradeRef",
            SettlementInstruction.class
        );
        query.setParameter("tradeRef", tradeRef);
        List<SettlementInstruction> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<SettlementInstruction> findByStatus(InstructionStatus status) {
        TypedQuery<SettlementInstruction> query = entityManager.createQuery(
            "SELECT s FROM SettlementInstruction s WHERE s.status = :status ORDER BY s.createdAt DESC",
            SettlementInstruction.class
        );
        query.setParameter("status", status);
        return query.getResultList();
    }

    public List<SettlementInstruction> findAll(int page, int size) {
        TypedQuery<SettlementInstruction> query = entityManager.createQuery(
            "SELECT s FROM SettlementInstruction s ORDER BY s.createdAt DESC",
            SettlementInstruction.class
        );
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    public long count() {
        return entityManager.createQuery(
            "SELECT COUNT(s) FROM SettlementInstruction s", Long.class
        ).getSingleResult();
    }
}
