package com.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CASH_MOVEMENT")
public class CashMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 50)
    private String accountId;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "MOVEMENT_TYPE", nullable = false, length = 6)
    private MovementType movementType;

    @Column(name = "AMOUNT", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "BALANCE_AFTER", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "TRADE_REF", nullable = false, length = 50)
    private String tradeRef;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    public CashMovement() {
    }

    public CashMovement(String accountId, String currency, MovementType movementType,
                        BigDecimal amount, BigDecimal balanceAfter, String tradeRef) {
        this.accountId = accountId;
        this.currency = currency;
        this.movementType = movementType;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.tradeRef = tradeRef;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public MovementType getMovementType() {
        return movementType;
    }

    public void setMovementType(MovementType movementType) {
        this.movementType = movementType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getTradeRef() {
        return tradeRef;
    }

    public void setTradeRef(String tradeRef) {
        this.tradeRef = tradeRef;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
