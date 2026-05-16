package com.settlement.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class OpenApiSettlementRequest {

    @NotBlank(message = "Client reference is required")
    @Size(max = 100, message = "Client reference must not exceed 100 characters")
    private String clientReference;

    @NotBlank(message = "ISIN is required")
    @Pattern(regexp = "^[A-Z]{2}[A-Z0-9]{9}[0-9]$", message = "Invalid ISIN format")
    private String isin;

    @NotNull(message = "Settlement date is required")
    @FutureOrPresent(message = "Settlement date must be today or in the future")
    private LocalDate settlementDate;

    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.01", message = "Quantity must be positive")
    private BigDecimal quantity;

    @NotBlank(message = "Counterparty is required")
    @Size(max = 100, message = "Counterparty name must not exceed 100 characters")
    private String counterparty;

    @NotBlank(message = "BIC code is required")
    @Pattern(regexp = "^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$", message = "Invalid BIC/SWIFT code format")
    private String bicCode;

    @NotBlank(message = "Direction is required")
    @Pattern(regexp = "^(BUY|SELL)$", message = "Direction must be BUY or SELL")
    private String direction;

    @NotBlank(message = "Account ID is required")
    @Size(max = 50, message = "Account ID must not exceed 50 characters")
    private String accountId;

    @Pattern(regexp = "^(MT|MX)$", message = "Preferred standard must be MT or MX")
    private String preferredStandard;

    @Pattern(regexp = "^(HKD|USD|EUR|CNY)?$", message = "Currency must be HKD, USD, EUR, or CNY")
    private String currency;

    @DecimalMin(value = "0.00", inclusive = true, message = "Settlement amount must be non-negative")
    private BigDecimal settlementAmount;

    @Pattern(regexp = "^(AGAINST_PAYMENT|FREE_OF_PAYMENT)?$", message = "Payment type must be AGAINST_PAYMENT or FREE_OF_PAYMENT")
    private String paymentType;

    public String getClientReference() {
        return clientReference;
    }

    public void setClientReference(String clientReference) {
        this.clientReference = clientReference;
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

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getPreferredStandard() {
        return preferredStandard;
    }

    public void setPreferredStandard(String preferredStandard) {
        this.preferredStandard = preferredStandard;
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
}
