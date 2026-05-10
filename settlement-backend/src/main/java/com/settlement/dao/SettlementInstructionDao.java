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
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
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

    /**
     * Directly updates failure fields without a prior SELECT.
     * Returns the number of rows updated (0 if tradeRef not found).
     */
    public int updateFailure(String tradeRef, int retryCount, String failureReason) {
        return entityManager.createQuery(
            "UPDATE SettlementInstruction s " +
            "SET s.status = :status, s.retryCount = :retryCount, s.failureReason = :reason " +
            "WHERE s.tradeRef = :tradeRef"
        )
        .setParameter("status", InstructionStatus.FAILED)
        .setParameter("retryCount", retryCount)
        .setParameter("reason", failureReason)
        .setParameter("tradeRef", tradeRef)
        .executeUpdate();
    }

    /**
     * Bulk-updates all instructions with {@code fromStatus} to {@code toStatus}.
     * Returns the number of rows updated.
     */
    public int bulkUpdateStatus(InstructionStatus fromStatus, InstructionStatus toStatus) {
        return entityManager.createQuery(
            "UPDATE SettlementInstruction s SET s.status = :toStatus WHERE s.status = :fromStatus"
        )
        .setParameter("toStatus", toStatus)
        .setParameter("fromStatus", fromStatus)
        .executeUpdate();
    }
}
