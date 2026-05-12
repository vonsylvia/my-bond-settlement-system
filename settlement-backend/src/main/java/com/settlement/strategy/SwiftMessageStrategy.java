package com.settlement.strategy;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.entity.MessageStandard;

/**
 * Strategy interface for building and parsing SWIFT messages.
 * Implementations handle either FIN (MT) or ISO 20022 (MX) formats.
 *
 * <p>All methods work with format-independent Canonical models rather than
 * JPA entities — ensuring the message layer has no dependency on persistence.
 */
public interface SwiftMessageStrategy {

    MessageStandard getStandard();

    /**
     * Builds an outbound settlement instruction message from Canonical data.
     */
    String buildSettlementInstruction(CanonicalSettlement settlement);

    /**
     * Returns the outbound message type identifier (e.g. "MT541", "sese.023.001.09").
     */
    String getOutboundMessageType(CanonicalSettlement settlement);

    /**
     * Returns the expected inbound status reply type (e.g. "MT548", "sese.024.001.10").
     */
    String getInboundStatusType();

    /**
     * Parses an inbound status reply into a Canonical status advice.
     */
    CanonicalStatusAdvice parseStatusReply(String rawPayload);

    /**
     * Extracts the trade reference from an inbound reply payload.
     * Returns {@code null} if the trade reference cannot be determined
     * (the caller must handle this as an unprocessable message).
     */
    String extractTradeRef(String rawPayload);
}
