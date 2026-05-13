package com.settlement.swift;

/**
 * SWIFT FIN qualifier and status code constants used in MT5xx message
 * construction and parsing. Centralised here so that every reference
 * is compile-time checked and IDE-discoverable, eliminating the risk
 * of mistyped magic strings.
 *
 * <p>Values are drawn from the SWIFT Standards Release (SRU) scheme;
 * where Prowide's {@code SchemeConstantsX} interfaces provide an
 * identical constant the value is intentionally duplicated here
 * for a single, stable import path.</p>
 */
public final class SwiftConst {

    private SwiftConst() {}

    // ── Tag names (block 4 field identifiers) ──

    /** Reference — e.g. Sender's Message Reference */
    public static final String TAG_20C = "20C";
    /** Function of the Message */
    public static final String TAG_23G = "23G";
    /** Status — e.g. Matching Status */
    public static final String TAG_25D = "25D";
    /** Identification of the Financial Instrument (ISIN) */
    public static final String TAG_35B = "35B";
    /** Quantity of Financial Instrument */
    public static final String TAG_36B = "36B";
    /** Party — identified by BIC */
    public static final String TAG_95P = "95P";
    /** Account — Safekeeping Account */
    public static final String TAG_97A = "97A";
    /** Date — e.g. Settlement Date */
    public static final String TAG_98A = "98A";

    // ── Field qualifiers (used in :20C:, :98A:, :36B:, :95P:, :97A: etc.) ──

    /** Sender's Message Reference (:20C:) */
    public static final String SEME = "SEME";
    /** Settlement Date / Settlement quantity (:98A:, :36B:) */
    public static final String SETT = "SETT";
    /** Delivering Agent (:95P:) */
    public static final String DEAG = "DEAG";
    /** Receiving Agent (:95P:) */
    public static final String REAG = "REAG";
    /** Safekeeping Account (:97A:) */
    public static final String SAFE = "SAFE";

    // ── Quantity type codes (:36B: component 2) ──

    /** Face Amount */
    public static final String FAMT = "FAMT";
    /** Unit */
    public static final String UNIT = "UNIT";

    // ── Function of the Message (:23G:) ──

    /** New Message */
    public static final String NEWM = "NEWM";
    /** Instruction */
    public static final String INST = "INST";

    // ── Matching / settlement status codes (:25D: component 2) ──

    /** Matched (by depository) */
    public static final String MATC = "MATC";
    /** Matching Confirmed */
    public static final String MACH = "MACH";
    /** Not Matched */
    public static final String NMAT = "NMAT";
    /** Rejected */
    public static final String REJT = "REJT";
    /** Cancelled */
    public static final String CANC = "CANC";

    // ── Matching qualifier (:25D: component 1) ──

    /** Matching Status */
    public static final String MTCH = "MTCH";
}
