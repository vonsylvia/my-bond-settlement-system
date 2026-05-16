package com.settlement.dao;

import com.settlement.entity.CounterpartyCapability;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class CounterpartyCapabilityDao {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional(readOnly = true)
    public Optional<CounterpartyCapability> findByBic(String bicCode) {
        CounterpartyCapability entity = entityManager.find(CounterpartyCapability.class, bicCode);
        if (entity != null && entity.isActive()) {
            return Optional.of(entity);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public Optional<CounterpartyCapability> findByBicFuzzy(String bicCode) {
        Optional<CounterpartyCapability> exact = findByBic(bicCode);
        if (exact.isPresent()) return exact;

        if (bicCode != null && bicCode.length() == 8) {
            return findByBic(bicCode + "XXX");
        }
        if (bicCode != null && bicCode.length() == 11 && bicCode.endsWith("XXX")) {
            return findByBic(bicCode.substring(0, 8));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<CounterpartyCapability> findAll() {
        TypedQuery<CounterpartyCapability> query = entityManager.createQuery(
                "SELECT c FROM CounterpartyCapability c WHERE c.active = true ORDER BY c.bicCode",
                CounterpartyCapability.class);
        return query.getResultList();
    }

    @Transactional
    public CounterpartyCapability save(CounterpartyCapability entity) {
        if (entityManager.find(CounterpartyCapability.class, entity.getBicCode()) == null) {
            entityManager.persist(entity);
            return entity;
        }
        return entityManager.merge(entity);
    }
}
