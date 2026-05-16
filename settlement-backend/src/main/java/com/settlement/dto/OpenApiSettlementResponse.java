package com.settlement.dto;

import java.time.LocalDateTime;

public class OpenApiSettlementResponse {

    private String instructionId;
    private String participantId;
    private String clientReference;
    private String status;
    private LocalDateTime acceptedAt;
    private LocalDateTime updatedAt;
    private SettlementResponse settlement;

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SettlementResponse getSettlement() {
        return settlement;
    }

    public void setSettlement(SettlementResponse settlement) {
        this.settlement = settlement;
    }
}
