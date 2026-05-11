package com.settlement.controller;

import com.settlement.dto.*;
import com.settlement.entity.SettlementInstruction;
import com.settlement.reconcile.PositionReconciliationService;
import com.settlement.service.SettlementService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SettlementController {

    private final SettlementService settlementService;
    private final PositionReconciliationService positionReconciliationService;

    public SettlementController(SettlementService settlementService,
                                PositionReconciliationService positionReconciliationService) {
        this.settlementService = settlementService;
        this.positionReconciliationService = positionReconciliationService;
    }

    @PostMapping("/settlement")
    public ResponseEntity<SettlementResponse> createSettlement(@Valid @RequestBody SettlementRequest request) {
        SettlementInstruction instruction = settlementService.submitInstruction(request);
        SettlementResponse response = toResponse(instruction);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping("/settlement/{tradeRef}/retry")
    public ResponseEntity<SettlementResponse> retrySettlement(@PathVariable String tradeRef) {
        SettlementInstruction instruction = settlementService.manualRetry(tradeRef);
        SettlementResponse response = toResponse(instruction);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/settlement/{tradeRef}")
    public ResponseEntity<SettlementResponse> getSettlement(@PathVariable String tradeRef) {
        SettlementInstruction instruction = settlementService.findByTradeRef(tradeRef);
        return ResponseEntity.ok(toResponse(instruction));
    }

    @GetMapping("/settlement")
    public ResponseEntity<PageResponse<SettlementResponse>> listSettlements(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<SettlementInstruction> instructions = settlementService.findAll(page, size);
        long total = settlementService.count();
        List<SettlementResponse> content = instructions.stream().map(this::toResponse).toList();
        return ResponseEntity.ok(new PageResponse<>(content, page, size, total));
    }

    @GetMapping("/holdings")
    public ResponseEntity<List<HoldingResponse>> getHoldings(
            @RequestParam(required = false) String accountId) {
        List<HoldingResponse> holdings = settlementService.getHoldings(accountId);
        return ResponseEntity.ok(holdings);
    }

    @PostMapping("/positions/reconcile")
    public ResponseEntity<ReconciliationResult> reconcilePositions() {
        ReconciliationResult result = positionReconciliationService.reconcile();
        return ResponseEntity.ok(result);
    }

    @PostMapping("/positions/daily-close")
    public ResponseEntity<ReconciliationResult> dailyClose() {
        ReconciliationResult result = positionReconciliationService.dailyClose();
        return ResponseEntity.ok(result);
    }

    private SettlementResponse toResponse(SettlementInstruction instruction) {
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
        response.setCreatedAt(instruction.getCreatedAt());
        response.setUpdatedAt(instruction.getUpdatedAt());
        return response;
    }
}
