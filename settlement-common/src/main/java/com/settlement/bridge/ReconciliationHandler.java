package com.settlement.bridge;

/**
 * Contract for processing inbound SWIFT MT548 settlement status replies.
 *
 * <p>This interface resides in the shared EAR library so both the WAR module
 * (which provides the implementation) and the EJB module (which consumes it
 * via {@link ServiceRegistry}) share the same type — eliminating the need
 * for reflective invocation across classloader boundaries.
 */
public interface ReconciliationHandler {

    /**
     * Processes an incoming MT548 reply message, reconciles it against the
     * original settlement instruction, and updates bond holdings accordingly.
     *
     * @param correlationId JMS correlation ID linking to the original MT541
     * @param mt548RawMessage raw SWIFT MT548 message body
     */
    void processSwiftReply(String correlationId, String mt548RawMessage);
}
