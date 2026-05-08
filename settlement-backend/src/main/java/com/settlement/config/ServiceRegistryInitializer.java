package com.settlement.config;

import com.settlement.bridge.ServiceRegistry;
import com.settlement.reconcile.ReconciliationService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Registers Spring-managed services into the EJB-side {@link ServiceRegistry}
 * on application startup, enabling MDBs to access them across module boundaries.
 */
@Component
public class ServiceRegistryInitializer {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistryInitializer.class);

    private final ReconciliationService reconciliationService;

    public ServiceRegistryInitializer(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostConstruct
    public void init() {
        ServiceRegistry.register("reconciliationService", reconciliationService);
        log.info("ReconciliationService registered in ServiceRegistry for MDB access");
    }

    @PreDestroy
    public void destroy() {
        ServiceRegistry.unregister("reconciliationService");
        log.info("ReconciliationService unregistered from ServiceRegistry");
    }
}
