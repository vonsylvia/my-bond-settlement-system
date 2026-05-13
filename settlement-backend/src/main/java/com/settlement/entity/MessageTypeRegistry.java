package com.settlement.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "MESSAGE_TYPE_REGISTRY")
public class MessageTypeRegistry {

    @Id
    @Column(name = "MESSAGE_TYPE", length = 50)
    private String messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "MESSAGE_STANDARD", nullable = false, length = 5)
    private MessageStandard messageStandard;

    @Column(name = "DESCRIPTION", nullable = false, length = 200)
    private String description;

    @Column(name = "MT_EQUIVALENT", length = 10)
    private String mtEquivalent;

    @Column(name = "MX_EQUIVALENT", length = 50)
    private String mxEquivalent;

    @Column(name = "CATEGORY", nullable = false, length = 30)
    private String category;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public MessageStandard getMessageStandard() {
        return messageStandard;
    }

    public void setMessageStandard(MessageStandard messageStandard) {
        this.messageStandard = messageStandard;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMtEquivalent() {
        return mtEquivalent;
    }

    public void setMtEquivalent(String mtEquivalent) {
        this.mtEquivalent = mtEquivalent;
    }

    public String getMxEquivalent() {
        return mxEquivalent;
    }

    public void setMxEquivalent(String mxEquivalent) {
        this.mxEquivalent = mxEquivalent;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the equivalent message type in the opposite standard,
     * or {@code null} if no mapping is registered.
     */
    public String getEquivalentType() {
        return (messageStandard == MessageStandard.MT) ? mxEquivalent : mtEquivalent;
    }
}
