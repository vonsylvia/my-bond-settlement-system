package com.settlement.service;

import com.settlement.dao.AuditLogDao;
import com.settlement.dao.BondHoldingDao;
import com.settlement.dao.SettlementInstructionDao;
import com.settlement.dao.SwiftMessageDao;
import com.settlement.dto.HoldingResponse;
import com.settlement.dto.SettlementRequest;
import com.settlement.canonical.CanonicalSettlement;
import com.settlement.canonical.PaymentType;
import com.settlement.entity.*;
import com.settlement.exception.BusinessException;
import com.settlement.exception.ResourceNotFoundException;
import com.settlement.strategy.CanonicalMapper;
import com.settlement.strategy.SwiftMessageStrategy;
import com.settlement.strategy.SwiftMessageStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final SettlementInstructionDao instructionDao;
    private final BondHoldingDao holdingDao;
    private final AuditLogDao auditLogDao;
    private final SwiftMessageDao swiftMessageDao;
    private final SwiftMessageStrategyFactory strategyFactory;
    private final CanonicalMapper canonicalMapper;
    private final AsyncSettlementProcessor asyncProcessor;

    public SettlementService(SettlementInstructionDao instructionDao,
                             BondHoldingDao holdingDao,
                             AuditLogDao auditLogDao,
                             SwiftMessageDao swiftMessageDao,
                             SwiftMessageStrategyFactory strategyFactory,
                             CanonicalMapper canonicalMapper,
                             AsyncSettlementProcessor asyncProcessor) {
        this.instructionDao = instructionDao;
        this.holdingDao = holdingDao;
        this.auditLogDao = auditLogDao;
        this.swiftMessageDao = swiftMessageDao;
        this.strategyFactory = strategyFactory;
        this.canonicalMapper = canonicalMapper;
        this.asyncProcessor = asyncProcessor;
    }

    /**
     * Phase 1 (synchronous): validate, persist instruction with PENDING status,
     * build outbound SWIFT message (MT or MX), store in SWIFT_MESSAGE table,
     * then trigger async XA processing.
     */
    @Transactional
    public SettlementInstruction submitInstruction(SettlementRequest request) {
        return createInstruction(request, null, null, null);
    }

    @Transactional
    public SettlementInstruction submitOpenApiInstruction(String participantId, String clientReference,
                                                          SettlementRequest request) {
        String normalizedParticipantId = participantId == null ? null : participantId.trim();
        String normalizedClientReference = clientReference == null ? null : clientReference.trim();
        String requestHash = hashOpenApiRequest(normalizedParticipantId, normalizedClientReference, request);

        return instructionDao.findByParticipantAndClientReference(
                        normalizedParticipantId, normalizedClientReference)
                .map(existing -> {
                    if (!Objects.equals(existing.getOpenApiRequestHash(), requestHash)) {
                        throw new BusinessException("Duplicate clientReference with different instruction payload: "
                                + normalizedClientReference);
                    }
                    log.info("Idempotent Open API replay: participantId={}, clientReference={}, tradeRef={}",
                            normalizedParticipantId, normalizedClientReference, existing.getTradeRef());
                    return existing;
                })
                .orElseGet(() -> createInstruction(
                        request, normalizedParticipantId, normalizedClientReference, requestHash));
    }

    private SettlementInstruction createInstruction(SettlementRequest request, String participantId,
                                                    String clientReference, String requestHash) {
        String tradeRef = generateTradeRef();

        MessageStandard requestedStandard = (request.getRequestedStandard() != null
                && !request.getRequestedStandard().isBlank())
                ? MessageStandard.valueOf(request.getRequestedStandard())
                : MessageStandard.MT;

        String currency = (request.getCurrency() != null && !request.getCurrency().isBlank())
                ? request.getCurrency() : "HKD";

        String paymentType = (request.getPaymentType() != null && !request.getPaymentType().isBlank())
                ? request.getPaymentType() : PaymentType.AGAINST_PAYMENT.name();

        SettlementInstruction instruction = new SettlementInstruction();
        instruction.setTradeRef(tradeRef);
        instruction.setParticipantId(participantId);
        instruction.setClientReference(clientReference);
        instruction.setOpenApiRequestHash(requestHash);
        instruction.setIsin(request.getIsin());
        instruction.setSettlementDate(request.getSettlementDate());
        instruction.setQuantity(request.getQuantity());
        instruction.setCounterparty(request.getCounterparty());
        instruction.setBicCode(request.getBicCode());
        instruction.setDirection(Direction.valueOf(request.getDirection()));
        instruction.setAccountId(request.getAccountId());
        instruction.setStatus(InstructionStatus.PENDING);
        instruction.setRequestedStandard(requestedStandard);
        instruction.setCurrency(currency);
        instruction.setPaymentType(paymentType);
        instruction.setSettlementAmount(request.getSettlementAmount());

        instructionDao.save(instruction);

        SwiftMessageStrategy strategy = strategyFactory.getStrategy(requestedStandard);
        CanonicalSettlement canonical = canonicalMapper.toCanonical(instruction);
        String rawMessage = strategy.buildSettlementInstruction(canonical);
        String messageType = strategy.getOutboundMessageType(canonical);

        SwiftMessage primaryMsg = new SwiftMessage(
                instruction.getId(), tradeRef, requestedStandard, messageType,
                MessageDirection.OUTBOUND, rawMessage);
        swiftMessageDao.save(primaryMsg);

        auditLogDao.save(new AuditLog(tradeRef, AuditEventType.INSTRUCTION_CREATED,
                auditDetailPrefix(participantId, clientReference)
                        + "Instruction saved, async XA processing queued. ISIN="
                        + request.getIsin() + " QTY=" + request.getQuantity()));

        log.info("Settlement instruction created: tradeRef={}, participantId={}, clientReference={}, ISIN={}, direction={}, standard={} — async send queued",
                tradeRef, participantId, clientReference, request.getIsin(), request.getDirection(), requestedStandard);

        scheduleAfterCommit(tradeRef);

        return instruction;
    }

    @Transactional
    public SettlementInstruction findByTradeRef(String tradeRef) {
        return instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement instruction not found: " + tradeRef));
    }

    @Transactional
    public SettlementInstruction findOpenApiInstruction(String participantId, String clientReference) {
        return instructionDao.findByParticipantAndClientReference(participantId.trim(), clientReference.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Open API instruction not found: " + clientReference));
    }

    @Transactional
    public SettlementInstruction manualRetry(String tradeRef) {
        SettlementInstruction instruction = instructionDao.findByTradeRef(tradeRef)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Settlement instruction not found: " + tradeRef));

        if (instruction.isFinal()) {
            throw new BusinessException(
                    "Cannot modify finalized instruction: " + tradeRef);
        }

        if (instruction.getStatus() != InstructionStatus.FAILED) {
            throw new BusinessException(
                    "Cannot retry instruction in status " + instruction.getStatus()
                            + ". Only FAILED instructions can be retried.");
        }

        instruction.setStatus(InstructionStatus.PENDING);
        instruction.setRetryCount(0);
        instruction.setFailureReason(null);
        instructionDao.save(instruction);

        auditLogDao.save(new AuditLog(tradeRef, AuditEventType.MANUAL_RETRY,
                "Manual retry triggered, status reset to PENDING"));

        log.info("Manual retry triggered: tradeRef={}", tradeRef);

        scheduleAfterCommit(tradeRef);

        return instruction;
    }

    @Transactional
    public List<SettlementInstruction> findAll(int page, int size) {
        return instructionDao.findAll(page, size);
    }

    @Transactional
    public long count() {
        return instructionDao.count();
    }

    @Transactional
    public List<HoldingResponse> getHoldings(String accountId) {
        List<BondHolding> holdings;
        if (accountId != null && !accountId.isBlank()) {
            holdings = holdingDao.findByAccount(accountId);
        } else {
            holdings = holdingDao.findAll();
        }
        return holdings.stream().map(this::toHoldingResponse).toList();
    }

    private HoldingResponse toHoldingResponse(BondHolding holding) {
        HoldingResponse resp = new HoldingResponse();
        resp.setAccountId(holding.getAccountId());
        resp.setIsin(holding.getIsin());
        resp.setQuantity(holding.getQuantity());
        resp.setUpdatedAt(holding.getUpdatedAt());
        return resp;
    }

    private void scheduleAfterCommit(String tradeRef) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncProcessor.processSettlementAsync(tradeRef);
                }
            });
        } else {
            asyncProcessor.processSettlementAsync(tradeRef);
        }
    }

    private String generateTradeRef() {
        return "TR-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    private String auditDetailPrefix(String participantId, String clientReference) {
        if (participantId == null || clientReference == null) {
            return "";
        }
        return "Open API participantId=" + participantId
                + " clientReference=" + clientReference + ". ";
    }

    private String hashOpenApiRequest(String participantId, String clientReference,
                                      SettlementRequest request) {
        String material = String.join("|",
                nullToEmpty(participantId),
                nullToEmpty(clientReference),
                nullToEmpty(request.getIsin()),
                Objects.toString(request.getSettlementDate(), ""),
                Objects.toString(request.getQuantity(), ""),
                nullToEmpty(request.getCounterparty()),
                nullToEmpty(request.getBicCode()),
                nullToEmpty(request.getDirection()),
                nullToEmpty(request.getAccountId()),
                nullToEmpty(request.getRequestedStandard()),
                nullToEmpty(request.getCurrency()),
                Objects.toString(request.getSettlementAmount(), ""),
                nullToEmpty(request.getPaymentType()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(material.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is not available", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
