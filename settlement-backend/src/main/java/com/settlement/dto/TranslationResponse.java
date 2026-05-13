package com.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TranslationResponse {

    private String sourceStandard;
    private String targetStandard;
    private String sourceMessageType;
    private String targetMessageType;
    private String translatedPayload;
    private CanonicalSummary canonical;

    public String getSourceStandard() {
        return sourceStandard;
    }

    public void setSourceStandard(String sourceStandard) {
        this.sourceStandard = sourceStandard;
    }

    public String getTargetStandard() {
        return targetStandard;
    }

    public void setTargetStandard(String targetStandard) {
        this.targetStandard = targetStandard;
    }

    public String getSourceMessageType() {
        return sourceMessageType;
    }

    public void setSourceMessageType(String sourceMessageType) {
        this.sourceMessageType = sourceMessageType;
    }

    public String getTargetMessageType() {
        return targetMessageType;
    }

    public void setTargetMessageType(String targetMessageType) {
        this.targetMessageType = targetMessageType;
    }

    public String getTranslatedPayload() {
        return translatedPayload;
    }

    public void setTranslatedPayload(String translatedPayload) {
        this.translatedPayload = translatedPayload;
    }

    public CanonicalSummary getCanonical() {
        return canonical;
    }

    public void setCanonical(CanonicalSummary canonical) {
        this.canonical = canonical;
    }

    /**
     * Lightweight summary of the canonical settlement data extracted during translation.
     */
    public static class CanonicalSummary {
        private String transactionId;
        private String isin;
        private LocalDate settlementDate;
        private BigDecimal quantity;
        private String direction;
        private String counterpartyBic;
        private String safekeepingAccount;

        public String getTransactionId() {
            return transactionId;
        }

        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
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

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getCounterpartyBic() {
            return counterpartyBic;
        }

        public void setCounterpartyBic(String counterpartyBic) {
            this.counterpartyBic = counterpartyBic;
        }

        public String getSafekeepingAccount() {
            return safekeepingAccount;
        }

        public void setSafekeepingAccount(String safekeepingAccount) {
            this.safekeepingAccount = safekeepingAccount;
        }
    }
}
