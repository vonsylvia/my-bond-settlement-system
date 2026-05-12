package com.settlement.bridge;

/**
 * Contract for processing inbound SWIFT settlement status replies.
 * Supports both MT (FIN) and MX (ISO 20022) message formats.
 *
 * <p>This interface resides in the shared EAR library so both the WAR module
 * (which provides the implementation) and the EJB module (which consumes it
 * via {@link ServiceRegistry}) share the same type — eliminating the need
 * for reflective invocation across classloader boundaries.
 */
public interface ReconciliationHandler {

    /**
     * Processes an incoming SWIFT reply message (MT548 or sese.024),
     * reconciles it against the original settlement instruction,
     * and updates bond holdings accordingly.
     *
     * @param correlationId JMS correlation ID linking to the original instruction
     * @param rawMessage raw SWIFT message body (FIN or XML)
     */
    void processSwiftReply(String correlationId, String rawMessage);
}
