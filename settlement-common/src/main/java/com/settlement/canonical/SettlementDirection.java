package com.settlement.canonical;

/**
 * Canonical settlement direction — format-independent.
 * MT maps: BUY→RECEIVE, SELL→DELIVER.
 * MX uses RECE/DELI natively.
 */
public enum SettlementDirection {
    RECEIVE,
    DELIVER
}
