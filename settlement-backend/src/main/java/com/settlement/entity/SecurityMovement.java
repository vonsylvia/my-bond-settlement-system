package com.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Immutable audit journal entry representing a single position change.
 * This table is append-only — rows are never updated or deleted.
 *
 * <p>The authoritative current position lives in {@code BondHolding}.
 * This journal serves as the audit trail and is used by daily
 * reconciliation to verify position integrity:
 * {@code position == eod_snapshot + SUM(movements since snapshot)}.
 */
@Entity
@Table(name = "SECURITY_MOVEMENT", indexes = {
    @Index(name = "IDX_MOVEMENT_ACCOUNT_ISIN", columnList = "ACCOUNT_ID, ISIN"),
    @Index(name = "IDX_MOVEMENT_TRADE_REF", columnList = "TRADE_REF")
})
public class SecurityMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 50)
    private String accountId;

    @Column(name = "ISIN", nullable = false, length = 12)
    private String isin;

    @Enumerated(EnumType.STRING)
    @Column(name = "MOVEMENT_TYPE", nullable = false, length = 6)
    private MovementType movementType;

    @Column(name = "QUANTITY", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Column(name = "BALANCE_AFTER", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "TRADE_REF", nullable = false, length = 50)
    private String tradeRef;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public SecurityMovement() {
    }

    public SecurityMovement(String accountId, String isin, MovementType movementType,
                            BigDecimal quantity, BigDecimal balanceAfter, String tradeRef) {
        this.accountId = accountId;
        this.isin = isin;
        this.movementType = movementType;
        this.quantity = quantity;
        this.balanceAfter = balanceAfter;
        this.tradeRef = tradeRef;
    }

    public Long getId() {
        return id;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getIsin() {
        return isin;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public String getTradeRef() {
        return tradeRef;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
