package com.settlement.entity;

public enum AlertEvent {

    SETTLEMENT_EXHAUSTED("SETTLEMENT_EXHAUSTED", AlertSeverity.CRITICAL),
    SETTLEMENT_STATUS_UNKNOWN("SETTLEMENT_STATUS_UNKNOWN", AlertSeverity.HIGH);

    private final String event;
    private final AlertSeverity severity;

    AlertEvent(String event, AlertSeverity severity) {
        this.event = event;
        this.severity = severity;
    }

    public String getEvent() {
        return event;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }
}
