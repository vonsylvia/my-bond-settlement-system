package com.settlement.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class SettlementResponse {

    private String tradeRef;
    private String isin;
    private LocalDate settlementDate;
    private BigDecimal quantity;
    private String counterparty;
    private String bicCode;
    private String direction;
    private String status;
    private String accountId;
    private int retryCount;
    private String failureReason;
    private String currency;
    private BigDecimal settlementAmount;
    private String paymentType;
    private String requestedStandard;
    private String resolvedStandard;
    private LocalDateTime finalityTimestamp;
    @JsonProperty("isFinal")
    private boolean isFinal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getSettlementAmount() { return settlementAmount; }
    public void setSettlementAmount(BigDecimal settlementAmount) { this.settlementAmount = settlementAmount; }

    public String getPaymentType() { return paymentType; }
    public void setPaymentType(String paymentType) { this.paymentType = paymentType; }

    public String getRequestedStandard() { return requestedStandard; }
    public void setRequestedStandard(String requestedStandard) { this.requestedStandard = requestedStandard; }

    public String getResolvedStandard() { return resolvedStandard; }
    public void setResolvedStandard(String resolvedStandard) { this.resolvedStandard = resolvedStandard; }

    public LocalDateTime getFinalityTimestamp() { return finalityTimestamp; }
    public void setFinalityTimestamp(LocalDateTime finalityTimestamp) { this.finalityTimestamp = finalityTimestamp; }

    public boolean isFinal() { return isFinal; }
    public void setFinal(boolean isFinal) { this.isFinal = isFinal; }
}
