package com.settlement.canonical;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Canonical (format-independent) representation of a settlement instruction.
 * Contains the superset of fields needed by both MT (FIN) and MX (ISO 20022).
 * MT-only flows leave MX-specific fields null; MX flows populate them.
 *
 * <p>This model is the single point of truth between business entities and
 * message construction/parsing. Strategy implementations translate to/from
 * this model without knowing about JPA entities.
 */
public record CanonicalSettlement(
        String transactionId,
        String isin,
        LocalDate settlementDate,
        BigDecimal quantity,
        SettlementDirection direction,
        PaymentType paymentType,
        PartyInfo instructingParty,
        PartyInfo counterparty,
        String safekeepingAccount,
        String cashAccount,
        String placeOfTrade,
        String placeOfClearing
) {
}
