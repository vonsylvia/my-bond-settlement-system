package com.settlement.service;

import java.math.BigDecimal;

/**
 * Abstraction for the HKMA CHATS (Clearing House Automated Transfer System)
 * real-time gross settlement (RTGS) interface. Supports HKD, USD, EUR, and CNY.
 *
 * <p>In production, implementations would integrate with the actual CHATS
 * system via SWIFT FIN or API. This interface allows the settlement engine
 * to verify and reserve funds before completing DVP settlement.
 */
public interface ChatsGateway {

    /**
     * Reserves (earmarks) cash for a pending DVP settlement.
     * The amount is blocked but not yet transferred.
     *
     * @return true if reservation succeeded (sufficient funds available)
     */
    boolean reserveFunds(String accountId, String currency, BigDecimal amount, String tradeRef);

    /**
     * Releases a previously made reservation (e.g. on settlement failure).
     */
    void releaseFunds(String accountId, String currency, BigDecimal amount, String tradeRef);

    /**
     * Executes the actual fund transfer after securities leg is confirmed.
     * Must be called only after a successful reservation.
     *
     * @return true if transfer completed successfully
     */
    boolean transferFunds(String fromAccount, String toAccount,
                          String currency, BigDecimal amount, String tradeRef);

    /**
     * Checks whether the account has sufficient available balance
     * (including any existing reservations).
     */
    boolean hasSufficientFunds(String accountId, String currency, BigDecimal amount);
}
