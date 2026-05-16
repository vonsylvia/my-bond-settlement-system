package com.settlement.entity;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "COUNTERPARTY_CAPABILITY")
public class CounterpartyCapability {

    @Id
    @Column(name = "BIC_CODE", length = 11)
    private String bicCode;

    @Column(name = "PARTICIPANT_NAME", nullable = false, length = 100)
    private String participantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "SUPPORTED_STANDARD", nullable = false, length = 10)
    private SupportedStandard supportedStandard = SupportedStandard.DUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREFERRED_STANDARD", nullable = false, length = 5)
    private MessageStandard preferredStandard = MessageStandard.MT;

    @Column(name = "EFFECTIVE_DATE", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active = true;

    public CounterpartyCapability() {}

    public CounterpartyCapability(String bicCode, String participantName,
                                   SupportedStandard supportedStandard,
                                   MessageStandard preferredStandard) {
        this.bicCode = bicCode;
        this.participantName = participantName;
        this.supportedStandard = supportedStandard;
        this.preferredStandard = preferredStandard;
        this.effectiveDate = LocalDate.now();
    }

    /**
     * Resolves the actual message standard to use when sending to this counterparty.
     * Takes effective date into account — if the capability is not yet effective,
     * falls back to MT (the legacy default).
     */
    public MessageStandard resolveOutboundStandard() {
        if (effectiveDate != null && effectiveDate.isAfter(LocalDate.now())) {
            return MessageStandard.MT;
        }
        return switch (supportedStandard) {
            case MT_ONLY -> MessageStandard.MT;
            case MX_ONLY -> MessageStandard.MX;
            case DUAL -> preferredStandard;
        };
    }

    public String getBicCode() { return bicCode; }
    public void setBicCode(String bicCode) { this.bicCode = bicCode; }

    public String getParticipantName() { return participantName; }
    public void setParticipantName(String participantName) { this.participantName = participantName; }

    public SupportedStandard getSupportedStandard() { return supportedStandard; }
    public void setSupportedStandard(SupportedStandard supportedStandard) { this.supportedStandard = supportedStandard; }

    public MessageStandard getPreferredStandard() { return preferredStandard; }
    public void setPreferredStandard(MessageStandard preferredStandard) { this.preferredStandard = preferredStandard; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
