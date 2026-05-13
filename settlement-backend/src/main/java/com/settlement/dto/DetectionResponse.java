package com.settlement.dto;

public class DetectionResponse {

    private String standard;
    private String messageType;
    private String tradeRef;

    public DetectionResponse() {
    }

    public DetectionResponse(String standard, String messageType, String tradeRef) {
        this.standard = standard;
        this.messageType = messageType;
        this.tradeRef = tradeRef;
    }

    public String getStandard() {
        return standard;
    }

    public void setStandard(String standard) {
        this.standard = standard;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTradeRef() {
        return tradeRef;
    }

    public void setTradeRef(String tradeRef) {
        this.tradeRef = tradeRef;
    }
}
