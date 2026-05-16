package com.settlement.entity;

public enum InstructionStatus {
    PENDING,
    SUBMITTING,
    SENT,
    MATCHED,
    FAILED,
    RETRYING,
    CANCELLED,
    DVP_LOCKED
}
