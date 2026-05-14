package com.settlement.controller;

import com.settlement.dao.MatchingInstructionDao;
import com.settlement.entity.MatchingInstruction;
import com.settlement.entity.MatchingStatus;
import com.settlement.service.MatchingEngineService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {

    private final MatchingEngineService matchingService;
    private final MatchingInstructionDao matchingDao;

    public MatchingController(MatchingEngineService matchingService, MatchingInstructionDao matchingDao) {
        this.matchingService = matchingService;
        this.matchingDao = matchingDao;
    }

    @PostMapping
    public ResponseEntity<MatchingInstruction> submitForMatching(@RequestBody MatchingInstruction instruction) {
        MatchingInstruction result = matchingService.submitForMatching(instruction);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result);
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<?> retryMatching(@PathVariable Long id) {
        return matchingService.retryMatching(id)
                .map(m -> ResponseEntity.ok().body((Object) Map.of("matched", true, "matchedWithId", m.getId())))
                .orElse(ResponseEntity.ok().body(Map.of("matched", false)));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelMatching(@PathVariable Long id) {
        matchingService.cancelMatchingInstruction(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<MatchingInstruction>> listAll(@RequestParam(required = false) String status) {
        List<MatchingInstruction> results;
        if (status != null) {
            results = matchingDao.findByStatus(MatchingStatus.valueOf(status));
        } else {
            List<MatchingInstruction> unmatched = matchingDao.findByStatus(MatchingStatus.UNMATCHED);
            List<MatchingInstruction> alleged = matchingDao.findByStatus(MatchingStatus.ALLEGED);
            List<MatchingInstruction> matched = matchingDao.findByStatus(MatchingStatus.MATCHED);
            results = new java.util.ArrayList<>();
            results.addAll(unmatched);
            results.addAll(alleged);
            results.addAll(matched);
        }
        return ResponseEntity.ok(results);
    }

    @GetMapping("/alleged")
    public ResponseEntity<List<MatchingInstruction>> listAlleged() {
        return ResponseEntity.ok(matchingService.findAlleged());
    }

    @GetMapping("/unmatched")
    public ResponseEntity<List<MatchingInstruction>> listUnmatched() {
        return ResponseEntity.ok(matchingService.findUnmatched());
    }
}
