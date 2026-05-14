package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.MatchingInstructionDao;
import com.settlement.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Bilateral instruction matching engine for CSD operations.
 * Buy and sell sides each submit instructions; the engine automatically
 * matches them when key fields align within configurable tolerances.
 *
 * <p>Matching criteria:
 * <ul>
 *   <li>ISIN must be identical</li>
 *   <li>Settlement date must be identical</li>
 *   <li>Directions must be opposite (BUY↔SELL)</li>
 *   <li>Counterparty BIC of each side must match the submitter of the other</li>
 *   <li>Quantity within tolerance (default: exact match for securities)</li>
 *   <li>Amount within tolerance (configurable, e.g. €2 for amounts under €100k)</li>
 * </ul>
 */
@Service
public class MatchingEngineService {

    private static final Logger log = LoggerFactory.getLogger(MatchingEngineService.class);

    private static final BigDecimal AMOUNT_TOLERANCE_THRESHOLD = new BigDecimal("100000");
    private static final BigDecimal AMOUNT_TOLERANCE_SMALL = new BigDecimal("2.00");
    private static final BigDecimal QUANTITY_TOLERANCE = BigDecimal.ZERO;

    private final MatchingInstructionDao matchingDao;
    private final AuditLogDao auditLogDao;

    public MatchingEngineService(MatchingInstructionDao matchingDao, AuditLogDao auditLogDao) {
        this.matchingDao = matchingDao;
        this.auditLogDao = auditLogDao;
    }

    @Transactional
    public MatchingInstruction submitForMatching(MatchingInstruction instruction) {
        matchingDao.save(instruction);
        log.info("Matching instruction submitted: tradeRef={}, isin={}, direction={}, submitter={}",
                instruction.getTradeRef(), instruction.getIsin(),
                instruction.getDirection(), instruction.getSubmitterBic());

        Optional<MatchingInstruction> match = findMatch(instruction);
        if (match.isPresent()) {
            performMatch(instruction, match.get());
        } else {
            instruction.setMatchingStatus(MatchingStatus.ALLEGED);
            matchingDao.save(instruction);
            auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.MATCHING_ALLEGED,
                    "No counterparty instruction found yet — status set to ALLEGED"));
            log.info("No match found, set to ALLEGED: tradeRef={}", instruction.getTradeRef());
        }

        return instruction;
    }

    @Transactional
    public Optional<MatchingInstruction> retryMatching(Long instructionId) {
        return matchingDao.findById(instructionId)
                .filter(mi -> mi.getMatchingStatus() == MatchingStatus.ALLEGED
                        || mi.getMatchingStatus() == MatchingStatus.UNMATCHED)
                .flatMap(mi -> {
                    Optional<MatchingInstruction> match = findMatch(mi);
                    match.ifPresent(m -> performMatch(mi, m));
                    return match;
                });
    }

    private Optional<MatchingInstruction> findMatch(MatchingInstruction instruction) {
        Direction oppositeDirection = (instruction.getDirection() == Direction.BUY)
                ? Direction.SELL : Direction.BUY;

        List<MatchingInstruction> candidates = matchingDao.findUnmatchedCandidates(
                instruction.getIsin(),
                instruction.getSettlementDate(),
                oppositeDirection,
                instruction.getCounterpartyBic()
        );

        for (MatchingInstruction candidate : candidates) {
            if (candidate.getId().equals(instruction.getId())) {
                continue;
            }
            if (isWithinTolerance(instruction, candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private boolean isWithinTolerance(MatchingInstruction a, MatchingInstruction b) {
        BigDecimal qtyDiff = a.getQuantity().subtract(b.getQuantity()).abs();
        if (qtyDiff.compareTo(QUANTITY_TOLERANCE) > 0) {
            return false;
        }

        if (a.getAmount() != null && b.getAmount() != null) {
            BigDecimal amtDiff = a.getAmount().subtract(b.getAmount()).abs();
            BigDecimal tolerance = a.getAmount().compareTo(AMOUNT_TOLERANCE_THRESHOLD) < 0
                    ? AMOUNT_TOLERANCE_SMALL
                    : BigDecimal.ZERO;
            if (amtDiff.compareTo(tolerance) > 0) {
                return false;
            }
        }

        return true;
    }

    private void performMatch(MatchingInstruction a, MatchingInstruction b) {
        a.setMatchingStatus(MatchingStatus.MATCHED);
        a.setMatchedWithId(b.getId());
        matchingDao.save(a);

        b.setMatchingStatus(MatchingStatus.MATCHED);
        b.setMatchedWithId(a.getId());
        matchingDao.save(b);

        auditLogDao.save(new AuditLog(a.getTradeRef(), AuditEventType.MATCHING_MATCHED,
                "Matched with counterparty instruction: " + b.getTradeRef()));
        auditLogDao.save(new AuditLog(b.getTradeRef(), AuditEventType.MATCHING_MATCHED,
                "Matched with counterparty instruction: " + a.getTradeRef()));

        log.info("Instructions matched: {} <-> {}", a.getTradeRef(), b.getTradeRef());
    }

    @Transactional
    public void cancelMatchingInstruction(Long instructionId) {
        matchingDao.findById(instructionId).ifPresent(mi -> {
            mi.setMatchingStatus(MatchingStatus.CANCELLED);
            matchingDao.save(mi);

            if (mi.getMatchedWithId() != null) {
                matchingDao.findById(mi.getMatchedWithId()).ifPresent(other -> {
                    other.setMatchingStatus(MatchingStatus.UNMATCHED);
                    other.setMatchedWithId(null);
                    matchingDao.save(other);
                });
            }
        });
    }

    @Transactional(readOnly = true)
    public List<MatchingInstruction> findUnmatched() {
        return matchingDao.findByStatus(MatchingStatus.UNMATCHED);
    }

    @Transactional(readOnly = true)
    public List<MatchingInstruction> findAlleged() {
        return matchingDao.findByStatus(MatchingStatus.ALLEGED);
    }
}
