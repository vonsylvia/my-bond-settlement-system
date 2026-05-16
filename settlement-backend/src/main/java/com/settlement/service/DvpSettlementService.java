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

        String currency = instruction.getCurrency();
        String tradeRef = instruction.getTradeRef();

        String payerAccount = cashPayerAccount(instruction);
        boolean reserved = chatsGateway.reserveFunds(payerAccount, currency, amount, tradeRef);
        if (!reserved) {
            instruction.setFailureReason("DVP failed: insufficient funds for cash leg");
            instruction.setStatus(InstructionStatus.FAILED);
            instructionDao.save(instruction);
            auditLogDao.save(new AuditLog(tradeRef, AuditEventType.DVP_FAILED,
                    "Cash reservation failed: insufficient " + currency + " balance in " + payerAccount));
            throw new BusinessException("DVP lock failed: insufficient funds");
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

        if (instruction.getStatus() != InstructionStatus.DVP_LOCKED) {
            throw new BusinessException("DVP completion requires DVP_LOCKED status: " + tradeRef);
        }

        updateHoldings(instruction);
        creditCashReceiver(instruction);

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
        String isin = instruction.getIsin();
        BigDecimal quantity = instruction.getQuantity();

        if (instruction.getDirection() == Direction.BUY) {
            applySecurityMovement(instruction.getBicCode(), isin, MovementType.DEBIT, quantity, instruction.getTradeRef());
            applySecurityMovement(instruction.getAccountId(), isin, MovementType.CREDIT, quantity, instruction.getTradeRef());
        } else {
            applySecurityMovement(instruction.getAccountId(), isin, MovementType.DEBIT, quantity, instruction.getTradeRef());
            applySecurityMovement(instruction.getBicCode(), isin, MovementType.CREDIT, quantity, instruction.getTradeRef());
        }
    }

    private void applySecurityMovement(String accountId, String isin, MovementType movementType,
                                       BigDecimal quantity, String tradeRef) {
        Optional<BondHolding> optHolding = holdingDao.findByAccountAndIsinForUpdate(accountId, isin);

        BondHolding holding;
        BigDecimal newBalance;

        if (movementType == MovementType.CREDIT) {
            holding = optHolding.orElseGet(() -> newHolding(accountId, isin));
            newBalance = holding.getQuantity().add(quantity);
        } else {
            holding = optHolding.orElseThrow(() ->
                    new BusinessException("Cannot deliver: no existing holding for account=" + accountId));
            newBalance = holding.getQuantity().subtract(quantity);
            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new BusinessException("Insufficient bond holdings for DVP delivery");
            }
        }

        holding.setQuantity(newBalance);
        holdingDao.save(holding);

        movementDao.save(new SecurityMovement(accountId, isin, movementType, quantity, newBalance, tradeRef));

        log.info("Holdings updated via DVP: account={}, isin={}, type={}, qty={}, newBalance={}",
                accountId, isin, movementType, quantity, newBalance);
    }

    private static BondHolding newHolding(String accountId, String isin) {
        BondHolding h = new BondHolding();
        h.setAccountId(accountId);
        h.setIsin(isin);
        h.setQuantity(BigDecimal.ZERO);
        return h;
    }

    private void creditCashReceiver(SettlementInstruction instruction) {
        BigDecimal amount = instruction.getSettlementAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || !PaymentType.AGAINST_PAYMENT.name().equals(instruction.getPaymentType())) {
            return;
        }
        chatsGateway.releaseFunds(cashReceiverAccount(instruction), instruction.getCurrency(),
                amount, instruction.getTradeRef());
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
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            chatsGateway.releaseFunds(
                    cashPayerAccount(instruction), instruction.getCurrency(),
                    amount, instruction.getTradeRef());
        }

        instruction.setStatus(InstructionStatus.FAILED);
        instruction.setFailureReason("DVP rolled back");
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(instruction.getTradeRef(), AuditEventType.DVP_FAILED,
                "DVP rolled back — funds released"));
        log.info("DVP rolled back: tradeRef={}", instruction.getTradeRef());
    }

    private String cashPayerAccount(SettlementInstruction instruction) {
        return instruction.getDirection() == Direction.BUY
                ? instruction.getAccountId()
                : instruction.getBicCode();
    }

    private String cashReceiverAccount(SettlementInstruction instruction) {
        return instruction.getDirection() == Direction.BUY
                ? instruction.getBicCode()
                : instruction.getAccountId();
    }
}
