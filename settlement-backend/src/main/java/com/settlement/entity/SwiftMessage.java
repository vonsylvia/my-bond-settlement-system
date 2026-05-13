package com.settlement.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SWIFT_MESSAGE")
public class SwiftMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "INSTRUCTION_ID", nullable = false)
    private Long instructionId;

    @Column(name = "TRADE_REF", nullable = false, length = 50)
    private String tradeRef;

    @Column(name = "MESSAGE_STANDARD", nullable = false, length = 5)
    @Enumerated(EnumType.STRING)
    private MessageStandard messageStandard;

    @Column(name = "MESSAGE_TYPE", nullable = false, length = 50)
    private String messageType;

    @Column(name = "DIRECTION", nullable = false, length = 10)
    @Enumerated(EnumType.STRING)
    private MessageDirection direction;

    @Lob
    @Column(name = "RAW_PAYLOAD", nullable = false)
    private String rawPayload;

    @Column(name = "NAMESPACE", length = 200)
    private String namespace;

    @Column(name = "BUSINESS_AREA", length = 10)
    private String businessArea;

    @Column(name = "PARSED_STATUS", length = 30)
    private String parsedStatus;

    @Column(name = "PARSED_REASON", length = 500)
    private String parsedReason;

    @Column(name = "SEQUENCE_NO", nullable = false)
    private int sequenceNo = 1;

    @Column(name = "IS_TRANSLATED", nullable = false)
    private boolean translated = false;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public SwiftMessage() {}

    public SwiftMessage(Long instructionId, String tradeRef,
                        MessageStandard messageStandard, String messageType,
                        MessageDirection direction, String rawPayload) {
        this.instructionId = instructionId;
        this.tradeRef = tradeRef;
        this.messageStandard = messageStandard;
        this.messageType = messageType;
        this.direction = direction;
        this.rawPayload = rawPayload;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getInstructionId() { return instructionId; }
    public void setInstructionId(Long instructionId) { this.instructionId = instructionId; }

    public String getTradeRef() { return tradeRef; }
    public void setTradeRef(String tradeRef) { this.tradeRef = tradeRef; }

    public MessageStandard getMessageStandard() { return messageStandard; }
    public void setMessageStandard(MessageStandard messageStandard) { this.messageStandard = messageStandard; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public MessageDirection getDirection() { return direction; }
    public void setDirection(MessageDirection direction) { this.direction = direction; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public String getNamespace() { return namespace; }
    public void setNamespace(String namespace) { this.namespace = namespace; }

    public String getBusinessArea() { return businessArea; }
    public void setBusinessArea(String businessArea) { this.businessArea = businessArea; }

    public String getParsedStatus() { return parsedStatus; }
    public void setParsedStatus(String parsedStatus) { this.parsedStatus = parsedStatus; }

    public String getParsedReason() { return parsedReason; }
    public void setParsedReason(String parsedReason) { this.parsedReason = parsedReason; }

    public int getSequenceNo() { return sequenceNo; }
    public void setSequenceNo(int sequenceNo) { this.sequenceNo = sequenceNo; }

    public boolean isTranslated() { return translated; }
    public void setTranslated(boolean translated) { this.translated = translated; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
