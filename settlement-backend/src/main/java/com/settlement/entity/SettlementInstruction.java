package com.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "SETTLEMENT_INSTRUCTION")
public class SettlementInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TRADE_REF", nullable = false, unique = true, length = 50)
    private String tradeRef;

    @Column(name = "ISIN", nullable = false, length = 12)
    private String isin;

    @Column(name = "SETTLEMENT_DATE", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "QUANTITY", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Column(name = "COUNTERPARTY", nullable = false, length = 100)
    private String counterparty;

    @Column(name = "BIC_CODE", nullable = false, length = 11)
    private String bicCode;

    @Column(name = "DIRECTION", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private Direction direction;

    @Column(name = "STATUS", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private InstructionStatus status = InstructionStatus.PENDING;

    @Column(name = "ACCOUNT_ID", nullable = false, length = 50)
    private String accountId;

    @Lob
    @Column(name = "MT541_RAW")
    private String mt541Raw;

    @Lob
    @Column(name = "MT548_RAW")
    private String mt548Raw;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
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

    public String getTradeRef() {
        return tradeRef;
    }

    public void setTradeRef(String tradeRef) {
        this.tradeRef = tradeRef;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public LocalDate getSettlementDate() {
        return settlementDate;
    }

    public void setSettlementDate(LocalDate settlementDate) {
        this.settlementDate = settlementDate;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getCounterparty() {
        return counterparty;
    }

    public void setCounterparty(String counterparty) {
        this.counterparty = counterparty;
    }

    public String getBicCode() {
        return bicCode;
    }

    public void setBicCode(String bicCode) {
        this.bicCode = bicCode;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public InstructionStatus getStatus() {
        return status;
    }

    public void setStatus(InstructionStatus status) {
        this.status = status;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getMt541Raw() {
        return mt541Raw;
    }

    public void setMt541Raw(String mt541Raw) {
        this.mt541Raw = mt541Raw;
    }

    public String getMt548Raw() {
        return mt548Raw;
    }

    public void setMt548Raw(String mt548Raw) {
        this.mt548Raw = mt548Raw;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
