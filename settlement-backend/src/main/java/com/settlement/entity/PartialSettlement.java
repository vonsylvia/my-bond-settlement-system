package com.settlement.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PARTIAL_SETTLEMENT", indexes = {
    @Index(name = "IDX_PS_PARENT", columnList = "PARENT_INSTRUCTION_ID")
})
public class PartialSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PARENT_INSTRUCTION_ID", nullable = false)
    private Long parentInstructionId;

    @Column(name = "SPLIT_SEQUENCE", nullable = false)
    private Integer splitSequence;

    @Column(name = "QUANTITY", nullable = false, precision = 18, scale = 2)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private PartialSettlementStatus status = PartialSettlementStatus.PENDING;

    @Column(name = "TRADE_REF", nullable = false, length = 50)
    private String tradeRef;

    @Column(name = "CREATED_AT", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    public PartialSettlement() {
    }

    public PartialSettlement(Long parentInstructionId, Integer splitSequence, BigDecimal quantity, String tradeRef) {
        this.parentInstructionId = parentInstructionId;
        this.splitSequence = splitSequence;
        this.quantity = quantity;
        this.tradeRef = tradeRef;
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

    public Long getParentInstructionId() {
        return parentInstructionId;
    }

    public void setParentInstructionId(Long parentInstructionId) {
        this.parentInstructionId = parentInstructionId;
    }

    public Integer getSplitSequence() {
        return splitSequence;
    }

    public void setSplitSequence(Integer splitSequence) {
        this.splitSequence = splitSequence;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public PartialSettlementStatus getStatus() {
        return status;
    }

    public void setStatus(PartialSettlementStatus status) {
        this.status = status;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
