package com.settlement.entity;

public enum InstructionStatus {
    PENDING,
    SUBMITTING,
    SENT,
    MATCHED,
    FAILED,
    RETRYING,
    CANCELLED,
    PARTIALLY_SETTLED,
    DVP_LOCKED
}
