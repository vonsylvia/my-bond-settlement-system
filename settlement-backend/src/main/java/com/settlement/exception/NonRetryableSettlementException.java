package com.settlement.exception;

/**
 * Thrown when a settlement attempt fails with a permanent error that
 * retrying will not resolve (e.g. missing outbound message, invalid state).
 * The processor should mark the instruction as FAILED immediately
 * without further retry attempts.
 */
public class NonRetryableSettlementException extends RuntimeException {

    public NonRetryableSettlementException(String message) {
        super(message);
    }

    public NonRetryableSettlementException(String message, Throwable cause) {
        super(message, cause);
    }
}
