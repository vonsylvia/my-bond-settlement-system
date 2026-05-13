package com.settlement.dto;

import java.time.LocalDate;

public class CounterpartyCapabilityResponse {
    private String bicCode;
    private String participantName;
    private String supportedStandard;
    private String preferredStandard;
    private String resolvedOutbound;
    private LocalDate effectiveDate;
    private boolean active;

    public String getBicCode() { return bicCode; }
    public void setBicCode(String bicCode) { this.bicCode = bicCode; }

    public String getParticipantName() { return participantName; }
    public void setParticipantName(String participantName) { this.participantName = participantName; }

    public String getSupportedStandard() { return supportedStandard; }
    public void setSupportedStandard(String supportedStandard) { this.supportedStandard = supportedStandard; }

    public String getPreferredStandard() { return preferredStandard; }
    public void setPreferredStandard(String preferredStandard) { this.preferredStandard = preferredStandard; }

    public String getResolvedOutbound() { return resolvedOutbound; }
    public void setResolvedOutbound(String resolvedOutbound) { this.resolvedOutbound = resolvedOutbound; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
