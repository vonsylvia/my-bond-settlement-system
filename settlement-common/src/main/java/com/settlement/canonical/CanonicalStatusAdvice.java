package com.settlement.canonical;

/**
 * Canonical (format-independent) result of parsing a settlement status reply.
 * Produced by Strategy implementations from MT548 or sese.024 payloads.
 */
public record CanonicalStatusAdvice(
        StatusOutcome outcome,
        String transactionId,
        String statusCode,
        String reasonCode,
        String reasonText,
        String isin,
        String counterpartyBic
) {
    public enum StatusOutcome {
        MATCHED,
        FAILED,
        PENDING,
        UNKNOWN
    }

    public static CanonicalStatusAdvice matched(String transactionId, String statusCode) {
        return new CanonicalStatusAdvice(
                StatusOutcome.MATCHED, transactionId, statusCode, null, null, null, null);
    }

    public static CanonicalStatusAdvice failed(String transactionId, String statusCode, String reasonText) {
        return new CanonicalStatusAdvice(
                StatusOutcome.FAILED, transactionId, statusCode, null, reasonText, null, null);
    }

    public static CanonicalStatusAdvice pending(String transactionId, String statusCode) {
        return new CanonicalStatusAdvice(
                StatusOutcome.PENDING, transactionId, statusCode, null, null, null, null);
    }

    public static CanonicalStatusAdvice unknown(String transactionId, String reasonText) {
        return new CanonicalStatusAdvice(
                StatusOutcome.UNKNOWN, transactionId, null, null, reasonText, null, null);
    }
}
