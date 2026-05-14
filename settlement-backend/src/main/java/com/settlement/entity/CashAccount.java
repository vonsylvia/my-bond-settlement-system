package com.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "CASH_ACCOUNT", uniqueConstraints = {
    @UniqueConstraint(name = "UK_CASH_ACCOUNT", columnNames = {"ACCOUNT_ID", "CURRENCY"})
})
public class CashAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 50)
    private String accountId;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency;

    @Column(name = "BALANCE", nullable = false, precision = 18, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public CashAccount() {
    }

    public CashAccount(String accountId, String currency) {
        this.accountId = accountId;
        this.currency = currency;
        this.balance = BigDecimal.ZERO;
    }

    @PrePersist
    protected void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
