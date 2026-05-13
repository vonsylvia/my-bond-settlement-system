package com.settlement.dao;

import com.settlement.entity.MessageStandard;
import com.settlement.entity.MessageTypeRegistry;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MessageTypeRegistryDao {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<MessageTypeRegistry> findByMessageType(String messageType) {
        MessageTypeRegistry entity = entityManager.find(MessageTypeRegistry.class, messageType);
        return Optional.ofNullable(entity);
    }

    public List<MessageTypeRegistry> findByStandard(MessageStandard standard) {
        TypedQuery<MessageTypeRegistry> query = entityManager.createQuery(
                "SELECT r FROM MessageTypeRegistry r WHERE r.messageStandard = :standard AND r.active = true",
                MessageTypeRegistry.class);
        query.setParameter("standard", standard);
        return query.getResultList();
    }

    public List<MessageTypeRegistry> findByCategory(String category) {
        TypedQuery<MessageTypeRegistry> query = entityManager.createQuery(
                "SELECT r FROM MessageTypeRegistry r WHERE r.category = :category AND r.active = true",
                MessageTypeRegistry.class);
        query.setParameter("category", category);
        return query.getResultList();
    }

    /**
     * Finds the equivalent message type in the target standard.
     * E.g. given "MT541" → returns the registry entry for "sese.023.001.09".
     */
    public Optional<MessageTypeRegistry> findEquivalent(String messageType) {
        return findByMessageType(messageType)
                .filter(r -> r.getEquivalentType() != null)
                .flatMap(r -> findByMessageType(r.getEquivalentType()));
    }

    public List<MessageTypeRegistry> findAll() {
        TypedQuery<MessageTypeRegistry> query = entityManager.createQuery(
                "SELECT r FROM MessageTypeRegistry r WHERE r.active = true ORDER BY r.messageStandard, r.messageType",
                MessageTypeRegistry.class);
        return query.getResultList();
    }
}
