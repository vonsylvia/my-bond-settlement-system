package com.settlement.dao;

import com.settlement.entity.Direction;
import com.settlement.entity.MatchingInstruction;
import com.settlement.entity.MatchingStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class MatchingInstructionDao {

    @PersistenceContext
    private EntityManager entityManager;

    public MatchingInstruction save(MatchingInstruction instruction) {
        if (instruction.getId() == null) {
            entityManager.persist(instruction);
            return instruction;
        }
        return entityManager.merge(instruction);
    }

    public Optional<MatchingInstruction> findById(Long id) {
        MatchingInstruction result = entityManager.find(MatchingInstruction.class, id);
        return Optional.ofNullable(result);
    }

    public List<MatchingInstruction> findUnmatchedCandidates(
            String isin, LocalDate settlementDate, Direction oppositeDirection, String counterpartyBic) {
        TypedQuery<MatchingInstruction> query = entityManager.createQuery(
            "SELECT m FROM MatchingInstruction m WHERE m.isin = :isin " +
            "AND m.settlementDate = :settlementDate AND m.direction = :direction " +
            "AND m.submitterBic = :counterpartyBic " +
            "AND m.matchingStatus IN (:statuses) ORDER BY m.createdAt",
            MatchingInstruction.class
        );
        query.setParameter("isin", isin);
        query.setParameter("settlementDate", settlementDate);
        query.setParameter("direction", oppositeDirection);
        query.setParameter("counterpartyBic", counterpartyBic);
        query.setParameter("statuses", List.of(MatchingStatus.ALLEGED, MatchingStatus.UNMATCHED));
        return query.getResultList();
    }

    public List<MatchingInstruction> findAll(int page, int size) {
        return entityManager.createQuery(
            "SELECT m FROM MatchingInstruction m ORDER BY m.id DESC",
            MatchingInstruction.class
        ).setFirstResult(page * size).setMaxResults(size).getResultList();
    }

    public long countAll() {
        return entityManager.createQuery(
            "SELECT COUNT(m) FROM MatchingInstruction m", Long.class
        ).getSingleResult();
    }

    public List<MatchingInstruction> findByStatus(MatchingStatus status) {
        return entityManager.createQuery(
            "SELECT m FROM MatchingInstruction m WHERE m.matchingStatus = :status ORDER BY m.id DESC",
            MatchingInstruction.class
        ).setParameter("status", status).getResultList();
    }

    public List<MatchingInstruction> findByStatus(MatchingStatus status, int page, int size) {
        TypedQuery<MatchingInstruction> query = entityManager.createQuery(
            "SELECT m FROM MatchingInstruction m WHERE m.matchingStatus = :status ORDER BY m.id DESC",
            MatchingInstruction.class
        );
        query.setParameter("status", status);
        query.setFirstResult(page * size);
        query.setMaxResults(size);
        return query.getResultList();
    }

    public long countByStatus(MatchingStatus status) {
        TypedQuery<Long> query = entityManager.createQuery(
            "SELECT COUNT(m) FROM MatchingInstruction m WHERE m.matchingStatus = :status", Long.class
        );
        query.setParameter("status", status);
        return query.getSingleResult();
    }

    public List<MatchingInstruction> findByTradeRef(String tradeRef) {
        TypedQuery<MatchingInstruction> query = entityManager.createQuery(
            "SELECT m FROM MatchingInstruction m WHERE m.tradeRef = :tradeRef",
            MatchingInstruction.class
        );
        query.setParameter("tradeRef", tradeRef);
        return query.getResultList();
    }
}
