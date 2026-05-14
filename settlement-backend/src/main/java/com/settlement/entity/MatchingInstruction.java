package com.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "MATCHING_INSTRUCTION", indexes = {
    @Index(name = "IDX_MI_ISIN_DATE", columnList = "ISIN, SETTLEMENT_DATE"),
    @Index(name = "IDX_MI_STATUS", columnList = "MATCHING_STATUS"),
    @Index(name = "IDX_MI_COUNTERPARTY", columnList = "COUNTERPARTY_BIC")
})
public class MatchingInstruction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TRADE_REF", nullable = false, length = 50)
    private String tradeRef;

    @Column(name = "ISIN", nullable = false, length = 12)
    private String isin;

    @Column(name = "SETTLEMENT_DATE", nullable = false)
    private LocalDate settlementDate;

    @Column(name = "QUANTITY", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Column(name = "AMOUNT", precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "CURRENCY", length = 3)
    private String currency = "HKD";

    @Column(name = "COUNTERPARTY_BIC", nullable = false, length = 11)
    private String counterpartyBic;

    @Column(name = "SUBMITTER_BIC", nullable = false, length = 11)
    private String submitterBic;

    @Enumerated(EnumType.STRING)
    @Column(name = "DIRECTION", nullable = false, length = 10)
    private Direction direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "MATCHING_STATUS", nullable = false, length = 20)
    private MatchingStatus matchingStatus = MatchingStatus.UNMATCHED;

    @Column(name = "MATCHED_WITH_ID")
    private Long matchedWithId;

    @Column(name = "INSTRUCTION_ID")
    private Long instructionId;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public MatchingInstruction() {
    }

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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCounterpartyBic() {
        return counterpartyBic;
    }

    public void setCounterpartyBic(String counterpartyBic) {
        this.counterpartyBic = counterpartyBic;
    }

    public String getSubmitterBic() {
        return submitterBic;
    }

    public void setSubmitterBic(String submitterBic) {
        this.submitterBic = submitterBic;
    }

    public Direction getDirection() {
        return direction;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public MatchingStatus getMatchingStatus() {
        return matchingStatus;
    }

    public void setMatchingStatus(MatchingStatus matchingStatus) {
        this.matchingStatus = matchingStatus;
    }

    public Long getMatchedWithId() {
        return matchedWithId;
    }

    public void setMatchedWithId(Long matchedWithId) {
        this.matchedWithId = matchedWithId;
    }

    public Long getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(Long instructionId) {
        this.instructionId = instructionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
