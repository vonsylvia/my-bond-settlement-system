package com.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CounterpartyCapabilityRequest {

    @NotBlank(message = "BIC code is required")
    @Size(min = 8, max = 11, message = "BIC code must be 8-11 characters")
    private String bicCode;

    @NotBlank(message = "Participant name is required")
    @Size(max = 100)
    private String participantName;

    @NotBlank(message = "Supported standard is required")
    @Pattern(regexp = "MT_ONLY|MX_ONLY|DUAL", message = "Must be MT_ONLY, MX_ONLY, or DUAL")
    private String supportedStandard;

    @NotBlank(message = "Preferred standard is required")
    @Pattern(regexp = "MT|MX", message = "Must be MT or MX")
    private String preferredStandard;

    public String getBicCode() { return bicCode; }
    public void setBicCode(String bicCode) { this.bicCode = bicCode; }

    public String getParticipantName() { return participantName; }
    public void setParticipantName(String participantName) { this.participantName = participantName; }

    public String getSupportedStandard() { return supportedStandard; }
    public void setSupportedStandard(String supportedStandard) { this.supportedStandard = supportedStandard; }

    public String getPreferredStandard() { return preferredStandard; }
    public void setPreferredStandard(String preferredStandard) { this.preferredStandard = preferredStandard; }
}
