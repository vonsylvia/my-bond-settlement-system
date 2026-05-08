package com.settlement.bridge;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe service registry for bridging Spring beans and EJB components
 * across EAR module classloader boundaries. This class resides in a shared
 * library JAR (EAR lib/) visible to all modules.
 *
 * <p>The WAR module registers Spring-managed beans on startup; the MDB retrieves
 * them at message processing time without needing Spring classloader access.
 */
public final class ServiceRegistry {

    private static final ConcurrentMap<String, Object> SERVICES = new ConcurrentHashMap<>();

    private ServiceRegistry() {}

    public static void register(String name, Object service) {
        SERVICES.put(name, service);
    }

    @SuppressWarnings("unchecked")
    public static <T> T lookup(String name, Class<T> type) {
        Object service = SERVICES.get(name);
        if (service == null) {
            return null;
        }
        return type.cast(service);
    }

    public static void unregister(String name) {
        SERVICES.remove(name);
    }

    public static void clear() {
        SERVICES.clear();
    }
}
