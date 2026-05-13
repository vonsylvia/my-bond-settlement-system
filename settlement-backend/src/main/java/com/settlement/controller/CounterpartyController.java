package com.settlement.controller;

import com.settlement.dao.CounterpartyCapabilityDao;
import com.settlement.dto.CounterpartyCapabilityRequest;
import com.settlement.dto.CounterpartyCapabilityResponse;
import com.settlement.entity.CounterpartyCapability;
import com.settlement.entity.MessageStandard;
import com.settlement.entity.SupportedStandard;
import com.settlement.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/counterparty")
public class CounterpartyController {

    private final CounterpartyCapabilityDao capabilityDao;

    public CounterpartyController(CounterpartyCapabilityDao capabilityDao) {
        this.capabilityDao = capabilityDao;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<CounterpartyCapabilityResponse>> listAll() {
        List<CounterpartyCapability> all = capabilityDao.findAll();
        List<CounterpartyCapabilityResponse> response = all.stream()
                .map(this::toResponse).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{bicCode}")
    @Transactional(readOnly = true)
    public ResponseEntity<CounterpartyCapabilityResponse> getByBic(@PathVariable String bicCode) {
        CounterpartyCapability cap = capabilityDao.findByBic(bicCode)
                .or(() -> capabilityDao.findByBicFuzzy(bicCode))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Counterparty not found: " + bicCode));
        return ResponseEntity.ok(toResponse(cap));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<CounterpartyCapabilityResponse> create(
            @Valid @RequestBody CounterpartyCapabilityRequest request) {
        CounterpartyCapability entity = fromRequest(request);
        capabilityDao.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(entity));
    }

    @PutMapping("/{bicCode}")
    @Transactional
    public ResponseEntity<CounterpartyCapabilityResponse> update(
            @PathVariable String bicCode,
            @Valid @RequestBody CounterpartyCapabilityRequest request) {
        CounterpartyCapability existing = capabilityDao.findByBic(bicCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Counterparty not found: " + bicCode));

        existing.setParticipantName(request.getParticipantName());
        existing.setSupportedStandard(SupportedStandard.valueOf(request.getSupportedStandard()));
        existing.setPreferredStandard(MessageStandard.valueOf(request.getPreferredStandard()));
        capabilityDao.save(existing);
        return ResponseEntity.ok(toResponse(existing));
    }

    @DeleteMapping("/{bicCode}")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable String bicCode) {
        CounterpartyCapability existing = capabilityDao.findByBic(bicCode)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Counterparty not found: " + bicCode));
        existing.setActive(false);
        capabilityDao.save(existing);
        return ResponseEntity.noContent().build();
    }

    private CounterpartyCapabilityResponse toResponse(CounterpartyCapability cap) {
        CounterpartyCapabilityResponse r = new CounterpartyCapabilityResponse();
        r.setBicCode(cap.getBicCode());
        r.setParticipantName(cap.getParticipantName());
        r.setSupportedStandard(cap.getSupportedStandard().name());
        r.setPreferredStandard(cap.getPreferredStandard().name());
        r.setResolvedOutbound(cap.resolveOutboundStandard().name());
        r.setEffectiveDate(cap.getEffectiveDate());
        r.setActive(cap.isActive());
        return r;
    }

    private CounterpartyCapability fromRequest(CounterpartyCapabilityRequest request) {
        return new CounterpartyCapability(
                request.getBicCode().toUpperCase(),
                request.getParticipantName(),
                SupportedStandard.valueOf(request.getSupportedStandard()),
                MessageStandard.valueOf(request.getPreferredStandard()));
    }
}
