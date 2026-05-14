package com.settlement.controller;

import com.settlement.entity.PartialSettlement;
import com.settlement.service.PartialSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/partial-settlement")
public class PartialSettlementController {

    private final PartialSettlementService partialService;

    public PartialSettlementController(PartialSettlementService partialService) {
        this.partialService = partialService;
    }

    @PostMapping("/{instructionId}/shape")
    public ResponseEntity<List<PartialSettlement>> shapeInstruction(
            @PathVariable Long instructionId,
            @RequestParam(required = false) BigDecimal batchSize) {
        List<PartialSettlement> splits;
        if (batchSize != null) {
            splits = partialService.shapeInstruction(instructionId, batchSize);
        } else {
            splits = partialService.shapeInstruction(instructionId);
        }
        return ResponseEntity.ok(splits);
    }

    @PostMapping("/split/{partialId}/complete")
    public ResponseEntity<Map<String, String>> completeSplit(@PathVariable Long partialId) {
        partialService.completeSplit(partialId);
        return ResponseEntity.ok(Map.of("status", "SETTLED", "partialId", partialId.toString()));
    }

    @GetMapping("/{instructionId}")
    public ResponseEntity<List<PartialSettlement>> getSplits(@PathVariable Long instructionId) {
        List<PartialSettlement> splits = partialService.getSplits(instructionId);
        return ResponseEntity.ok(splits);
    }
}
