package com.settlement.controller;

import com.settlement.canonical.CanonicalSettlement;
import com.settlement.dto.DetectionResponse;
import com.settlement.dto.TranslationRequest;
import com.settlement.dto.TranslationResponse;
import com.settlement.entity.MessageStandard;
import com.settlement.translation.TranslationResult;
import com.settlement.translation.TranslationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for MT↔MX message translation.
 * Accepts raw SWIFT FIN or ISO 20022 XML and translates to the opposite format.
 */
@RestController
@RequestMapping("/api/translation")
public class TranslationController {

    private final TranslationService translationService;

    public TranslationController(TranslationService translationService) {
        this.translationService = translationService;
    }

    @PostMapping("/translate")
    public ResponseEntity<TranslationResponse> translate(@Valid @RequestBody TranslationRequest request) {
        TranslationResult result;
        if (request.getTargetStandard() != null && !request.getTargetStandard().isBlank()) {
            MessageStandard target = MessageStandard.valueOf(request.getTargetStandard());
            result = translationService.translate(request.getRawPayload(), target);
        } else {
            result = translationService.translate(request.getRawPayload());
        }

        TranslationResponse response = toResponse(result);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/detect")
    public ResponseEntity<DetectionResponse> detect(@Valid @RequestBody TranslationRequest request) {
        TranslationService.DetectionResult detection =
                translationService.detect(request.getRawPayload());

        DetectionResponse response = new DetectionResponse(
                detection.standard().name(),
                detection.messageType(),
                detection.tradeRef());
        return ResponseEntity.ok(response);
    }

    private TranslationResponse toResponse(TranslationResult result) {
        TranslationResponse response = new TranslationResponse();
        response.setSourceStandard(result.sourceStandard().name());
        response.setTargetStandard(result.targetStandard().name());
        response.setSourceMessageType(result.sourceMessageType());
        response.setTargetMessageType(result.targetMessageType());
        response.setTranslatedPayload(result.translatedPayload());
        response.setCanonical(toCanonicalSummary(result.canonical()));
        return response;
    }

    private TranslationResponse.CanonicalSummary toCanonicalSummary(CanonicalSettlement c) {
        TranslationResponse.CanonicalSummary summary = new TranslationResponse.CanonicalSummary();
        summary.setTransactionId(c.transactionId());
        summary.setIsin(c.isin());
        summary.setSettlementDate(c.settlementDate());
        summary.setQuantity(c.quantity());
        summary.setDirection(c.direction() != null ? c.direction().name() : null);
        summary.setCounterpartyBic(c.counterparty() != null ? c.counterparty().bic() : null);
        summary.setSafekeepingAccount(c.safekeepingAccount());
        return summary;
    }
}
