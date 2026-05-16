package com.settlement.dao;

import com.settlement.entity.InstructionStatus;
import com.settlement.entity.SettlementInstruction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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

    public Optional<SettlementInstruction> findByParticipantAndClientReference(
            String participantId, String clientReference) {
        TypedQuery<SettlementInstruction> query = entityManager.createQuery(
            "SELECT s FROM SettlementInstruction s " +
            "WHERE s.participantId = :participantId AND s.clientReference = :clientReference",
            SettlementInstruction.class
        );
        query.setParameter("participantId", participantId);
        query.setParameter("clientReference", clientReference);
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
     * Sets status to RETRYING if retries remain, or FAILED when exhausted.
     * Returns the number of rows updated (0 if tradeRef not found).
     */
    public int updateFailure(String tradeRef, int retryCount, String failureReason,
                             boolean exhausted) {
        InstructionStatus targetStatus = exhausted
                ? InstructionStatus.FAILED
                : InstructionStatus.RETRYING;
        return entityManager.createQuery(
            "UPDATE SettlementInstruction s " +
            "SET s.status = :status, s.retryCount = :retryCount, s.failureReason = :reason " +
            "WHERE s.tradeRef = :tradeRef"
        )
        .setParameter("status", targetStatus)
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

    /**
     * Resets only instructions stuck in {@code fromStatus} for longer than {@code threshold}.
     * Prevents resetting instructions that are still actively being processed.
     */
    public int bulkUpdateStatusOlderThan(InstructionStatus fromStatus, InstructionStatus toStatus,
                                         LocalDateTime threshold) {
        return entityManager.createQuery(
            "UPDATE SettlementInstruction s SET s.status = :toStatus " +
            "WHERE s.status = :fromStatus AND s.updatedAt < :threshold"
        )
        .setParameter("toStatus", toStatus)
        .setParameter("fromStatus", fromStatus)
        .setParameter("threshold", threshold)
        .executeUpdate();
    }

    /**
     * CAS-style conditional status transition. Returns 1 if the update succeeded
     * (the instruction was in the expected status), 0 otherwise.
     */
    public int compareAndSetStatus(String tradeRef, InstructionStatus expectedStatus,
                                   InstructionStatus newStatus) {
        return entityManager.createQuery(
            "UPDATE SettlementInstruction s SET s.status = :newStatus, s.updatedAt = :now " +
            "WHERE s.tradeRef = :tradeRef AND s.status = :expectedStatus"
        )
        .setParameter("newStatus", newStatus)
        .setParameter("now", LocalDateTime.now())
        .setParameter("tradeRef", tradeRef)
        .setParameter("expectedStatus", expectedStatus)
        .executeUpdate();
    }
}
