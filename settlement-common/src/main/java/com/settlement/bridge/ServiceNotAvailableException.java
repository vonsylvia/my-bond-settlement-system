package com.settlement.bridge;

/**
 * Thrown when a required service has not been registered in {@link ServiceRegistry}
 * at the time of lookup. This typically indicates a startup ordering issue or
 * a missing registration in {@code ServiceRegistryInitializer}.
 */
public class ServiceNotAvailableException extends RuntimeException {

    private final Class<?> serviceType;

    public ServiceNotAvailableException(Class<?> serviceType) {
        super("Required service not registered: " + serviceType.getName()
                + ". Ensure the WAR module has fully started and registered all services.");
        this.serviceType = serviceType;
    }

    public Class<?> getServiceType() {
        return serviceType;
    }
}
