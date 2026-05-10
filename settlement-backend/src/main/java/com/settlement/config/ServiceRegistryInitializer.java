package com.settlement.config;

import com.settlement.bridge.ReconciliationHandler;
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
 *
 * <p>Services are registered under their shared interface types (defined in
 * settlement-common) so the EJB module can invoke them without reflection.
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
        ServiceRegistry.register(ReconciliationHandler.class, reconciliationService);
        log.info("ServiceRegistry initialized: ReconciliationHandler registered for MDB access");

        verify();
    }

    @PreDestroy
    public void destroy() {
        ServiceRegistry.unregister(ReconciliationHandler.class);
        log.info("ServiceRegistry cleared on shutdown");
    }

    private void verify() {
        if (!ServiceRegistry.isRegistered(ReconciliationHandler.class)) {
            throw new IllegalStateException(
                    "ServiceRegistry startup verification failed: ReconciliationHandler not available");
        }
        log.info("ServiceRegistry startup verification passed");
    }
}
