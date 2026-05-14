package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.PartialSettlementDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.entity.AuditEventType;
import com.settlement.entity.AuditLog;
import com.settlement.entity.InstructionStatus;
import com.settlement.entity.PartialSettlement;
import com.settlement.entity.PartialSettlementStatus;
import com.settlement.entity.SettlementInstruction;
import com.settlement.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Partial settlement support with ICMA-recommended shaping.
 *
 * <p>When a full settlement cannot be completed (e.g. insufficient securities
 * on the delivering side), the instruction can be shaped into smaller batches
 * to maximize settlement rates and reduce fails.
 *
 * <p>ICMA best practice: shape into batches of 50,000,000 nominal value
 * for major currencies. This is configurable per currency.
 */
@Service
public class PartialSettlementService {

    private static final Logger log = LoggerFactory.getLogger(PartialSettlementService.class);

    private static final BigDecimal DEFAULT_SHAPING_SIZE = new BigDecimal("50000000");

    private final PartialSettlementDao partialDao;
    private final SettlementInstructionDao instructionDao;
    private final AuditLogDao auditLogDao;

    public PartialSettlementService(PartialSettlementDao partialDao,
                                    SettlementInstructionDao instructionDao,
                                    AuditLogDao auditLogDao) {
        this.partialDao = partialDao;
        this.instructionDao = instructionDao;
        this.auditLogDao = auditLogDao;
    }

    /**
     * Shapes a large instruction into smaller batches per ICMA guidelines.
     * Returns the list of partial settlements created.
     */
    @Transactional
    public List<PartialSettlement> shapeInstruction(Long instructionId) {
        return shapeInstruction(instructionId, DEFAULT_SHAPING_SIZE);
    }

    @Transactional
    public List<PartialSettlement> shapeInstruction(Long instructionId, BigDecimal batchSize) {
        SettlementInstruction instruction;

        List<PartialSettlement> existing = partialDao.findByParentInstructionId(instructionId);
        if (!existing.isEmpty()) {
            throw new BusinessException("Instruction already shaped into " + existing.size() + " batches");
        }

        instruction = findInstructionById(instructionId);
        if (instruction == null) {
            throw new BusinessException("Instruction not found: " + instructionId);
        }

        if (instruction.isFinal()) {
            throw new BusinessException("Cannot shape finalized instruction");
        }

        BigDecimal totalQuantity = instruction.getQuantity();
        if (totalQuantity.compareTo(batchSize) <= 0) {
            log.info("Instruction quantity {} is within batch size {}, no shaping needed",
                    totalQuantity, batchSize);
            return List.of();
        }

        List<PartialSettlement> splits = new ArrayList<>();
        BigDecimal remaining = totalQuantity;
        int sequence = 1;

        while (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal splitQty = remaining.min(batchSize);
            String splitRef = instruction.getTradeRef() + "-P" + sequence;

            PartialSettlement ps = new PartialSettlement(
                    instructionId, sequence, splitQty, splitRef);
            partialDao.save(ps);
            splits.add(ps);

            remaining = remaining.subtract(splitQty);
            sequence++;
        }

        instruction.setStatus(InstructionStatus.PARTIALLY_SETTLED);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.PARTIAL_SETTLEMENT_CREATED,
                "Shaped into " + splits.size() + " batches of max " + batchSize));

        log.info("Instruction {} shaped into {} partial settlements", instruction.getTradeRef(), splits.size());
        return splits;
    }

    /**
     * Marks a partial settlement split as settled and checks if all splits
     * are complete, in which case the parent instruction is finalized.
     */
    @Transactional
    public void completeSplit(Long partialId) {
        PartialSettlement ps = partialDao.findById(partialId)
                .orElseThrow(() -> new BusinessException("Partial settlement not found: " + partialId));

        ps.setStatus(PartialSettlementStatus.SETTLED);
        partialDao.save(ps);

        long pendingCount = partialDao.countByParentAndStatus(
                ps.getParentInstructionId(), PartialSettlementStatus.PENDING);
        long sentCount = partialDao.countByParentAndStatus(
                ps.getParentInstructionId(), PartialSettlementStatus.SENT);

        if (pendingCount == 0 && sentCount == 0) {
            SettlementInstruction parent = findInstructionById(ps.getParentInstructionId());
            if (parent != null) {
                parent.setStatus(InstructionStatus.MATCHED);
                parent.setFinalityTimestamp(java.time.LocalDateTime.now());
                parent.setFinal(true);
                instructionDao.save(parent);

                auditLogDao.save(new AuditLog(parent.getTradeRef(),
                        AuditEventType.PARTIAL_SETTLEMENT_COMPLETED,
                        "All partial settlements completed — instruction finalized"));

                log.info("All splits settled, parent finalized: {}", parent.getTradeRef());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PartialSettlement> getSplits(Long parentInstructionId) {
        return partialDao.findByParentInstructionId(parentInstructionId);
    }

    private SettlementInstruction findInstructionById(Long id) {
        return instructionDao.findAll(0, Integer.MAX_VALUE).stream()
                .filter(i -> i.getId() != null && i.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
}
