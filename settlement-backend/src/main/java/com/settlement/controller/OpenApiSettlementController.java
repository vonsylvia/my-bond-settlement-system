package com.settlement.controller;

import com.settlement.dto.OpenApiSettlementRequest;
import com.settlement.dto.OpenApiSettlementResponse;
import com.settlement.dto.SettlementRequest;
import com.settlement.dto.SettlementResponse;
import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.BusinessException;
import com.settlement.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/open")
public class OpenApiSettlementController {

    private final SettlementService settlementService;

    public OpenApiSettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/settlement-instructions")
    public ResponseEntity<OpenApiSettlementResponse> submitInstruction(
            @RequestHeader("X-Participant-Id") String participantId,
            @Valid @RequestBody OpenApiSettlementRequest request) {
        requireParticipant(participantId);
        SettlementInstruction instruction = settlementService.submitOpenApiInstruction(
                participantId, request.getClientReference(), toSettlementRequest(request));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toOpenApiResponse(instruction));
    }

    @GetMapping("/settlement-instructions")
    public ResponseEntity<OpenApiSettlementResponse> getInstructionByClientReference(
            @RequestHeader("X-Participant-Id") String participantId,
            @RequestParam String clientReference) {
        requireParticipant(participantId);
        if (clientReference == null || clientReference.isBlank()) {
            throw new BusinessException("clientReference is required");
        }
        SettlementInstruction instruction = settlementService.findOpenApiInstruction(
                participantId, clientReference);
        return ResponseEntity.ok(toOpenApiResponse(instruction));
    }

    private void requireParticipant(String participantId) {
        if (participantId == null || participantId.isBlank()) {
            throw new BusinessException("X-Participant-Id header is required");
        }
    }

    private SettlementRequest toSettlementRequest(OpenApiSettlementRequest request) {
        SettlementRequest settlementRequest = new SettlementRequest();
        settlementRequest.setIsin(request.getIsin());
        settlementRequest.setSettlementDate(request.getSettlementDate());
        settlementRequest.setQuantity(request.getQuantity());
        settlementRequest.setCounterparty(request.getCounterparty());
        settlementRequest.setBicCode(request.getBicCode());
        settlementRequest.setDirection(request.getDirection());
        settlementRequest.setAccountId(request.getAccountId());
        settlementRequest.setPreferredStandard(request.getPreferredStandard());
        settlementRequest.setCurrency(request.getCurrency());
        settlementRequest.setSettlementAmount(request.getSettlementAmount());
        settlementRequest.setPaymentType(request.getPaymentType());
        return settlementRequest;
    }

    private OpenApiSettlementResponse toOpenApiResponse(SettlementInstruction instruction) {
        OpenApiSettlementResponse response = new OpenApiSettlementResponse();
        response.setInstructionId(instruction.getTradeRef());
        response.setParticipantId(instruction.getParticipantId());
        response.setClientReference(instruction.getClientReference());
        response.setStatus(instruction.getStatus().name());
        response.setAcceptedAt(instruction.getCreatedAt());
        response.setUpdatedAt(instruction.getUpdatedAt());
        response.setSettlement(toSettlementResponse(instruction));
        return response;
    }

    private SettlementResponse toSettlementResponse(SettlementInstruction instruction) {
        SettlementResponse response = new SettlementResponse();
        response.setTradeRef(instruction.getTradeRef());
        response.setIsin(instruction.getIsin());
        response.setSettlementDate(instruction.getSettlementDate());
        response.setQuantity(instruction.getQuantity());
        response.setCounterparty(instruction.getCounterparty());
        response.setBicCode(instruction.getBicCode());
        response.setDirection(instruction.getDirection().name());
        response.setStatus(instruction.getStatus().name());
        response.setAccountId(instruction.getAccountId());
        response.setRetryCount(instruction.getRetryCount());
        response.setFailureReason(instruction.getFailureReason());
        response.setCurrency(instruction.getCurrency());
        response.setSettlementAmount(instruction.getSettlementAmount());
        response.setPaymentType(instruction.getPaymentType());
        response.setPreferredStandard(instruction.getPreferredStandard().name());
        response.setFinalityTimestamp(instruction.getFinalityTimestamp());
        response.setFinal(instruction.isFinal());
        response.setCreatedAt(instruction.getCreatedAt());
        response.setUpdatedAt(instruction.getUpdatedAt());
        return response;
    }
}
