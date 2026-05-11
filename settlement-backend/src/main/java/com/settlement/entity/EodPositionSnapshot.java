package com.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * End-of-day position snapshot per (account, isin). Serves as the baseline
 * for next-day incremental reconciliation: any subsequent movements are
 * summed only from this snapshot's business date forward.
 */
@Entity
@Table(name = "EOD_POSITION_SNAPSHOT", uniqueConstraints = {
    @UniqueConstraint(name = "UK_EOD_POS", columnNames = {"BUSINESS_DATE", "ACCOUNT_ID", "ISIN"})
})
public class EodPositionSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "BUSINESS_DATE", nullable = false)
    private LocalDate businessDate;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 50)
    private String accountId;

    @Column(name = "ISIN", nullable = false, length = 12)
    private String isin;

    @Column(name = "BALANCE", nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public EodPositionSnapshot() {
    }

    public EodPositionSnapshot(LocalDate businessDate, String accountId, String isin, BigDecimal balance) {
        this.businessDate = businessDate;
        this.accountId = accountId;
        this.isin = isin;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getAccountId() { return accountId; }
    public String getIsin() { return isin; }
    public BigDecimal getBalance() { return balance; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
