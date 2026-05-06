package com.settlement.controller;

import com.settlement.dto.*;
import com.settlement.entity.SettlementInstruction;
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

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @PostMapping("/settlement")
    public ResponseEntity<SettlementResponse> createSettlement(@Valid @RequestBody SettlementRequest request) {
        SettlementInstruction instruction = settlementService.submitInstruction(request);
        SettlementResponse response = toResponse(instruction);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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
        response.setCreatedAt(instruction.getCreatedAt());
        response.setUpdatedAt(instruction.getUpdatedAt());
        return response;
    }
}
