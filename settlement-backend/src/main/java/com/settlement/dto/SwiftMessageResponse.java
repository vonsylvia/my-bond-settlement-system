package com.settlement.dto;

import java.time.LocalDateTime;

public class SwiftMessageResponse {
    private Long id;
    private String messageStandard;
    private String messageType;
    private String direction;
    private String rawPayload;
    private boolean translated;
    private String parsedStatus;
    private String parsedReason;
    private int sequenceNo;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMessageStandard() { return messageStandard; }
    public void setMessageStandard(String messageStandard) { this.messageStandard = messageStandard; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public boolean isTranslated() { return translated; }
    public void setTranslated(boolean translated) { this.translated = translated; }

    public String getParsedStatus() { return parsedStatus; }
    public void setParsedStatus(String parsedStatus) { this.parsedStatus = parsedStatus; }

    public String getParsedReason() { return parsedReason; }
    public void setParsedReason(String parsedReason) { this.parsedReason = parsedReason; }

    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
