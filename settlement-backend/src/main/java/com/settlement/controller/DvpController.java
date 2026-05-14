package com.settlement.controller;

import com.settlement.dao.CashAccountDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.CashAccount;
import com.settlement.entity.SettlementInstruction;
import com.settlement.service.DvpSettlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dvp")
public class DvpController {

    private final DvpSettlementService dvpService;
    private final SettlementInstructionDao instructionDao;
    private final CashAccountDao cashAccountDao;

    public DvpController(DvpSettlementService dvpService,
                         SettlementInstructionDao instructionDao,
                         CashAccountDao cashAccountDao) {
        this.dvpService = dvpService;
        this.instructionDao = instructionDao;
        this.cashAccountDao = cashAccountDao;
    }

    @PostMapping("/{tradeRef}/lock")
    public ResponseEntity<Map<String, String>> lockForDvp(@PathVariable String tradeRef) {
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new com.settlement.exception.ResourceNotFoundException("Instruction not found: " + tradeRef));
        dvpService.lockForDvp(instruction);
        return ResponseEntity.ok(Map.of("status", "DVP_LOCKED", "tradeRef", tradeRef));
    }

    @PostMapping("/{tradeRef}/complete")
    public ResponseEntity<Map<String, String>> completeDvp(@PathVariable String tradeRef) {
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new com.settlement.exception.ResourceNotFoundException("Instruction not found: " + tradeRef));
        dvpService.completeDvp(instruction);
        return ResponseEntity.ok(Map.of("status", "MATCHED", "tradeRef", tradeRef, "finalized", "true"));
    }

    @PostMapping("/{tradeRef}/rollback")
    public ResponseEntity<Map<String, String>> rollbackDvp(@PathVariable String tradeRef) {
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new com.settlement.exception.ResourceNotFoundException("Instruction not found: " + tradeRef));
        dvpService.rollbackDvp(instruction);
        return ResponseEntity.ok(Map.of("status", "FAILED", "tradeRef", tradeRef));
    }

    @GetMapping("/cash-accounts")
    public ResponseEntity<List<CashAccount>> listCashAccounts(@RequestParam(required = false) String accountId) {
        List<CashAccount> accounts;
        if (accountId != null && !accountId.isBlank()) {
            accounts = cashAccountDao.findByAccount(accountId);
        } else {
            accounts = cashAccountDao.findAll();
        }
        return ResponseEntity.ok(accounts);
    }
}
