package com.settlement.canonical;

/**
 * Canonical representation of a settlement party.
 * Covers both MT (BIC-only) and MX (BIC + LEI + name + accounts) data.
 */
public record PartyInfo(
        String bic,
        String lei,
        String name,
        String safekeepingAccount
) {
    public static PartyInfo ofBic(String bic) {
        return new PartyInfo(bic, null, null, null);
    }

    public static PartyInfo ofBicAndAccount(String bic, String safekeepingAccount) {
        return new PartyInfo(bic, null, null, safekeepingAccount);
    }
}
