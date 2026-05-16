package com.settlement.entity;

import com.settlement.canonical.PaymentType;
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

    @Column(name = "PARTICIPANT_ID", length = 50)
    private String participantId;

    @Column(name = "CLIENT_REFERENCE", length = 100)
    private String clientReference;

    @Column(name = "OPEN_API_REQUEST_HASH", length = 64)
    private String openApiRequestHash;

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

    @Column(name = "PREFERRED_STANDARD", nullable = false, length = 5)
    @Enumerated(EnumType.STRING)
    private MessageStandard preferredStandard = MessageStandard.MT;

    @Column(name = "RETRY_COUNT", nullable = false)
    private int retryCount = 0;

    @Column(name = "FAILURE_REASON", length = 1000)
    private String failureReason;

    @Column(name = "CURRENCY", nullable = false, length = 3)
    private String currency = "HKD";

    @Column(name = "SETTLEMENT_AMOUNT", precision = 18, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "PAYMENT_TYPE", nullable = false, length = 20)
    private String paymentType = PaymentType.AGAINST_PAYMENT.name();

    @Column(name = "FINALITY_TIMESTAMP")
    private LocalDateTime finalityTimestamp;

    @Column(name = "IS_FINAL", nullable = false)
    private boolean isFinal = false;

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

    public String getParticipantId() {
        return participantId;
    }

    public void setParticipantId(String participantId) {
        this.participantId = participantId;
    }

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
    }

    public String getOpenApiRequestHash() {
        return openApiRequestHash;
    }

    public void setOpenApiRequestHash(String openApiRequestHash) {
        this.openApiRequestHash = openApiRequestHash;
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

    public MessageStandard getPreferredStandard() {
        return preferredStandard;
    }

    public void setPreferredStandard(MessageStandard preferredStandard) {
        this.preferredStandard = preferredStandard;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSettlementAmount() {
        return settlementAmount;
    }

    public void setSettlementAmount(BigDecimal settlementAmount) {
        this.settlementAmount = settlementAmount;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public LocalDateTime getFinalityTimestamp() {
        return finalityTimestamp;
    }

    public void setFinalityTimestamp(LocalDateTime finalityTimestamp) {
        this.finalityTimestamp = finalityTimestamp;
    }

    public boolean isFinal() {
        return isFinal;
    }

    public void setFinal(boolean isFinal) {
        this.isFinal = isFinal;
    }
}
