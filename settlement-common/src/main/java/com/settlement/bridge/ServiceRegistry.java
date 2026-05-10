package com.settlement.bridge;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Type-safe, thread-safe service registry for bridging Spring beans and EJB
 * components across EAR module classloader boundaries.
 *
 * <p>This class resides in a shared library JAR (EAR lib/) visible to all
 * modules. The WAR module registers Spring-managed beans on startup; the MDB
 * retrieves them at message processing time via their interface type.
 *
 * <p>Key design choices:
 * <ul>
 *   <li>Uses {@code Class<T>} as key — eliminates magic string coupling</li>
 *   <li>{@link #lookup} throws on missing service — fast-fail over silent null</li>
 *   <li>ConcurrentHashMap ensures thread-safe concurrent access</li>
 * </ul>
 */
public final class ServiceRegistry {

    private static final ConcurrentMap<Class<?>, Object> SERVICES = new ConcurrentHashMap<>();

    private ServiceRegistry() {}

    /**
     * Registers a service instance under its interface type.
     *
     * @throws IllegalStateException if a service is already registered for the type
     */
    public static <T> void register(Class<T> type, T service) {
        Object prev = SERVICES.putIfAbsent(type, service);
        if (prev != null) {
            throw new IllegalStateException(
                    "Service already registered for type: " + type.getName());
        }
    }

    /**
     * Looks up a registered service by interface type.
     *
     * @throws ServiceNotAvailableException if no service is registered for the type
     */
    public static <T> T lookup(Class<T> type) {
        Object service = SERVICES.get(type);
        if (service == null) {
            throw new ServiceNotAvailableException(type);
        }
        return type.cast(service);
    }

    /**
     * Returns the registered service or {@code null} if not yet available.
     * Use when graceful degradation is preferred over fast-fail.
     */
    public static <T> T lookupOptional(Class<T> type) {
        Object service = SERVICES.get(type);
        return service != null ? type.cast(service) : null;
    }

    /**
     * Checks whether a service is registered for the given type.
     */
    public static boolean isRegistered(Class<?> type) {
        return SERVICES.containsKey(type);
    }

    public static <T> void unregister(Class<T> type) {
        SERVICES.remove(type);
    }

    public static void clear() {
        SERVICES.clear();
    }
}
