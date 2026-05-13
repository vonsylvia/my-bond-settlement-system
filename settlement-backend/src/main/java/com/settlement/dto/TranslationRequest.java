package com.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TranslationRequest {

    @NotBlank(message = "Raw payload is required")
    private String rawPayload;

    @Pattern(regexp = "^(MT|MX)?$", message = "Target standard must be MT or MX")
    private String targetStandard;

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }

    public String getTargetStandard() {
        return targetStandard;
    }

    public void setTargetStandard(String targetStandard) {
        this.targetStandard = targetStandard;
    }
}
