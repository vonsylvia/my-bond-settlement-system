package com.settlement.dto;

import java.math.BigDecimal;

public class PositionDiscrepancy {

    private String accountId;
    private String isin;
    private BigDecimal cachedBalance;
    private BigDecimal ledgerBalance;
    private BigDecimal difference;

    public PositionDiscrepancy() {
    }

    public PositionDiscrepancy(String accountId, String isin,
                               BigDecimal cachedBalance, BigDecimal ledgerBalance) {
        this.accountId = accountId;
        this.isin = isin;
        this.cachedBalance = cachedBalance;
        this.ledgerBalance = ledgerBalance;
        this.difference = cachedBalance.subtract(ledgerBalance);
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public BigDecimal getCachedBalance() {
        return cachedBalance;
    }

    public void setCachedBalance(BigDecimal cachedBalance) {
        this.cachedBalance = cachedBalance;
    }

    public BigDecimal getLedgerBalance() {
        return ledgerBalance;
    }

    public void setLedgerBalance(BigDecimal ledgerBalance) {
        this.ledgerBalance = ledgerBalance;
    }

    public BigDecimal getDifference() {
        return difference;
    }

    public void setDifference(BigDecimal difference) {
        this.difference = difference;
    }
}
