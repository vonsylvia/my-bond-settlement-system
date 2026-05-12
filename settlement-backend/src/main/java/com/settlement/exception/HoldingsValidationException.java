package com.settlement.exception;

/**
 * Thrown when a settlement cannot proceed due to a business-rule violation
 * in the holdings layer (e.g. insufficient balance, missing holding record).
 * This is NOT a transient/infrastructure error — retrying will not help.
 */
public class HoldingsValidationException extends RuntimeException {

    public HoldingsValidationException(String message) {
        super(message);
    }
}
