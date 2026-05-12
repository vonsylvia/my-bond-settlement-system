package com.settlement.dao;

import com.settlement.entity.MessageDirection;
import com.settlement.entity.SwiftMessage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SwiftMessageDao {

    @PersistenceContext
    private EntityManager entityManager;

    public SwiftMessage save(SwiftMessage message) {
        if (message.getId() == null) {
            entityManager.persist(message);
            return message;
        }
        return entityManager.merge(message);
    }

    public List<SwiftMessage> findByInstructionId(Long instructionId) {
        TypedQuery<SwiftMessage> query = entityManager.createQuery(
            "SELECT m FROM SwiftMessage m WHERE m.instructionId = :instructionId ORDER BY m.createdAt",
            SwiftMessage.class
        );
        query.setParameter("instructionId", instructionId);
        return query.getResultList();
    }

    public Optional<SwiftMessage> findLatestOutbound(Long instructionId, String messageType) {
        TypedQuery<SwiftMessage> query = entityManager.createQuery(
            "SELECT m FROM SwiftMessage m WHERE m.instructionId = :instructionId " +
            "AND m.messageType = :messageType AND m.direction = :direction " +
            "ORDER BY m.sequenceNo DESC, m.createdAt DESC",
            SwiftMessage.class
        );
        query.setParameter("instructionId", instructionId);
        query.setParameter("messageType", messageType);
        query.setParameter("direction", MessageDirection.OUTBOUND);
        query.setMaxResults(1);
        List<SwiftMessage> results = query.getResultList();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<SwiftMessage> findByTradeRef(String tradeRef) {
        TypedQuery<SwiftMessage> query = entityManager.createQuery(
            "SELECT m FROM SwiftMessage m WHERE m.tradeRef = :tradeRef ORDER BY m.createdAt",
            SwiftMessage.class
        );
        query.setParameter("tradeRef", tradeRef);
        return query.getResultList();
    }

    public int nextSequenceNo(Long instructionId, String messageType, MessageDirection direction) {
        TypedQuery<Integer> query = entityManager.createQuery(
            "SELECT COALESCE(MAX(m.sequenceNo), 0) + 1 FROM SwiftMessage m " +
            "WHERE m.instructionId = :instructionId AND m.messageType = :messageType " +
            "AND m.direction = :direction",
            Integer.class
        );
        query.setParameter("instructionId", instructionId);
        query.setParameter("messageType", messageType);
        query.setParameter("direction", direction);
        return query.getSingleResult();
    }
}
