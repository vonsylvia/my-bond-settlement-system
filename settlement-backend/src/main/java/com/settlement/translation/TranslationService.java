package com.settlement.translation;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.CanonicalStatusAdvice;
import com.settlement.entity.MessageStandard;
import com.settlement.strategy.SwiftMessageStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Translates SWIFT messages between MT (FIN) and MX (ISO 20022) formats.
 *
 * <p>Uses the canonical pivot model: detect source format → parse to
 * {@link CanonicalSettlement} → build target format. This leverages the
 * existing {@link SwiftMessageStrategy} implementations so translation
 * rules stay consistent with the settlement pipeline.
 */
@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private final SwiftMessageStrategyFactory strategyFactory;

    public TranslationService(SwiftMessageStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
    }

    /**
     * Translates a raw SWIFT message to the opposite standard (MT→MX or MX→MT).
     */
    public TranslationResult translate(String rawPayload) {
        SwiftMessageStrategy sourceStrategy = strategyFactory.detectStrategy(rawPayload);
        MessageStandard targetStandard = (sourceStrategy.getStandard() == MessageStandard.MT)
                ? MessageStandard.MX
                : MessageStandard.MT;
        return translate(rawPayload, targetStandard);
    }

    /**
     * Translates a raw SWIFT message to the specified target standard.
     *
     * @throws IllegalArgumentException if the source and target are the same standard,
     *                                  or the payload cannot be parsed
     */
    public TranslationResult translate(String rawPayload, MessageStandard targetStandard) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new IllegalArgumentException("Raw payload must not be blank");
        }

        SwiftMessageStrategy sourceStrategy = strategyFactory.detectStrategy(rawPayload);
        MessageStandard sourceStandard = sourceStrategy.getStandard();

        if (sourceStandard == targetStandard) {
            throw new IllegalArgumentException(
                    "Source and target standard are both " + sourceStandard + " — nothing to translate");
        }

        log.info("Translating {} → {}", sourceStandard, targetStandard);

        CanonicalSettlement canonical = sourceStrategy.parseSettlementInstruction(rawPayload);
        String sourceMessageType = sourceStrategy.getOutboundMessageType(canonical);

        SwiftMessageStrategy targetStrategy = strategyFactory.getStrategy(targetStandard);
        String translatedPayload = targetStrategy.buildSettlementInstruction(canonical);
        String targetMessageType = targetStrategy.getOutboundMessageType(canonical);

        log.info("Translation complete: {} ({}) → {} ({}), tradeRef={}",
                sourceStandard, sourceMessageType, targetStandard, targetMessageType,
                canonical.transactionId());

        return new TranslationResult(
                sourceStandard, targetStandard,
                sourceMessageType, targetMessageType,
                translatedPayload, canonical);
    }

    /**
     * Translates a status reply (MT548 / sese.024) to the opposite standard.
     * Used for inbound reply archival: parse → canonical → build in target format.
     */
    public StatusTranslationResult translateStatusReply(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new IllegalArgumentException("Raw payload must not be blank");
        }

        SwiftMessageStrategy sourceStrategy = strategyFactory.detectStrategy(rawPayload);
        MessageStandard sourceStandard = sourceStrategy.getStandard();
        MessageStandard targetStandard = (sourceStandard == MessageStandard.MT)
                ? MessageStandard.MX : MessageStandard.MT;

        CanonicalStatusAdvice canonical = sourceStrategy.parseStatusReply(rawPayload);
        String sourceType = sourceStrategy.getInboundStatusType();

        SwiftMessageStrategy targetStrategy = strategyFactory.getStrategy(targetStandard);
        String translatedPayload = targetStrategy.buildStatusReply(canonical);
        String targetType = targetStrategy.getInboundStatusType();

        log.info("Status translation complete: {} ({}) → {} ({}), tradeRef={}",
                sourceStandard, sourceType, targetStandard, targetType,
                canonical.transactionId());

        return new StatusTranslationResult(
                sourceStandard, targetStandard,
                sourceType, targetType,
                translatedPayload, canonical);
    }

    public record StatusTranslationResult(
            MessageStandard sourceStandard,
            MessageStandard targetStandard,
            String sourceMessageType,
            String targetMessageType,
            String translatedPayload,
            CanonicalStatusAdvice canonical
    ) {
    }

    /**
     * Detects the message standard and type from raw payload without full translation.
     */
    public DetectionResult detect(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new IllegalArgumentException("Raw payload must not be blank");
        }

        SwiftMessageStrategy strategy = strategyFactory.detectStrategy(rawPayload);
        MessageStandard standard = strategy.getStandard();

        CanonicalSettlement canonical = strategy.parseSettlementInstruction(rawPayload);
        String messageType = strategy.getOutboundMessageType(canonical);

        return new DetectionResult(standard, messageType, canonical.transactionId());
    }

    public record DetectionResult(
            MessageStandard standard,
            String messageType,
            String tradeRef
    ) {
    }
}
