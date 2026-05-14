package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SecurityMovementDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.canonical.PaymentType;
import com.settlement.entity.*;
import com.settlement.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Implements BIS Model 1 real-time DVP (Delivery vs Payment).
 * Securities and cash legs are settled simultaneously — the cash is
 * reserved via CHATS before the securities transfer is committed,
 * ensuring no principal risk (PFMI Principle 12).
 *
 * <p>DVP flow:
 * <ol>
 *   <li>Lock instruction (DVP_LOCKED)</li>
 *   <li>Reserve cash via CHATS gateway</li>
 *   <li>Confirm securities transfer</li>
 *   <li>Complete DVP — mark as MATCHED with finality</li>
 * </ol>
 */
@Service
public class DvpSettlementService {

    private static final Logger log = LoggerFactory.getLogger(DvpSettlementService.class);

    private final SettlementInstructionDao instructionDao;
    private final ChatsGateway chatsGateway;
    private final AuditLogDao auditLogDao;
    private final BondHoldingDao holdingDao;
    private final SecurityMovementDao movementDao;

    public DvpSettlementService(SettlementInstructionDao instructionDao,
                                ChatsGateway chatsGateway,
                                AuditLogDao auditLogDao,
                                BondHoldingDao holdingDao,
                                SecurityMovementDao movementDao) {
        this.instructionDao = instructionDao;
        this.chatsGateway = chatsGateway;
        this.auditLogDao = auditLogDao;
        this.holdingDao = holdingDao;
        this.movementDao = movementDao;
    }

    /**
     * Initiates the DVP locking phase: validates funds availability
     * and reserves cash before the securities leg can proceed.
     */
    @Transactional
    public void lockForDvp(SettlementInstruction instruction) {
        if (instruction.isFinal()) {
            throw new BusinessException("Cannot modify finalized instruction: " + instruction.getTradeRef());
        }

        String paymentType = instruction.getPaymentType();
        if (!PaymentType.AGAINST_PAYMENT.name().equals(paymentType)) {
            log.debug("Skipping DVP lock for FOP instruction: tradeRef={}", instruction.getTradeRef());
            return;
        }

        BigDecimal amount = instruction.getSettlementAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("No settlement amount for DVP: tradeRef={}", instruction.getTradeRef());
            return;
        }

        String accountId = instruction.getAccountId();
        String currency = instruction.getCurrency();
        String tradeRef = instruction.getTradeRef();

        if (instruction.getDirection() == Direction.BUY) {
            boolean reserved = chatsGateway.reserveFunds(accountId, currency, amount, tradeRef);
            if (!reserved) {
                instruction.setFailureReason("DVP failed: insufficient funds for cash leg");
                instruction.setStatus(InstructionStatus.FAILED);
                instructionDao.save(instruction);
                auditLogDao.save(new AuditLog(tradeRef, AuditEventType.DVP_FAILED,
                        "Cash reservation failed: insufficient " + currency + " balance"));
                throw new BusinessException("DVP lock failed: insufficient funds");
            }
        }

        instruction.setStatus(InstructionStatus.DVP_LOCKED);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, AuditEventType.DVP_LOCKED,
                "DVP locked: " + currency + " " + amount + " reserved for " + instruction.getDirection()));
        log.info("DVP locked: tradeRef={}, currency={}, amount={}", tradeRef, currency, amount);
    }

    /**
     * Completes the DVP cycle after both legs are confirmed.
     * Marks the instruction as MATCHED with settlement finality.
     */
    @Transactional
    public void completeDvp(SettlementInstruction instruction) {
        String tradeRef = instruction.getTradeRef();

        updateHoldings(instruction);

        instruction.setStatus(InstructionStatus.MATCHED);
        instruction.setFinalityTimestamp(LocalDateTime.now());
        instruction.setFinal(true);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, AuditEventType.DVP_COMPLETED,
                "DVP settlement completed with finality. Currency=" + instruction.getCurrency()
                + " Amount=" + instruction.getSettlementAmount()));

        log.info("DVP completed with finality: tradeRef={}", tradeRef);
    }

    private void updateHoldings(SettlementInstruction instruction) {
        String accountId = instruction.getAccountId();
        String isin = instruction.getIsin();
        BigDecimal quantity = instruction.getQuantity();

        MovementType movementType = (instruction.getDirection() == Direction.BUY)
                ? MovementType.CREDIT
                : MovementType.DEBIT;

        Optional<BondHolding> optHolding = holdingDao.findByAccountAndIsinForUpdate(accountId, isin);

        BondHolding holding;
        BigDecimal newBalance;

        if (movementType == MovementType.CREDIT) {
            holding = optHolding.orElseGet(() -> {
                BondHolding h = new BondHolding();
                h.setAccountId(accountId);
                h.setIsin(isin);
                h.setQuantity(BigDecimal.ZERO);
                return h;
            });
            newBalance = holding.getQuantity().add(quantity);
        } else {
            holding = optHolding.orElseThrow(() ->
                    new BusinessException("Cannot sell: no existing holding for account=" + accountId));
            newBalance = holding.getQuantity().subtract(quantity);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("Insufficient bond holdings for settlement");
            }
        }

        holding.setQuantity(newBalance);
        holdingDao.save(holding);

        movementDao.save(new SecurityMovement(
                accountId, isin, movementType, quantity, newBalance, instruction.getTradeRef()));

        log.info("Holdings updated via DVP: account={}, isin={}, type={}, qty={}, newBalance={}",
                accountId, isin, movementType, quantity, newBalance);
    }

    /**
     * Rolls back a failed DVP by releasing reserved funds.
     */
    @Transactional
    public void rollbackDvp(SettlementInstruction instruction) {
        if (instruction.getStatus() != InstructionStatus.DVP_LOCKED) {
            return;
        }

        BigDecimal amount = instruction.getSettlementAmount();
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0
                && instruction.getDirection() == Direction.BUY) {
            chatsGateway.releaseFunds(
                    instruction.getAccountId(), instruction.getCurrency(),
                    amount, instruction.getTradeRef());
        }

        instruction.setStatus(InstructionStatus.FAILED);
        instruction.setFailureReason("DVP rolled back");
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.DVP_FAILED,
                "DVP rolled back — funds released"));
        log.info("DVP rolled back: tradeRef={}", instruction.getTradeRef());
    }
}
