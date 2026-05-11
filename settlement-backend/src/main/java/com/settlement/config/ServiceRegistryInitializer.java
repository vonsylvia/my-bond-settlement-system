package com.settlement.config;

import com.settlement.bridge.ReconciliationHandler;
import com.settlement.bridge.ServiceRegistry;
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
 * Injecting the interface (not the concrete class) avoids JDK proxy type
 * mismatch when Spring AOP creates transactional proxies.
 */
@Component
public class ServiceRegistryInitializer {

    private static final Logger log = LoggerFactory.getLogger(ServiceRegistryInitializer.class);

    private final ReconciliationHandler reconciliationService;

    public ServiceRegistryInitializer(ReconciliationHandler reconciliationService) {
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
