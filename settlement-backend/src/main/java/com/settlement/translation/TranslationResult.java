package com.settlement.translation;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.entity.MessageStandard;

/**
 * Immutable result of a MT↔MX translation operation.
 */
public record TranslationResult(
        MessageStandard sourceStandard,
        MessageStandard targetStandard,
        String sourceMessageType,
        String targetMessageType,
        String translatedPayload,
        CanonicalSettlement canonical
) {
}
